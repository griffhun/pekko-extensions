package com.github.griffhun.pekko.example.stream.cluster.singleton

import com.github.griffhun.stream.cluster.singleton.ClusterSingletonStream
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.StrictLogging
import org.apache.pekko.Done
import org.apache.pekko.actor.ActorSystem as ClassicActorSystem
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.adapter.*
import org.apache.pekko.stream.KillSwitches
import org.apache.pekko.stream.scaladsl.Source

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.*

object TestApp2 extends App with StrictLogging {
  val system = ClassicActorSystem("TestApp", ConfigFactory.load("application-2.conf"))
  implicit val typedSystem: ActorSystem[Nothing] = system.toTyped
  val sharedKillSwitch = KillSwitches.shared("my-kill-switch")
  val sourceFactory = () =>
    Future.successful(
      Source
        .tick(1.millis, 1.millis, "s")
        .zipWithIndex
        .wireTap { (_, index) => logger.debug(s"$index") }
        .mapAsync(1) {
          case (_, i) if i == 100 => Future.failed(new RuntimeException("Bumm!"))
          case (_, i) => Future.successful(i)
        }
        .take(1000))
  ClusterSingletonStream.applyF(sourceFactory, sharedKillSwitch): Unit

  for {
    _ <- Source.tick(180.seconds, 1.millis, "s").take(1).run()
    _ <- system.terminate()
  } yield Done
}
