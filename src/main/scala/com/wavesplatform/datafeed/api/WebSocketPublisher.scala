package com.wavesplatform.datafeed.api

import akka.actor._
import akka.routing.{ActorRefRoutee, AddRoutee, RemoveRoutee}
import akka.stream.actor.ActorPublisher
import play.api.libs.json.{Json, JsObject}

import scala.annotation.tailrec

object WebSocketPublisher {
  def props: Props = Props[WebSocketPublisher]

  final case class Job(payload: String)

  case object JobAccepted
  case object JobDenied
  case class Subscribe(channel: String)
  case class Unsubscribe(channel: String)
  case class UnsubscribeAll()
  case class Get(key: String, value:String)
}

class WebSocketPublisher(router: ActorRef) extends ActorPublisher[String] {

  import WebSocketPublisher._
  case class QueueUpdated()

  import akka.stream.actor.ActorPublisherMessage._
  import scala.collection.mutable

  val MaxBufferSize = 100
  var buf = Vector.empty[String]

  var queueUpdated = false;

  // on startup, register with routee
  override def preStart() {
    router ! AddRoutee(ActorRefRoutee(self))
  }

  override def postStop(): Unit = {
    router ! RemoveRoutee(ActorRefRoutee(self))
  }

  def receive = {
    case Subscribe(channel) => router ! WebSocketRouter.AddSubscription(ActorRefRoutee(self), channel)
    case Unsubscribe(channel) => router ! WebSocketRouter.RemoveSubscription(ActorRefRoutee(self), channel)
    case UnsubscribeAll => router ! WebSocketRouter.RemoveAllSubscriptions(ActorRefRoutee(self))

    case job: JsObject if buf.size == MaxBufferSize =>
      sender() ! JobDenied
    case job: JsObject =>
      sender() ! JobAccepted
      if (buf.isEmpty && totalDemand > 0)
        onNext(Json.prettyPrint(job))
      else {
        buf :+= Json.prettyPrint(job)
        deliver()
      }

    case Request(_) =>
      deliver()
    case Cancel =>
      router ! WebSocketRouter.RemoveAllSubscriptions(ActorRefRoutee(self))
      context.stop(self)
  }

  @tailrec final def deliver(): Unit =
    if (totalDemand > 0) {
      if (totalDemand <= Int.MaxValue) {
        val (use, keep) = buf.splitAt(totalDemand.toInt)
        buf = keep
        use foreach onNext
      } else {
        val (use, keep) = buf.splitAt(Int.MaxValue)
        buf = keep
        use foreach onNext
        deliver()
      }
  }
}

