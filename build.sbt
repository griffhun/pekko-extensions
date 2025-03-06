import Dependencies.{Pekko, *}
import sbt.Keys.libraryDependencies
import com.typesafe.sbt.MultiJvmPlugin.{autoImport, multiJvmSettings}
import sbt.TupleSyntax.t2ToTable2

import scala.math.Ordered.orderingToOrdered

ThisBuild / organization := "com.github.griffhun.pekko"
ThisBuild / version      := "0.1.0-SNAPSHOT"
ThisBuild / scalaVersion := "3.6.3"

lazy val dbStreamer = project
  .in(file("extensions/db-streamer"))
  .settings(
    name := "db-streamer",
    libraryDependencies ++= Pekko.stream ++ Testing.all
  )

lazy val clusterSingletonStream = project
  .in(file("extensions/cluster-singleton-stream"))
  .settings(
    name := "cluster-singleton-stream",
    libraryDependencies ++= Pekko.all ++ Testing.all ++ Logging.all
  )

lazy val exampleDbStreamerPostgres = project
  .in(file("examples/db-streamer-postgres"))
  .settings(
    name := "db-streamer-postgres",
    libraryDependencies ++= Pekko.stream ++ Testing.all ++ Postgres.all ++ TestContainers.all ++ Logging.all
  ).dependsOn(dbStreamer)

lazy val exampleClusterSingletonPostgres = project
  .in(file("examples/cluster-singleton-postgres"))
  .enablePlugins(MultiJvmPlugin)
  .configs(MultiJvm)
  .settings(multiJvmSettings)
  .settings(
    name := "cluster-singleton-postgres",
    libraryDependencies ++= Pekko.all ++ Testing.all ++ Postgres.all ++ TestContainers.all ++ Logging.all
  )
//  .settings(
//    Seq(
//      // make sure that MultiJvm test are compiled by the default test compilation
//      MultiJvm / compile <<= (MultiJvm / compile ) triggeredBy (Test / compile),
//      // disable parallel tests
//      Test / parallelExecution := false,
//      // make sure that MultiJvm tests are executed by the default test target,
//      // and combine the results from ordinary test and multi-jvm tests
//      Test / executeTests <<= (Test / executeTests, MultiJvm / executeTests) map {
//        case (testResults, multiNodeResults) =>
//          val overall = multiNodeResults.overall
////            if (testResults.overall ++ multiNodeResults.overall)
////              multiNodeResults.overall
////            else
////              testResults.overall
//          Tests.Output(overall,
//            testResults.events ++ multiNodeResults.events,
//            testResults.summaries ++ multiNodeResults.summaries)
//      })
//  )
  .dependsOn(dbStreamer, clusterSingletonStream)

lazy val root = (project in file("."))
  .aggregate(dbStreamer, clusterSingletonStream, exampleDbStreamerPostgres, exampleClusterSingletonPostgres)
  .settings(
    name := "pekko-extensions-root",
  )
  .enablePlugins(MultiJvmPlugin)
  .configs(MultiJvm)

