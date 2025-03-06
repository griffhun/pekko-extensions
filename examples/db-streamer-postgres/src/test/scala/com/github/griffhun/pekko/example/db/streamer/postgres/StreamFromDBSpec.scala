package com.github.griffhun.pekko.example.db.streamer.postgres

import PostgresTestContainers.config
import com.github.griffhun.pekko.db.streamer.{Message, StreamFromDB}
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.{Sink, Source}
import org.scalatest.wordspec.AsyncWordSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.Future
import scala.concurrent.duration.*

class StreamFromDBSpec extends AsyncWordSpec with Matchers {
  val actorSystem = ActorSystem("StreamFromDBSpec")
  implicit val materializer: Materializer = Materializer(actorSystem)

  def generateUserNames(n: Int): List[String] = (1 to n).map(i => s"User-$i").toList

  val repo = new UserRepo.Impl(config.db)

  "StreamFromDBSpec" when {
    "started" should {
      "stream from the db continuously" in {
        val usernames = generateUserNames(100)
        for {
          _ <- repo.init()
          saveStreamF = Source
            .fromIterator(() => usernames.iterator)
            .throttle(1, 10.millis)
            .mapAsync(1)(repo.save)
            .runWith(Sink.seq)
          streamed <- StreamFromDB
            .sourceFromDB[Long, User](1L, repo.stream, _.id, 100.millis)(Ordering[Long])
            .collect {
              case Message.Loaded(_, data) =>
                data
            }
            .take(99)
            .runWith(Sink.seq)
          saved <- saveStreamF
        } yield {
          streamed should have size 99
          streamed should contain theSameElementsInOrderAs saved.sortBy(_.id).tail
        }
      }
    }
  }

}
