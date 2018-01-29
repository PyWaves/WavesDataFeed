package com.wavesplatform.datafeed.api

import akka.actor._
import akka.routing.{AddRoutee, RemoveRoutee, Routee}
import com.wavesplatform.datafeed.utils._
import play.api.libs.json._

object WebSocketRouter {

  case class TMessage(msg: JsObject, channel: String)

  case class AddSubscription(subscriber: Routee, channel: String)

  case class RemoveSubscription(subscriber: Routee, channel: String)

  case class RemoveAllSubscriptions(subscriber: Routee)

  case object Status

}

class WebSocketRouter extends Actor with Logging {

  var subscribers = Set[(Routee, String)]()
  var routees = Set[Routee]()

  import WebSocketRouter._

  def receive = {

    case ar: AddRoutee => routees = routees + ar.routee
    case rr: RemoveRoutee => routees = routees - rr.routee

    case AddSubscription(subscriber, channel) => { subscribers = subscribers + ((subscriber, channel))
      log.info("AddSubscription request. Subscribers : " + routees.size.toString + " Subscriptions : " + subscribers.size.toString)
      subscriber.send(Json.obj("status" -> "ok", "op" -> ("subscribe " + channel)), sender)
    }

    case RemoveSubscription(subscriber, channel) => { subscribers = subscribers - ((subscriber, channel))
      log.info("RemoveSubscription request.")
      subscriber.send(Json.obj("status" -> "ok", "op" -> ("unsubscribe " + channel)), sender)
    }

    case RemoveAllSubscriptions(subscriber) => { subscribers.foreach(s => if (s._1 == subscriber) subscribers -= s)
      log.info("RemoveAllSubscription request.")
      subscriber.send(Json.obj("status" -> "ok", "op" -> "unsubscribe all"), sender)
    }

    case TMessage(msg, channel) => {
      subscribers.foreach(s => if(s._2 == channel.take(s._2.size)) s._1.send(Json.obj("op" -> channel, "msg" -> msg), sender))
    }

    case Status => sender ! (routees.size, subscribers.map(s => s._1).size, subscribers.size)
  }
}