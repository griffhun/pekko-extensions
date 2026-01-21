package com.github.griffhun.pekko.example.stream.cluster.singleton.db

import org.apache.pekko.NotUsed
import org.apache.pekko.stream.scaladsl.Source
import slick.jdbc.JdbcBackend
import slick.jdbc.PostgresProfile.api.*

import scala.concurrent.Future

trait UserRepo {
  def init(): Future[Unit]

  def save(name: String): Future[User]

  def stream(fromId: Long): Source[User, NotUsed]
}

object UserRepo {

  class Impl(db: JdbcBackend.Database) extends UserRepo {

    override def init(): Future[Unit] = {
      db.run(users.schema.createIfNotExists)
    }

    override def save(name: String): Future[User] = {
      val action =
        (users returning users.map(_.id)) into ((user, newId) => user.copy(id = newId)) += User(
          0L,
          name)
      db.run(action)
    }

    override def stream(fromId: Long): Source[User, NotUsed] = {
      val action = users.filter(_.id > fromId).sortBy(_.id).result
      Source.fromPublisher(db.stream[User](action))
    }

    class UserTable(tag: Tag) extends Table[User](tag, "users") {

      def id = column[Long]("id", O.PrimaryKey, O.AutoInc)

      def name = column[String]("name")

      def * = (id, name).mapTo[User]
    }

    lazy val users = TableQuery[UserTable]
  }

}
