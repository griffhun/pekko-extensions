import sbt.*
object Dependencies {

  object Pekko {
    val version = "1.1.2"
    val pekkoStream = "org.apache.pekko" %% "pekko-stream" % version
    val pekkoStreamTestKit = "org.apache.pekko" %% "pekko-stream-testkit" % version % Test
    val cluster = "org.apache.pekko" %% "pekko-cluster-typed" % version
    val stream = Seq(pekkoStream, pekkoStreamTestKit)
    val all = stream ++ Seq(cluster)
  }

  object Postgres {
    val postgres = "org.postgresql" % "postgresql" % "42.7.4"
    val slick = "com.typesafe.slick" %% "slick"% "3.5.2"

    val all = Seq(postgres, slick)
  }

  object Testing {
    val scalaTest = "org.scalatest" %% "scalatest" % "3.2.19" % Test
    val scalaTestPlus = "org.scalatestplus" %% "scalacheck-1-16" % "3.2.14.0" % Test
    val scalaMock = "org.scalamock" %% "scalamock" % "6.2.0" % Test

    val all = Seq(scalaTest, scalaTestPlus, scalaMock)
  }


  object TestContainers {
    val scalatest = "com.dimafeng" %% "testcontainers-scala-scalatest" % "0.41.8" % Test
    val postgres = "org.testcontainers" % "postgresql" % "1.20.5" % Test
    val all = Seq(scalatest, postgres)
  }

  object Logging {
    val logback = "ch.qos.logback" % "logback-classic" % "1.5.17"
    val scalaLogging = "com.typesafe.scala-logging" %% "scala-logging" % "3.9.5"

    val all = Seq(logback, scalaLogging)
  }

}
