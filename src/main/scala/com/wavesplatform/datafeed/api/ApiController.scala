package com.wavesplatform.datafeed.api

import com.wavesplatform.datafeed.settings.Constants
import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout
import play.api.libs.json.{JsNull, JsObject, JsValue, Json}
import com.wavesplatform.datafeed.model.{TimeSeries, Trade}
import com.wavesplatform.datafeed.settings.WDFSettings

import scala.concurrent.duration._
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global



case class ApiController(settings: WDFSettings, timeseries: TimeSeries, router: ActorRef) {
  private implicit val timeout: Timeout = 5.seconds

  val ValidTimeFrames = List(5, 15, 30, 60, 240, 1440)
  val MaxRecordsPerRequest = 1000
  val ValidTimeStampMin = 1483228800000L
  val ValidTimeStampMax = 4133894400000L
  val AddressLength = 35

  val ErrorInvalidAssetPair = Json.obj("status" -> "error",
    "message" -> "Asset pair not available")

  val ErrorInvalidTimeframe = Json.obj("status" -> "error",
    "message" -> ("Invalid timeframe. Valid options are: " + ValidTimeFrames.mkString(", ")))

  val ErrorOutOfBounds = Json.obj("status" -> "error",
    "message" -> ("Request out of bounds. Valid numbers are between 1 and " + MaxRecordsPerRequest.toString))

  val ErrorInvalidTimestamp = Json.obj("status" -> "error",
    "message" -> "Invalid timestamp")

  val ErrorInvalidAddress = Json.obj("status" -> "error",
    "message" -> "Invalid address")

  def getAssetId(a: String): String = if (a.toUpperCase=="WAVES") "WAVES" else if (a.length >= 2 && a.length <= 20) settings.symbols.getOrElse(a.toUpperCase, "") else a

  def apiStatus: Future[JsObject] = {
      for {
        (conns, unique, subs) <- router ? WebSocketRouter.Status
      } yield
        Json.obj(
          "Version" -> (Constants.Product + " " + Constants.Version),
          "Blockchain synchronizer" -> "running",
          "Asset pairs" -> timeseries.numberOfMarkets,
          "REST API" -> (if (settings.restEnable) "enabled" else "disabled"),
          "WebSocket API" -> (if (settings.websocketEnable) "enabled" else "disabled"),
          "WebSocket connections" -> conns.toString,
          "WebSocket subscribers" -> unique.toString,
          "WebSocket subscriptions" -> subs.toString)
    }


  def apiPairsList: List[JsObject] = timeseries.markets

  def apiSymbolsList: List[JsObject] =
    settings.symbols.toSeq.sorted.map(s => Json.obj("symbol" -> s._1,
      "assetID" -> s._2)).toList

  def apiGetTicker(amountAsset: String, priceAsset: String): Either[JsObject, JsObject] =
    timeseries.getPair(getAssetId(amountAsset), getAssetId(priceAsset)) match {
      case Some(pair) => Right(pair.getTicker)
      case None => Left(ErrorInvalidAssetPair)
    }

  def apiTickers: List[JsObject] = timeseries.tickers

  def apiGetTradesRange(amountAsset: String, priceAsset: String, fromTimeStamp: Long, toTimeStamp: Long): Either[JsObject, List[JsObject]] =
    timeseries.getPair(getAssetId(amountAsset), getAssetId(priceAsset)) match {
      case Some(pair) =>
        if (fromTimeStamp < ValidTimeStampMin ||
          fromTimeStamp > ValidTimeStampMax ||
          toTimeStamp < ValidTimeStampMin ||
          toTimeStamp > ValidTimeStampMax) Left(ErrorInvalidTimestamp)
        else Right(pair.getTradesRange(fromTimeStamp, toTimeStamp))
      case None => Left(ErrorInvalidAssetPair)
    }

  def apiGetTradesLimit(amountAsset: String, priceAsset: String, limit: Int): Either[JsObject, List[JsObject]] =
    timeseries.getPair(getAssetId(amountAsset), getAssetId(priceAsset)) match {
      case Some(pair) =>
        if (limit < 1 || limit > MaxRecordsPerRequest) Left(ErrorOutOfBounds)
        else Right(pair.getTradesLimit(limit))
      case None => Left(ErrorInvalidAssetPair)
    }

  def apiGetTradesByAddress(amountAsset: String, priceAsset: String, address: String, limit: Int): Either[JsObject, List[JsObject]] =
    timeseries.getPair(getAssetId(amountAsset), getAssetId(priceAsset)) match {
      case Some(pair) =>
        if (address.length != AddressLength) Left(ErrorInvalidAddress)
        else if (limit < 1 || limit > MaxRecordsPerRequest) Left(ErrorOutOfBounds)
        else Right(pair.getTradesByAddress(address, limit))
      case None => Left(ErrorInvalidAssetPair)
    }

  def apiGetCandlesRange(amountAsset: String, priceAsset: String, timeframe: Int, fromTimeStamp: Long, toTimeStamp:Long): Either[JsObject, List[JsObject]] =
    timeseries.getPair(getAssetId(amountAsset), getAssetId(priceAsset)) match {
      case Some(pair) =>
        if (!ValidTimeFrames.contains(timeframe)) Left(ErrorInvalidTimeframe)
        else if (fromTimeStamp < ValidTimeStampMin ||
          fromTimeStamp > ValidTimeStampMax ||
          fromTimeStamp < ValidTimeStampMin ||
          fromTimeStamp > ValidTimeStampMax) Left(ErrorInvalidTimestamp)
        else Right(pair.getCandlesRange(fromTimeStamp, toTimeStamp, timeframe))
      case None => Left(ErrorInvalidAssetPair)
    }

  def apiGetCandlesLimit(amountAsset: String, priceAsset: String, timeframe: Int, limit: Int): Either[JsObject, List[JsObject]] =
    timeseries.getPair(getAssetId(amountAsset), getAssetId(priceAsset)) match {
      case Some(pair) =>
        if (!ValidTimeFrames.contains(timeframe)) Left(ErrorInvalidTimeframe)
        else if (limit < 1 || limit > MaxRecordsPerRequest) Left(ErrorOutOfBounds)
        else Right(pair.getCandlesLimit(timeframe, limit))
      case None => Left(ErrorInvalidAssetPair)
    }

}

