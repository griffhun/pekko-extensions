package com.github.griffhun.pekko.extensions

import org.apache.pekko.NotUsed
import org.apache.pekko.stream.{FlowShape, Graph, SourceShape}
import org.apache.pekko.stream.scaladsl.*

import java.util.concurrent.atomic.AtomicReference
import scala.concurrent.duration.*

sealed trait Message[ID, T]

object Message {
  final case class Loaded[ID, T](id: ID, data: T) extends Message[ID, T]
  final case class KeepAlive[ID, T]() extends Message[ID, T]
}

object StreamFromDB {
  import GraphDSL.Implicits._

  /**
   * Provide continuous Source for streaming data from the provided data source from the provided ID: fromId.
   * The stream stays open until the downstream cancelled. Each attempt to load data from the data source
   * is finished with a Message.KeepAlive message. The loaded data is wrapped in the Message.Loaded type
   *
   * @param fromId - the last ID which was processed by the downstream previously (starting point of the stream)
   * @param dataSource - source provider which start streaming data T from ID
   * @param idExtractor - Extracts the ID of the data T
   * @param pollInterval - DB poll interval
   * @param ordering - ordering function for ID comparison
   * @tparam ID - Type of the ID field
   * @tparam T - Type of the data provided by the data source
   * @return
   */
  def sourceFromDB[ID, T](
      fromId: ID,
      dataSource: ID => Source[T, ?],
      idExtractor: T => ID,
      pollInterval: FiniteDuration = 500.millis
  )(implicit ordering: Ordering[ID]): Source[Message[ID, T], NotUsed] = {
    Source.fromGraph(
      StreamFromDB.streamGraphFromDB(fromId, dataSource, idExtractor, pollInterval))
  }

  private def streamGraphFromDB[ID, T](
      fromId: ID,
      dataSource: ID => Source[T, ?],
      idExtractor: T => ID,
      pollInterval: FiniteDuration
  )(implicit ordering: Ordering[ID]): Graph[SourceShape[Message[ID, T]], NotUsed] = {
    GraphDSL
      .create() { implicit builder =>
        val start = builder.add(Source.single(fromId))
        val idState = AtomicReference[ID](fromId)
        val idMerge = builder.add(Merge[ID](2))
        val dbLoaderFlow: FlowShape[ID, Message[ID, T]] = builder.add(
          Flow[ID].flatMapConcat(id =>
            dataSource(id)
              .map(t => Message.Loaded(idExtractor(t), t))
              .concat(Source.single(Message.KeepAlive[ID, T]())))
        )
        // Important: eagerCancel=true make sure the stream stops when the actual downstream is cancelled
        val messageBroadcaster = builder.add(Broadcast[Message[ID, T]](2, eagerCancel = true))
        val nextIdExtractor = builder.add(
          Flow[Message[ID, T]].aggregateWithBoundary[ID, ID](
            allocate = () => {
              idState.get()
            }
          )(
            aggregate = (agg, message) =>
              message match {
                case Message.Loaded(id, _) if ordering.lt(agg, id) =>
                  idState.set(id)
                  (id, false)
                case Message.Loaded(_, _) =>
                  (agg, false)
                case Message.KeepAlive() =>
                  (agg, true)
              },
            harvest = identity,
            emitOnTimer = None
          )
        )

        start.out ~> idMerge.in(1)
        idMerge.out ~> dbLoaderFlow ~> messageBroadcaster.in
        messageBroadcaster.out(1) ~> nextIdExtractor.in
        nextIdExtractor.throttle(1, pollInterval) ~> idMerge.in(0)

        SourceShape(messageBroadcaster.out(0))
      }
      .named("streamFromDB")
  }
}
