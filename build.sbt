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
      libraryDependencies += "io.github.liewhite" %% "common"                       % "0.0.3",
      libraryDependencies += "org.typelevel"      %% "shapeless3-deriving"          % "3.0.3",
      libraryDependencies += "dev.zio"            %% "zio"                          % "2.0.6",
      libraryDependencies += "dev.zio"            %% "zio-json"                     % "0.4.2",
      libraryDependencies += "com.devsisters"     %% "shardcake-manager"            % "2.0.5",
      libraryDependencies += "com.devsisters"     %% "shardcake-protocol-grpc"      % "2.0.5",
      libraryDependencies += "com.devsisters"     %% "shardcake-serialization-kryo" % "2.0.5",
      libraryDependencies += "org.scalameta" %% "munit" % "0.7.29" % Test
    )
