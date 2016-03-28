package com.galacticfog.gestalt.meta.dcos

import java.util.UUID


import akka.dispatch.sysmsg.Failed
import com.galacticfog.gestalt.cli.control._
import com.galacticfog.gestalt.meta.api.sdk._
import com.galacticfog.gestalt.cli.Console
import play.api.libs.json.Json
import scala.util.{Success, Failure, Try}
import joptsimple.OptionSet
import scala.collection.JavaConverters._
import com.galacticfog.gestalt.utils.json.JsonUtils._
import com.galacticfog.gestalt.cli.ShellUtils._
import sys.process._


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

case class ProviderAuth( scheme : String, username : String, password : String )
case class ProviderConfig( url : String, auth : ProviderAuth )

object JsonHelp {
  implicit val providerAuthFormats = Json.format[ProviderAuth]
  implicit val providerConfigFormats = Json.format[ProviderConfig]
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
'##::::'##'########'########:::'###:::::::::::::::'########::'######::'#######::'######::
| ###::'###:##.....:... ##..:::'## ##:::::::::::::::##.... ##'##... ##'##.... ##'##... ##:
| ####'####:##::::::::: ##::::'##:. ##::::::::::::::##:::: ##:##:::..::##:::: ##:##:::..::
| ## ### ##:######::::: ##:::'##:::. ##::'#######:::##:::: ##:##:::::::##:::: ##. ######::
| ##. #: ##:##...:::::: ##::::#########::........:::##:::: ##:##:::::::##:::: ##:..... ##:
| ##:.:: ##:##::::::::: ##::::##.... ##:::::::::::::##:::: ##:##::: ##:##:::: ##'##::: ##:
| ##:::: ##:########::: ##::::##:::: ##:::::::::::::########:. ######:. #######:. ######::
|..:::::..:........::::..::::..:::::..:::::::::::::........:::......:::.......:::......:::
""") toString


  var data = scala.collection.mutable.Map[String, String]()
  var functions = scala.collection.mutable.Map[Int, (Int) => Int]()

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
    if ( options.hasArgument( "meta-hostname" ) ) data.put( "metaHost", options.valuesOf( "meta-hostname" ).asScala.head.asInstanceOf[String] )
    if ( options.hasArgument( "meta-port" ) )     data.put( "metaPort", options.valuesOf( "meta-port" ).asScala.head.asInstanceOf[String] )
    if ( options.hasArgument( "user" ) )          data.put( "username", options.valuesOf( "user" ).asScala.head.asInstanceOf[String] )
    if ( options.hasArgument( "password" ) )      data.put( "password", options.valuesOf( "password" ).asScala.head.asInstanceOf[String] )
  }


  val MAX_RETRIES = 5
  def initMeta( numTries : Int ) : Meta = {

    if( numTries > MAX_RETRIES )
    {
      throw new Exception( "Failed to init meta after " + MAX_RETRIES + " tries...")
    }

    val metaHost = data.get( "metaHost" ) getOrElse console.readLine( q( "meta-hostname" ) )
    val metaPort = data.get( "metaPort" ) getOrElse console.readLine( q( "meta-port" ) )
    val username = data.get( "username" ) getOrElse console.readLine( q( "user" ) )
    val password = data.get( "password" ) getOrElse console.readLine( q( "password" ) )

    //we'll use these later
    data.put( "username", username )
    data.put( "metaHost", metaHost )

    val config = new HostConfig(
      protocol = "http",
      host = metaHost,
      port = Some( metaPort.toLong ),
      timeout = 40,
      creds = Option( new BasicCredential( username = username, password = password ) )
    )

    val meta = new Meta(config)

    //test the connection here, if it fails we're going to allow the user to manually enter the connection info
    meta.topLevelOrgs match {
      case Success( s ) => meta
      case Failure( ex ) => {
        println( "FAILED to init meta with the following error : " + ex.getMessage )

        data.remove( "metaHost" )
        data.remove( "metaPort" )
        data.remove( "username" )
        data.remove( "password" )

        initMeta( numTries + 1 )
      }
    }
  }

  def q(s: String) = s"[${green("?")}] ${s}: "

  //allow a "one liner"
  if( options.hasArgument( "provider" ) ) data.put( "provider", options.valuesOf( "provider" ).asScala.head.asInstanceOf[String] )
  if( options.hasArgument( "environment" ) ) data.put( "environment", options.valuesOf( "environment" ).asScala.head.asInstanceOf[String] )

  getMetaOptions

  val meta = initMeta( 0 )

  println(dcosBanner)
  println("> Welcome, " + data.get( "username" ).get )
  println("> Bound to: https://" + data.get("metaHost").get )


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
  }

  def start(input: List[String]): Unit = {

    //TODO : need to fix this
    /*
    val shutdown = new ShutdownHook
    shutdown.attach()
    */


    setPs1(ps1)
    console.setPrompt(getPrompt)

    //if they passed us a provider, then skip all of this
    if( !( data.get( "provider" ).isDefined && data.get( "environment").isDefined) )
    {
      wizardSelect
    }
    else
    {
      fqonSelect
    }

    val oid = UUID.fromString( data.get( "org" ).get )
    val eid = UUID.fromString( data.get( "environment" ).get )
    val pid = UUID.fromString( data.get( "provider" ).get )

    val providers = meta.providers(oid, "environment", eid, Some("Marathon"))
    val providerResource = providers.get.filter(_.id == pid).head

    val providerPropertiesJson = providerResource.properties.get.get( "config" ) getOrElse {
      throw new Exception( "Provider instance not well formed" )
    }

    import JsonHelp._
    val providerConfig = parseAs[ProviderConfig]( providerPropertiesJson,  "Provider config not well formed" )

    println( "SETTING PROVIDER URL : " + providerConfig.url )

    val cmd = "dcos config set marathon.url " + providerConfig.url
    val output = cmd.!!

    println( "RESULTS : " + output )

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

    if( data.get( "provider" ).isDefined )
    {
      //break out of the loop and don the next thing
      println( "BREAKING OUT OF THE LOOP!!!" )
    }
    else
    {
      process( index )
    }
  }

  /*
  def process(input: Option[String]): Unit = {
    input match {
      case None => ;
      case Some("") => ;
      case Some("exit") => System.exit(0)
      case Some(in) => {
        

        val args = parse(in)

        if (controller.hasHandler(args(0))) {    
          val out = controller.process(CliRequest(args))
          console.println(out.payload)
        } else {
          console.println(s"-gf: ${args(0)}: command not found.")
        }
      }
    }
  }
  */

  def parse(input: String) = input.split("\\s+")
  
}
