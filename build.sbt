import Dependencies._

val scala3Version = "3.6.3"

lazy val root = project
  .in(file("."))
  .settings(
    name := "pekko-extensions",
    version := "0.1.0-SNAPSHOT",

    scalaVersion := scala3Version,

    libraryDependencies ++= Pekko.all ++ Postgres.all ++ Testing.all ++ TestContainers.all
  )
