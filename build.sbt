import Dependencies.{Pekko, *}
import sbt.Keys.libraryDependencies

val scala3Version = "3.6.3"

lazy val extensions = project
  .in(file("extensions"))
  .settings(
    name := "pekko-extensions",
    scalaVersion := scala3Version,
    libraryDependencies ++= Pekko.all ++ Testing.all
  )

lazy val examplePostgres = project
  .in(file("example-postgres"))
  .settings(
    name := "example-postgres",
    scalaVersion := scala3Version,
    libraryDependencies ++= Pekko.all ++ Testing.all ++ Postgres.all ++ TestContainers.all ++ Logback.all
  ).dependsOn(extensions)

lazy val root = (project in file("."))
  .aggregate(extensions, examplePostgres)
  .settings(
    name := "pekko-extensions-root",
    version := "0.1.0-SNAPSHOT",
    scalaVersion := scala3Version
  )

