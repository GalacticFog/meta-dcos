package com.galacticfog.gestalt.meta.dcos

import java.net.{MalformedURLException, URL}
import java.util.UUID


import akka.dispatch.sysmsg.Failed
import com.galacticfog.gestalt.cli.control._
import com.galacticfog.gestalt.meta.api.sdk._
import com.galacticfog.gestalt.cli.Console
import com.galacticfog.gestalt.cli.ShutdownHook
import com.galacticfog.gestalt.security.api.{HTTP => sHTTP, HTTPS => sHTTPS, AccessTokenResponse, GestaltToken, GestaltBasicCredentials, GestaltSecurityClient}
import com.ning.http.client.AsyncHttpClientConfig
import play.api.http.HeaderNames
import play.api.libs.json.Json
import play.api.libs.ws.WSClient
import play.api.libs.ws.ning.NingWSClient
import scala.concurrent.Future
import scala.util.{Success, Failure, Try}
import joptsimple.OptionSet
import scala.collection.JavaConverters._
import com.galacticfog.gestalt.utils.json.JsonUtils._
import com.galacticfog.gestalt.cli.ShellUtils._
import sys.process._
import scala.concurrent._
import scala.concurrent.duration._
import HeaderNames.WWW_AUTHENTICATE

object AnsiColor {
  val Black = "black"
  val Blue = "blue"
  val Green = "green"
  val Cyan = "cyan"
  val Red = "red"
  val Purple = "purple"
  val Orange = "orange"
  val LightGray = "lightgray"
  val DarkGray = "darkgray"
  val LightBlue = "lightblue"
  val LightGreen = "lightgreen"
  val LightCyan = "lightcyan"
  val LightRed = "lightred"
  val LightPurple = "lightpurple"
  val Yellow = "yellow"
  val White = "white"
  val None = "none"
}

case class MetaURL(protocol: String, hostname: String, port: Int)
case object MetaURL {
  def fromString(urlString: String): Try[MetaURL] = {
    for {
      u <- Try{new java.net.URL(urlString)}
      p = if (u.getPort != -1) u.getPort else u.getDefaultPort
    } yield MetaURL(u.getProtocol, u.getHost, p)
  }
}

abstract class Shell(console: Console) {
  var ps1: String = ">"
  var ps2: String = ">>"

  def start(input: List[String]): Unit
  def setPs1(p1: String) { ps1 = p1 }

  def promptColor = AnsiColor.Yellow
  
  def getPrompt = {
    val fmt = "\u001B[%sm%s\u001B[0m "
    val code = promptColor.toLowerCase match {
      case "black"       => "0;30"
      case "blue"        => "0;34"
      case "green"       => "0;32"
      case "cyan"        => "0;36"
      case "red"         => "0;31"
      case "purple"      => "0;35"
      case "orange"      => "0;33"
      case "lightgray"   => "0;37"
      case "darkgray"    => "1;30"
      case "lightblue"   => "1;34"
      case "lightgreen"  => "1;32"
      case "lightcyan"   => "1;36"
      case "lightred"    => "1;31"
      case "lightpurple" => "1;35"
      case "yellow"      => "1;33"
      case "white"       => "1;37"
    }
    fmt.format(code, ps1)
  }
}

class DCOSShell(console: Console, ps1: String = ">", ps2: String = ">>", options : OptionSet) extends Shell(console) {

  val dcosBanner = darkgray("""
|'##::::'##'########'########:::'###:::::::::::::::'########::'######::'#######::'######::
| ###::'###:##.....:... ##..:::'## ##:::::::::::::::##.... ##'##... ##'##.... ##'##... ##:
| ####'####:##::::::::: ##::::'##:. ##::::::::::::::##:::: ##:##:::..::##:::: ##:##:::..::
| ## ### ##:######::::: ##:::'##:::. ##::'#######:::##:::: ##:##:::::::##:::: ##. ######::
| ##. #: ##:##...:::::: ##::::#########::........:::##:::: ##:##:::::::##:::: ##:..... ##:
| ##:.:: ##:##::::::::: ##::::##.... ##:::::::::::::##:::: ##:##::: ##:##:::: ##'##::: ##:
| ##:::: ##:########::: ##::::##:::: ##:::::::::::::########:. ######:. #######:. ######::
|..:::::..:........::::..::::..:::::..:::::::::::::........:::......:::.......:::......:::
""") toString


  val data = scala.collection.mutable.Map[String, String]()
  val functions = scala.collection.mutable.Map[Int, (Int) => Int]()
  val MAX_RETRIES = 5

  //allow a "one liner"
  if( options.hasArgument( "provider" ) ) data.put( "provider", options.valuesOf( "provider" ).asScala.head.asInstanceOf[String] )
  if( options.hasArgument( "environment" ) ) data.put( "environment", options.valuesOf( "environment" ).asScala.head.asInstanceOf[String] )

  getMetaOptions

  val (meta,self) = initMeta( 0 )

  println(dcosBanner)
  println("> Welcome, " + self.name )
  println("> Bound to: " + data.get("metaURL").get )

  /**
   * Create a Map[T,String] from a Seq of resource instances.
   * Key is the instance ID
   * Value is determined by the mapping function you pass in (fm) - usually name.
   */
  def mkmap[T](rss: => Try[Seq[ResourceInstance]])(fm: (ResourceInstance) => (T, String)) = {
    rss match {
      case Failure(error) => throw error;
      case Success(rss)  => rss.map { fm( _ ) }.toMap
    }
  }

  def getMetaOptions = {
    if ( options.hasArgument( "metaURL" ) )       data.put( "metaURL", options.valuesOf( "metaURL" ).asScala.head.asInstanceOf[String] )
    if ( options.hasArgument( "user" ) )          data.put( "username", options.valuesOf( "user" ).asScala.head.asInstanceOf[String] )
    if ( options.hasArgument( "password" ) )      data.put( "password", options.valuesOf( "password" ).asScala.head.asInstanceOf[String] )
  }

  def getSecurityURLFromMeta(client: WSClient, metaURL: String): Try[String] = {
    val realmRegex = "realm=\"(.*)\"".r
    for {
      response <- Try{ Await.result( client.url(metaURL.stripSuffix("/") + "/users/self").get(), 15.seconds ) }
      if response.status == 401
      authResponse <- response.header(WWW_AUTHENTICATE).fold[Try[String]](
        Failure(new RuntimeException(s"did not receive ${WWW_AUTHENTICATE} response header"))
      )( Success.apply )
      realmPart <- authResponse.split(" ").find(_.matches("[Rr]ealm=.*")).fold[Try[String]](
        Failure(new RuntimeException(s"${WWW_AUTHENTICATE} response header did not indicate realm"))
      )( Success.apply )
      realm <- realmRegex.findFirstMatchIn(realmPart).map(_.group(1)).fold[Try[String]](
        Failure(new RuntimeException(s"${WWW_AUTHENTICATE} response header did not properly format realm"))
      )( Success.apply )
    } yield realm
  }

  def initMeta( numTries : Int ): (Meta, ResourceInstance) = {

    val builder = new AsyncHttpClientConfig.Builder()
    val wsclient  = new NingWSClient(builder.build())

    if( numTries > MAX_RETRIES )
    {
      throw new Exception( "Failed to init meta after " + MAX_RETRIES + " tries...")
    }

    val metaURL  = data.get( "metaURL" )  getOrElse console.readLine( q( "metaURL" ) )
    val username = data.get( "username" ) getOrElse console.readLine( q( "user" ) )
    val password = data.get( "password" ) getOrElse console.readPassword( q( "password" ) )

    //we'll use these later
    data.put( "metaURL", metaURL )

    val meta = for {
      s <- getSecurityURLFromMeta(wsclient, metaURL)
      sec <- MetaURL.fromString(s)
      securitySDK = {
        println(s"exchanging credentials for authentication token from ${sec.protocol}://${sec.hostname}:${sec.port}")
        new GestaltSecurityClient(wsclient, if (sec.protocol.equalsIgnoreCase("HTTPS")) sHTTPS else sHTTP, sec.hostname, sec.port, GestaltBasicCredentials("",""))
      }
      tokenAttempt <- Try{
        val maybeToken = Await.result(GestaltToken.grantPasswordToken("root", username, password)(securitySDK), 40.seconds)
        maybeToken.fold[Try[AccessTokenResponse]](Failure(new RuntimeException("invalid credentials")))(Success.apply)
      }
      accessTokenResponse <- tokenAttempt
      token = accessTokenResponse.accessToken.toString
      _ = data.put("metaToken", token)
      metaUrl <- MetaURL.fromString(metaURL)
      config <- Try{new HostConfig(
        protocol = metaUrl.protocol,
        host = metaUrl.hostname,
        port = Some(metaUrl.port),
        timeout = 40,
        creds = Option( new BearerCredential(token) )
      )}
    } yield new Meta(config)

    //test the connection here, if it fails we're going to allow the user to manually enter the connection info
    meta.flatMap(_.self) match {
      case Success( self ) => (meta.get,self)
      case Failure( ex ) => {
        println( "FAILED to init meta with the following error : " + ex.getClass + ": " + ex.getMessage )

        data.remove( "metaURL" )
        data.remove( "metaToken" )
        data.remove( "username" )
        data.remove( "password" )

        initMeta( numTries + 1 )
      }
    }
  }

  def q(s: String) = s"[${green("?")}] ${s}: "


  def wizardSelect = {
    val currentIndex = 0
    functions += ( 0 -> pickOrg )
    functions += ( 1 -> pickWorkspace )
    functions += ( 2 -> pickEnvironment )
    functions += ( 3 -> pickProvider )
    process( currentIndex )
  }

  def fqonSelect = {

    //do the meta stuff required to select the oid, pid
    val envPath = data.get( "environment" ).get
    val providerName = data.get( "provider" ).get

    val results = meta.mapPath( envPath ) match {
      case Success(s) => s
      case Failure(ex) => {
        ex.printStackTrace
        println( "Failed to decompose env path with error : " + ex.getMessage )

        data.remove( "environment" )
        data.remove( "provider" )

        throw new Exception( "Failed to decompose env path with error : " + ex.getMessage )
      }
    }

    val orgResourceInfo = results.get("org").get
    val workspaceResourceInfo = results.get("workspace").get
    val envResourceInfo = results.get("environment").get

    val orgId = orgResourceInfo.id.toString
    val workId = workspaceResourceInfo.id.toString
    val envId = envResourceInfo.id.toString

    data.put( "org", orgId )
    data.put( "workspace", workId )
    data.put( "environment", envId )

    val providers = meta.providers(UUID.fromString( orgId ), "environment", UUID.fromString( envId ), Some("Marathon"))
    val provider = providers.get.filter( _.name == providerName ).headOption getOrElse {
      throw new Exception( "No providers found for path : " + envPath + " with name : " + providerName )
    }

    data.put( "provider", provider.id.toString )
  }

  def start(input: List[String]): Unit = {

    //this clears any formatting that may have occured to the terminal
    val shutdown = new ShutdownHook
    shutdown.attach()

    setPs1(ps1)
    console.setPrompt(getPrompt)

    //if they passed us a provider, then skip all of this
    if( !(data.get( "provider" ).isDefined && data.get( "environment").isDefined) )
    {
      wizardSelect
    }
    else
    {
      try {
        fqonSelect
      }
      catch {
        case ex : Exception => {
          println( "Failed to use one-liner config : launching wizard select" )
          wizardSelect
        }
      }
    }

    val oid = UUID.fromString( data.get( "org" ).get )
    val wid = UUID.fromString( data.get( "workspace" ).get )
    val eid = UUID.fromString( data.get( "environment" ).get )
    val pid = UUID.fromString( data.get( "provider" ).get )

    //get the fqon from the org we selected
    val fqon = meta.topLevelOrgs.get.filter( _.id == oid ).head.properties.get( "fqon" ).as[String]

    /*
    val providers = meta.providers(oid, "environment", eid, Some("Marathon"))
    val providerResource = providers.get.filter(_.id == pid).head
    val providerPropertiesJson = providerResource.properties.get.get( "config" ) getOrElse {
      throw new Exception( "Provider instance not well formed" )
    }
    //println( "PROVIDER : " + provider)
    import JsonHelp._
    val providerConfig = parseAs[ProviderConfig]( providerPropertiesJson,  "Provider config not well formed" )
    println( "SETTING PROVIDER URL : " + providerConfig.url )
    */

    val url = MetaURL.fromString(data("metaURL")).get

    val providerUrl = s"${url.protocol}://" + url.hostname + ":" + url.port + "/" + fqon + "/environments/" + eid + "/providers/" + pid + "/"

    println( "Setting providerURL : " + providerUrl )

    val cmd1 = "dcos config set marathon.url " + providerUrl
    val cmd2 = "dcos config set core.dcos_acs_token " + data("metaToken")

    try {
      val o1 = cmd1.!!
      println( "RESULTS : " + o1 )
      val o2 = cmd2.!!
      println( "RESULTS : " + o2 )
    }
    catch {
      case ex : Exception => {
        println( "FAILED to run DCOS with the following error : " + ex.getMessage )
        println( "Did you forget to install the dcos app or set your path? - try 'which dcos' ")
        throw new Exception( "Failed to run DCOS" )
      }
    }
  }

  def pickOrg( index : Int ) : Int = {

    val orgdata = mkmap(meta.topLevelOrgs)(o => (o.id -> o.properties.get("fqon").as[String]))
    val oid = DCOSMenu(orgdata, title = Some("Select an Org"), console = console).render.choose()
    println("You chose: " + oid)

    data.put( "org", oid.toString )
    index + 1
  }

  def pickWorkspace( index : Int ) : Int = {

    //TODO : error handling?
    val oid = data.get( "org" ).get
    val workspacedata = mkmap(meta.workspaces(UUID.fromString(oid)))(w => (w.id -> w.name))

    if( workspacedata.size == 0 )
    {
      println( "Org contains no workspaces, please choose a different org..." )
      data.remove( "org" )
      index - 1
    }
    else
    {
      val wid = DCOSMenu(workspacedata, Some("Select a Workspace"), console = console).render.choose()
      println("You chose: " + wid)
      data.put( "workspace", wid.toString )
      index + 1
    }
  }

  def pickEnvironment( index : Int ) : Int = {

    //TODO : error handling?
    val oid = UUID.fromString( data.get( "org" ).get )
    val wid = UUID.fromString( data.get( "workspace" ).get )

    val envdata = mkmap(meta.environments(oid, wid)) { env =>
      (env.id -> "%s [%s]".format(env.name, env.properties.get("environment_type").as[String]))
    }

    if( envdata.size == 0 )
    {
      println( "Workspace contains no environments, please choose another workspace" )
      data.remove( "workspace" )
      index - 1
    }
    else
    {
      val eid = DCOSMenu(envdata, Some("Select an Environment"), console = console).render.choose()
      println("You chose : " + eid)
      data.put( "environment", eid.toString )
      index + 1
    }
  }

  def pickProvider( index : Int ) : Int = {

    //TODO : error handling?
    val oid = UUID.fromString( data.get( "org" ).get )
    val eid = UUID.fromString( data.get( "environment" ).get )

    val providers = meta.providers(oid, "environment", eid, Some("Marathon"))
    val providerdata = mkmap(providers) { p =>
      (p.id -> p.name)
    }

    if( providerdata.size == 0 )
    {
      println( "Environment contains no providers, please select a different provider" )
      data.remove( "environment" )
      index - 1
    }
    else
    {
      val pid = DCOSMenu(providerdata, Some("Select a Provider"), console = console).render.choose()
      println("You chose : " + pid)
      data.put( "provider", pid.toString )
      index + 1
    }
  }

  def process( currentIndex : Int ): Unit =
  {
    val func = functions.get(currentIndex).get
    val index = func( currentIndex )

    if( !data.get( "provider" ).isDefined )
    {
      process( index )
    }
  }

  def parse(input: String) = input.split("\\s+")

}
