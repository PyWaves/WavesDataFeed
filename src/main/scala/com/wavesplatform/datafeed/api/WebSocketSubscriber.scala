package com.wavesplatform.datafeed.api


import akka.actor._
import akka.routing.{ActorRefRoutee, AddRoutee, RemoveRoutee}
import akka.stream.actor.{ActorSubscriber, WatermarkRequestStrategy}
import akka.http.scaladsl.model.ws._
import akka.stream.actor.ActorPublisherMessage.Cancel
import akka.stream.actor.ActorSubscriberMessage.OnNext
import play.api.libs.json._
import play.api.libs.functional.syntax._
import com.wavesplatform.datafeed.api._
import com.wavesplatform.datafeed.utils._
import com.wavesplatform.datafeed.model.TimeSeries

import scala.util.{Failure, Success, Try}

object WebSocketSubscriber {
  def props: Props = Props[WebSocketSubscriber]

  final case class IncomingMessage(text: String)
  case class RegisterSource(sourceActor: ActorRef)

}

class WebSocketSubscriber(apiController: ApiController) extends ActorSubscriber with Logging {

  import WebSocketSubscriber._
  var publisherActor: ActorRef = null
  val requestStrategy = WatermarkRequestStrategy(20)

  val ErrorInvalidRequest = Json.obj("status" -> "error",
    "message" -> "Invalid request")
  val ErrorUnknownCommand = Json.obj("status" -> "error",
    "message" -> "Unknown command")
  val ErrorMalformedGetRequest = Json.obj("status" -> "error",
    "message" -> "Malformed get request")

  def toInt(s: String): Option[Int] = {
    try {
      Some(s.toInt)
    } catch {
      case e: Exception => None
    }
  }

  def toLong(s: String): Option[Long] = {
    try {
      Some(s.toLong)
    } catch {
      case e: Exception => None
    }
  }

  def receive = {

    case OnNext(TextMessage.Strict(text)) => {
      val json: Try[JsValue] = Try(Json.parse(text))
      json match {
        case Success(s) =>
          (s \ "op").validate[String] match {
            case op: JsSuccess[String] =>
              val ops = op.get.split(" |/").filter(_.nonEmpty)
              val arg = ops.drop(1).mkString("/")
              ops(0).toLowerCase match {
                case "ping" => publisherActor ! Json.obj("op" -> "pong")
                case "get" =>
                  ops(1).toLowerCase match {
                    case "markets" => publisherActor ! Json.obj("op" -> arg, "msg" -> apiController.apiPairsList)

                    case "trades" => publisherActor !
                      (if (ops.size==5) apiController.apiGetTradesLimit(ops(2), ops(3), toInt(ops(4)).getOrElse(0)) match {
                        case Left(l) => l
                        case Right(r) => Json.obj("op" -> arg, "msg" -> r)
                      }
                      else if (ops.size==6 && ops(4).forall(_.isDigit)) apiController.apiGetTradesRange(ops(2), ops(3), toLong(ops(4)).getOrElse(0), toLong(ops(5)).getOrElse(0)) match {
                        case Left(l) => l
                        case Right(r) => Json.obj("op" -> arg, "msg" -> r)
                    }
                      else apiController.apiGetTradesByAddress(ops(2), ops(3), ops(4), toInt(ops(5)).getOrElse(0)) match {
                        case Left(l) => l
                        case Right(r) => Json.obj("op" -> arg, "msg" -> r)
                      })

                    case "candles" => publisherActor !
                      (if (ops.size==6) apiController.apiGetCandlesLimit(ops(2), ops(3), toInt(ops(4)).getOrElse(0), toInt(ops(5)).getOrElse(0)) match {
                        case Left(l) => l
                        case Right(r) => Json.obj("op" -> arg, "msg" -> r)
                      }
                      else if (ops.size==7) apiController.apiGetCandlesRange(ops(2), ops(3), toInt(ops(4)).getOrElse(0), toLong(ops(5)).getOrElse(0), toLong(ops(6)).getOrElse(0)) match {
                        case Left(l) => l
                        case Right(r) => Json.obj("op" -> arg, "msg" -> r)
                      })

                    case _ => publisherActor ! ErrorMalformedGetRequest
                  }
                case "subscribe" => publisherActor ! WebSocketPublisher.Subscribe(arg)
                case "unsubscribe" =>
                  ops(1).toLowerCase match {
                    case "all" => publisherActor ! WebSocketPublisher.UnsubscribeAll
                    case _ => publisherActor ! WebSocketPublisher.Unsubscribe(arg)
                  }
                case "close" => publisherActor ! cancel()
                case _ => publisherActor ! ErrorUnknownCommand
              }
            case e: JsError => publisherActor ! ErrorInvalidRequest
          }
        case Failure(e) => publisherActor ! ErrorInvalidRequest
      }
    }

    case RegisterSource(sourceActor) => publisherActor = sourceActor

    case Cancel => context.stop(self)
  }

}

