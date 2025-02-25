package com.github.griffhun.pekko.example.postgres

import org.apache.pekko.NotUsed
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.{Sink, Source}
import org.scalatest.wordspec.AsyncWordSpec
import org.scalatest.matchers.should.Matchers

import java.util.concurrent.atomic.AtomicReference
import scala.concurrent.Future
import scala.concurrent.duration.*

class StreamFromDBSpec extends AsyncWordSpec with Matchers {
  val actorSystem = ActorSystem("StreamFromDBSpec")
  implicit val materializer: Materializer = Materializer(actorSystem)

  final case class User(id: Long, name: String)

  trait Repo {
    def save(name: String): Future[User]

    def stream(fromId: Long): Source[User, NotUsed]
  }

  def generateUserNames(n: Int): List[String] = (1 to n).map(i => s"User-$i").toList

  val repo = new Repo {
    val table: AtomicReference[List[User]] = AtomicReference[List[User]](List.empty)
    override def save(name: String): Future[User] = {
      val updatedTable = table.updateAndGet { i =>
        val currentMaxId = i.map(_.id).maxOption.getOrElse(0L)
        User(currentMaxId + 1L, name) :: i
      }
      val user = updatedTable.head
      Future.successful(user)
    }

    override def stream(fromId: Long): Source[User, NotUsed] =
      Source.fromIterator(() => table.get().reverse.filter(_.id > fromId).iterator)
  }

  "StreamFromDBSpec" when {
    "started" should {
      "stream from the db continuously" in {
        val usernames = generateUserNames(100)
        val saveStreamF = Source
          .fromIterator(() => usernames.iterator)
          .throttle(1, 10.millis)
          .mapAsync(1)(repo.save)
          .runWith(Sink.seq)
        for {
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
