package sample

import com.github.griffhun.pekko.db.streamer.Message
import com.github.griffhun.pekko.example.stream.cluster.singleton.UserStreamService
import com.github.griffhun.pekko.example.stream.cluster.singleton.db.{PostgresConfig, StreamOffsetRepo, User, UserRepo}
import com.github.griffhun.stream.cluster.singleton.ClusterSingletonStream
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.StrictLogging
import org.apache.pekko.Done
import org.apache.pekko.actor.ActorSystem as ClassicActorSystem
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.adapter.*
import org.apache.pekko.stream.KillSwitches
import org.apache.pekko.stream.scaladsl.{Flow, Sink, Source}
import sample.DbConfig.postgresConfig

import scala.concurrent.Future
import scala.concurrent.duration.*
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Random

object DbConfig {
  val postgresConfig = PostgresConfig(
    jdbcUrl = "jdbc:postgresql://localhost:15432/stream_db",
    username = "stream_db",
    password = "totoLOTTO7650"
  )
}

object StreamTestMultiJvmNode1 extends StrictLogging{

  def main(args: Array[String]): Unit = {
    StreamTestNode(1, "application-1.conf").run(1.seconds, 20.seconds): Unit
  }
}

object StreamTestMultiJvmNode2 extends StrictLogging {

  def main(args: Array[String]): Unit = {
    StreamTestNode(2, "application-2.conf").run(5.seconds, 40.seconds): Unit
  }
}

object StreamTestMultiJvmNode3 extends StrictLogging {

  def main(args: Array[String]): Unit = {
    StreamTestNode(3, "application-3.conf").run(10.seconds, 60.seconds): Unit
  }
}

object StreamWriter extends StrictLogging {
  def generateUserNames(n: Int): List[String] = (1 to n).map(i => s"User-$i").toList

  def main(args: Array[String]): Unit = {
    val config = ConfigFactory.load("stream-writer.conf")
    implicit val system: ClassicActorSystem = ClassicActorSystem("DbWriter", config)
    val userRepo = new UserRepo.Impl(postgresConfig.db)
    val usernames = generateUserNames(10000)
    (for {
      _ <- userRepo.init()
      saveStreamF = Source
        .fromIterator(() => usernames.iterator)
        .throttle(1, 50.millis)
        .mapAsync(1)(userRepo.save)
        .wireTap(u => logger.debug(s"User saved: $u"))
        .runWith(Sink.seq)
      _ <- saveStreamF
      _ <- system.terminate()
    } yield Done): Unit
  }
}

final case class StreamTestNode(node: Int, confName: String) extends StrictLogging {
  private val config = ConfigFactory.load(confName)
  private val system = ClassicActorSystem("StreamTest", config)
  implicit val typedSystem: ActorSystem[Nothing] = system.toTyped
  private val sharedKillSwitch = KillSwitches.shared("my-kill-switch")
  private val userRepo = new UserRepo.Impl(postgresConfig.db)
  private val streamOffsetRepo = new StreamOffsetRepo.Impl(postgresConfig.db)
  private val userStreamService = new UserStreamService(userRepo, streamOffsetRepo)
  private val sourceFactory = userStreamService.sourceFactory("singleton-2", () => Flow[Message[Long, User]].mapAsync(1) {
    case Message.Loaded(id, _) if id % 100 == 0 && Random.nextInt(100) < 50 => //Emulating failure 50% after every 100 message
      Future.failed(new RuntimeException("Emulated failure"))
    case msg =>
      Future.successful(msg)
  }.wireTap(s => logger.info(s"$s")))

  def run(startAfter: FiniteDuration, stopAfter: FiniteDuration): Future[Done] = {
    for {
      _ <- streamOffsetRepo.init()
      _ <- userRepo.init()
      _ <- Source.tick(startAfter, 1.millis, "").take(1).run()
      _ = ClusterSingletonStream.applyF(sourceFactory, sharedKillSwitch): Unit
      _ <- Source.tick(stopAfter, 1.millis, "").take(1).run()
      _ <- system.classicSystem.terminate()
    } yield Done
  }
}
