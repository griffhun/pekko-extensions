package com.github.griffhun.stream.cluster.singleton

import com.github.griffhun.stream.cluster.singleton.ClusterSingletonStream.Command
import com.typesafe.scalalogging.StrictLogging
import org.apache.pekko.Done
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.actor.typed.{
  ActorRef,
  ActorSystem,
  Behavior,
  Scheduler,
  SupervisorStrategy
}
import org.apache.pekko.cluster.typed.{ClusterSingleton, SingletonActor}
import org.apache.pekko.stream.{Materializer, SharedKillSwitch}
import org.apache.pekko.stream.scaladsl.{Sink, Source}

import scala.concurrent.Future
import scala.util.{Failure, Success}

class ClusterSingletonStream[T, M] private (
    streamFactory: () => Future[Source[T, M]],
    sharedKillSwitch: SharedKillSwitch)(implicit system: ActorSystem[Nothing])
    extends StrictLogging {
  implicit val scheduler: Scheduler = system.scheduler
  private val singletonManager = ClusterSingleton(system)
  // Start if needed and provide a proxy to a named singleton
  private val proxy: ActorRef[ClusterSingletonStream.Command] = singletonManager.init(
    SingletonActor(
      Behaviors.supervise(createBehavior()).onFailure[Exception](SupervisorStrategy.restart),
      "StreamGuard"
    ).withStopMessage(ClusterSingletonStream.Command.Stop)
  )

  private def createBehavior(implicit mat: Materializer): Behavior[Command] = {
    def running(): Behavior[Command] = {
      Behaviors.receiveMessage[Command] {
        case Command.Stop =>
          sharedKillSwitch.shutdown()
          stopping()
        case Command.Failed(e) =>
          throw e // scalafix:ok DisableSyntax:throw
        case Command.Succeed =>
          Behaviors.stopped
      }
    }

    def stopping(): Behavior[Command] = {
      Behaviors.receiveMessage[Command] {
        case Command.Stop =>
          Behaviors.same
        case Command.Failed(_) =>
          Behaviors.stopped
        case Command.Succeed =>
          Behaviors.stopped
      }
    }

    Behaviors.setup { context =>
      context.pipeToSelf(
        Source.futureSource(streamFactory()).via(sharedKillSwitch.flow).runWith(Sink.ignore)) {
        case Failure(e) =>
          logger.warn(s"Stream failed: ${e.getMessage}", e)
          Command.Failed(e)
        case Success(_) =>
          logger.info("Stream succeed")
          Command.Succeed
      }
      running()
    }
  }
}

object ClusterSingletonStream {

  def applyF[T, M](
      streamFactory: () => Future[Source[T, M]],
      sharedKillSwitch: SharedKillSwitch)(
      implicit system: ActorSystem[Nothing]): ClusterSingletonStream[T, M] = {
    new ClusterSingletonStream(streamFactory, sharedKillSwitch)
  }

  def apply[T, M](streamFactory: () => Source[T, M], sharedKillSwitch: SharedKillSwitch)(
      implicit system: ActorSystem[Nothing]): ClusterSingletonStream[T, M] = {
    applyF[T, M](() => Future.successful(streamFactory()), sharedKillSwitch)
  }

  sealed trait Command

  object Command {
    case object Stop extends Command
    final case class Failed(error: Throwable) extends Command
    case object Succeed extends Command
  }
}
