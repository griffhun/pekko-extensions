package com.github.griffhun.pekko.example.stream.cluster.singleton.db

import slick.jdbc.JdbcBackend
import slick.jdbc.PostgresProfile.api.*

import scala.concurrent.{ExecutionContext, Future}

trait StreamOffsetRepo {
  def init(): Future[Unit]

  def commit(s: StreamOffset): Future[StreamOffset]

  def load(streamId: String): Future[Option[StreamOffset]]
}

object StreamOffsetRepo {

  class Impl(db: JdbcBackend.Database)(implicit ec: ExecutionContext) extends StreamOffsetRepo {

    override def init(): Future[Unit] = {
      db.run(streamOffsets.schema.createIfNotExists)
    }

    override def commit(s: StreamOffset): Future[StreamOffset] = {
      val action = streamOffsets.insertOrUpdate(s)
      db.run(action).map(_ => s)
    }

    override def load(streamId: String): Future[Option[StreamOffset]] = {
      val action = streamOffsets.filter(_.streamId === streamId).result.headOption
      db.run(action)
    }

    class StreamOffsets(tag: Tag) extends Table[StreamOffset](tag, "stream_offset") {

      def streamId = column[String]("stream_id", O.PrimaryKey)

      def committedOffset = column[Long]("offset")

      def * = (streamId, committedOffset).mapTo[StreamOffset]
    }

    lazy val streamOffsets = TableQuery[StreamOffsets]
  }

}
