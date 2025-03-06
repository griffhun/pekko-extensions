import Dependencies.{Pekko, *}
import sbt.Keys.libraryDependencies

ThisBuild / organization := "com.github.griffhun.pekko"
ThisBuild / version      := "0.1.0-SNAPSHOT"
ThisBuild / scalaVersion := "3.6.3"

lazy val dbStreamer = project
  .in(file("extensions/db-streamer"))
  .settings(
    name := "db-streamer",
    libraryDependencies ++= Pekko.all ++ Testing.all
  )

lazy val exampleDbStreamerPostgres = project
  .in(file("examples/db-streamer-postgres"))
  .settings(
    name := "db-streamer-postgres",
    libraryDependencies ++= Pekko.all ++ Testing.all ++ Postgres.all ++ TestContainers.all ++ Logback.all
  ).dependsOn(dbStreamer)

lazy val root = (project in file("."))
  .aggregate(dbStreamer, exampleDbStreamerPostgres)
  .settings(
    name := "pekko-extensions-root",
  )

