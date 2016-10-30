name := """meta-dcos"""

organization := "com.galacticfog"

version := "0.0.1-SNAPSHOT"

scalaVersion := "2.11.5"

credentials += Credentials(Path.userHome / ".ivy2" / ".credentials")

resolvers ++= Seq(
  "gestalt" at "http://galacticfog.artifactoryonline.com/galacticfog/libs-snapshots-local",
  "gestalt-releases" at "http://galacticfog.artifactoryonline.com/galacticfog/libs-releases-local",
  "snapshots" at "http://scala-tools.org/repo-snapshots",
  "releases"  at "http://scala-tools.org/repo-releases")

credentials ++= {
  (for {
    realm <- sys.env.get("GESTALT_RESOLVER_REALM")
    username <- sys.env.get("GESTALT_RESOLVER_USERNAME")
    resolverUrlStr <- sys.env.get("GESTALT_RESOLVER_URL")
    resolverUrl <- scala.util.Try{url(resolverUrlStr)}.toOption
    password <- sys.env.get("GESTALT_RESOLVER_PASSWORD")
  } yield {
    Seq(Credentials(realm, resolverUrl.getHost, username, password))
  }) getOrElse(Seq())
}

resolvers ++= {
  sys.env.get("GESTALT_RESOLVER_URL") map {
    url => Seq("gestalt-resolver" at url)
  } getOrElse(Seq())
}

//
// Adds project name to prompt like in a Play project
//
shellPrompt in ThisBuild := { state => "\033[0;36m" + Project.extract(state).currentRef.project + "\033[0m] " }

// ----------------------------------------------------------------------------
// Play JSON
// ----------------------------------------------------------------------------

libraryDependencies += "com.typesafe.play" %% "play-json" % "2.4.0-M2"


// ----------------------------------------------------------------------------
// Specs 2
// ----------------------------------------------------------------------------

libraryDependencies += "junit" % "junit" % "4.12" % "test"

libraryDependencies += "org.specs2" %% "specs2-junit" % "2.4.15" % "test"

libraryDependencies += "org.specs2" %% "specs2-core" % "2.4.15" % "test"

// ----------------------------------------------------------------------------
// Logging
// ----------------------------------------------------------------------------

libraryDependencies += "org.slf4j" % "slf4j-api" % "1.7.10"

libraryDependencies += "ch.qos.logback" % "logback-classic" % "1.1.2"

libraryDependencies += "com.typesafe.scala-logging" %% "scala-logging" % "3.1.0"

// ----------------------------------------------------------------------------
// Apps Deps
// ----------------------------------------------------------------------------

libraryDependencies += "net.sf.jopt-simple" % "jopt-simple" % "4.3"
libraryDependencies += "com.galacticfog" %% "gestalt-meta-sdk-scala" % "0.3.6-SNAPSHOT" withSources()
libraryDependencies += "com.galacticfog" %% "gestalt-cli" % "1.0-SNAPSHOT" withSources()
libraryDependencies += "com.galacticfog" %% "gestalt-utils" % "0.0.1-SNAPSHOT" withSources()
libraryDependencies += "com.galacticfog" %% "gestalt-security-sdk-scala" % "2.2.6-SNAPSHOT" withSources()

assemblyMergeStrategy in assembly := {
	case "META-INF/MANIFEST.MF"         => MergeStrategy.discard
		case _                              => MergeStrategy.first
}
