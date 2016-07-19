/*
 * Copyright 2016 Dennis Vriend
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package akka.persistence.query.extension

import akka.actor.{ ActorLogging, ActorSystem, Props }
import akka.event.LoggingReceive
import akka.persistence.query.EventEnvelope
import akka.persistence.{ PersistentActor, Recovery, RecoveryCompleted, SnapshotOffer }
import akka.stream._
import akka.stream.actor.ActorPublisher
import akka.stream.integration.activemq.AckBidiFlow
import akka.stream.scaladsl.{ Flow, GraphDSL, Keep, Sink, Source }
import akka.util.Timeout
import akka.{ Done, NotUsed }
import com.typesafe.config.Config

import scala.concurrent.{ ExecutionContext, Future }
import scala.concurrent.duration._
import scala.util.Failure
import scalaz.syntax.std.boolean._

object ResumableQueryConfig {
  def apply(config: Config): ResumableQueryConfig = {
    ResumableQueryConfig(
      config.hasPath("snapshot-interval").option(config.getString("snapshot-interval").toLong),
      config.hasPath("backpressure-buffer").option(config.getString("backpressure-buffer").toLong),
      config.hasPath("journal-plugin-id").option(config.getString("journal-plugin-id")).filter(_.nonEmpty),
      config.hasPath("snapshot-plugin-id").option(config.getString("snapshot-plugin-id")).filter(_.nonEmpty)
    )
  }
}

case class ResumableQueryConfig(
  snapshotInterval: Option[Long],
  backpressureBuffer: Option[Long],
  journalPluginId: Option[String],
  snapshotPluginId: Option[String]
)

object ResumableQuery {
  def apply(
    queryName: String,
    query: Long => Source[EventEnvelope, NotUsed],
    snapshotInterval: Option[Long] = Some(250),
    backpressureBuffer: Option[Long] = Some(1),
    journalPluginId: String = "",
    snapshotPluginId: String = ""
  )(implicit mat: Materializer, ec: ExecutionContext, system: ActorSystem, timeout: Timeout): Flow[Any, EventEnvelope, Future[Done]] = {
    import akka.pattern.ask

    val cfg = system.settings.config

    val queryConfig = cfg match {
      case _ if cfg.hasPath(queryName)         => ResumableQueryConfig(cfg.getConfig(queryName))
      case _ if cfg.hasPath("resumable-query") => ResumableQueryConfig(cfg.getConfig("resumable-query"))
      case _                                   => ResumableQueryConfig(snapshotInterval, backpressureBuffer, Option(journalPluginId), Option(snapshotPluginId))
    }

    val writer = system.actorOf(Props(new ResumableQueryWriter(queryName, queryConfig.snapshotInterval, queryConfig.journalPluginId.getOrElse(""), queryConfig.snapshotPluginId.getOrElse(""))))
    val sink = Flow[(Long, Any)].map { case (offset, _) => offset }.mapAsync(1) { offset =>
      writer ? offset
    }.toMat(Sink.ignore)(Keep.right)

    Flow.fromGraph(GraphDSL.create(sink) { implicit b => snk =>
      import GraphDSL.Implicits._
      val src = Source.actorPublisher[Long](Props(new ResumableQueryPublisher(queryName, queryConfig.journalPluginId.getOrElse(""), queryConfig.snapshotPluginId.getOrElse(""))))
        .flatMapConcat(query).map(ev => (ev.offset, ev))
      val bidi = b.add(AckBidiFlow[Long, EventEnvelope, Any]())
      val backpressure = Flow[(Long, EventEnvelope)].buffer(1, OverflowStrategy.backpressure)
      src ~> backpressure ~> bidi.in1
      bidi.out2 ~> snk
      FlowShape(bidi.in2, bidi.out1)
    })
  }
}

private[persistence] class ResumableQueryPublisher(
  queryName: String,
  override val journalPluginId: String,
  override val snapshotPluginId: String
)(implicit mat: Materializer, ec: ExecutionContext, system: ActorSystem) extends PersistentActor
    with ActorPublisher[Long]
    with ActorLogging {

  override val persistenceId: String = queryName
  final val RecoveredMessage = "RECOVERED"
  var latestOffset: Long = 0L
  log.debug("Creating: '{}': '{}'", queryName, this.hashCode())

  override val receiveRecover: Receive = {
    case SnapshotOffer(_, offset: Long) =>
      log.debug("Query: {} is recovering from snapshot offer: {}", queryName, offset)
      latestOffset = offset
    case offset: Long =>
      log.debug("Query: {} is recovering applying offset event: {}", queryName, offset)
      latestOffset = offset
    case RecoveryCompleted =>
      log.debug("Query: {} has finished recovering to offset: {}", queryName, latestOffset)
      context.system.scheduler.scheduleOnce(0.seconds, self, RecoveredMessage)
  }

  override val receiveCommand: Receive = {
    case RecoveredMessage if totalDemand == 0 =>
      context.system.scheduler.scheduleOnce(0.seconds, self, RecoveredMessage)
    case RecoveredMessage if totalDemand > 0 =>
      onNext(latestOffset)
      onCompleteThenStop()

  }
}

private[persistence] class ResumableQueryWriter(queryName: String, snapshotInterval: Option[Long] = None, override val journalPluginId: String, override val snapshotPluginId: String)(implicit mat: Materializer, ec: ExecutionContext, system: ActorSystem) extends PersistentActor with ActorLogging {
  override val recovery: Recovery = Recovery.none
  override val persistenceId: String = queryName
  override val receiveRecover: Receive = PartialFunction.empty

  log.debug("Creating: '{}': '{}'", queryName, this.hashCode())

  override val receiveCommand: Receive = LoggingReceive {
    case offset: Long =>
      log.debug("Query: '{}' is saving offset: '{}'", queryName, offset)
      persist(offset) { _ =>
        snapshotInterval.foreach { interval =>
          if (lastSequenceNr != 0L && lastSequenceNr % interval == 0)
            saveSnapshot(offset)
        }
        sender() ! akka.actor.Status.Success("")
      }
  }

  override protected def onPersistFailure(cause: Throwable, event: Any, seqNr: Long): Unit = {
    super.onPersistFailure(cause, event, seqNr)
    sender() ! Failure(cause)
  }

  override protected def onPersistRejected(cause: Throwable, event: Any, seqNr: Long): Unit = {
    super.onPersistRejected(cause, event, seqNr)
    sender() ! Failure(cause)
  }
}
