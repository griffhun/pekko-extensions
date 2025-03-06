package com.github.griffhun.pekko.example.stream.cluster.singleton

import com.github.griffhun.pekko.db.streamer.{Message, StreamFromDB}
import com.github.griffhun.pekko.example.stream.cluster.singleton.db.{
  StreamOffset,
  StreamOffsetRepo,
  User,
  UserRepo
}
import org.apache.pekko.NotUsed
import org.apache.pekko.stream.scaladsl.{Flow, Source}

import scala.concurrent.{ExecutionContext, Future}

class UserStreamService(userRepo: UserRepo, streamOffsetRepo: StreamOffsetRepo)(
    implicit ec: ExecutionContext) {
  def sourceFactory[M](
      streamId: String,
      preCommitFlow: () => Flow[Message[Long, User], Message[Long, User], M])
      : () => Future[Source[StreamOffset, NotUsed]] = () => {
    for {
      offset <- streamOffsetRepo.load(streamId).map(_.getOrElse(StreamOffset(streamId, 0L)))
      s = StreamFromDB
        .sourceFromDB[Long, User](offset.offset, userRepo.stream, _.id)
        .via(preCommitFlow())
        .collect { case Message.Loaded(id, _) => id }
        .mapAsync(1)(id => streamOffsetRepo.commit(StreamOffset(streamId, offset = id)))
    } yield s
  }
}
