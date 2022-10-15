val AkkaVersion     = "2.6.20"
val AkkaHttpVersion = "10.2.10"
ThisBuild / organization           := "io.github.liewhite"
ThisBuild / organizationName       := "liewhite"
ThisBuild / version                := sys.env.get("RELEASE_VERSION").getOrElse("0.4.2")
ThisBuild / scalaVersion           := "3.2.0"
ThisBuild / versionScheme          := Some("early-semver")
ThisBuild / sonatypeCredentialHost := "s01.oss.sonatype.org"
ThisBuild / publishTo              := sonatypePublishToBundle.value
sonatypeCredentialHost             := "s01.oss.sonatype.org"
ThisBuild / sonatypeRepository     := "https://s01.oss.sonatype.org/service/local"

lazy val root = project
    .in(file("."))
    .settings(
      name                                        := "rpc4s",
      libraryDependencies += "io.github.liewhite" %% "json" % "1.0.0",
      libraryDependencies ++= Seq(
        "com.typesafe.akka" %% "akka-actor-typed"            % AkkaVersion,
        "com.typesafe.akka" %% "akka-cluster-typed"          % AkkaVersion,
        "com.typesafe.akka" %% "akka-cluster-sharding-typed" % AkkaVersion,
        "com.typesafe.akka" %% "akka-serialization-jackson"  % AkkaVersion
      ),
      libraryDependencies += "ch.qos.logback" % "logback-classic" % "1.2.11",
      libraryDependencies += "org.scalameta" %% "munit"           % "0.7.29" % Test
    )
