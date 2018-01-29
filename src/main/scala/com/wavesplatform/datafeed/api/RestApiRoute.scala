package com.wavesplatform.datafeed.api

import javax.ws.rs.Path

import scala.concurrent.duration._
import scala.language.postfixOps
import akka.http.scaladsl.marshalling.ToResponseMarshallable
import akka.http.scaladsl.server.{Directives, Route}
import akka.http.scaladsl.model.HttpResponse
import akka.util.Timeout
import com.wavesplatform.datafeed.model._
import com.wavesplatform.datafeed.settings.WDFSettings
import io.swagger.annotations._
import play.api.libs.json._
import play.api.libs.json.Reads

import scala.concurrent._
import ExecutionContext.Implicits.global

@Path("/datafeed")
@Api(value = "datafeed")
case class RestApiRoute(settings: WDFSettings, apiController: ApiController) extends Directives with ApiMarshallers {
  private implicit val timeout: Timeout = 5.seconds


  def json[A: Reads](f: A => ToResponseMarshallable): Route = entity(as[A]) { a =>
    complete(f(a))
  }

var route: Route =
pathPrefix("datafeed") {
  datafeedStatus ~ listPairs ~ listSymbols ~ ticker ~ tickers ~ tradesPeriod ~ tradesLimit ~ tradesByAddress ~ candlesRange ~ candlesLimit
}

  @Path("/status")
  @ApiOperation(value = "Datafeed Status", notes = "Get datafeed status", httpMethod = "GET")
  def datafeedStatus: Route = path("status") {
    complete(apiController.apiStatus)
  }

  @Path("/symbols")
  @ApiOperation(value = "Asset symbols", notes = "Get list of asset symbols", httpMethod = "GET")
  def listSymbols: Route = path("symbols") {
    complete(apiController.apiSymbolsList)
  }

  @Path("/markets")
  @ApiOperation(value = "Available markets", notes = "List of all traded markets", httpMethod = "GET")
  def listPairs: Route = path("markets") {
    complete(apiController.apiPairsList)
  }

  @Path("/ticker/{amountAsset}/{priceAsset}")
  @ApiOperation(value = "Ticker",
    notes = "Get ticker for a given Asset Pair",
    httpMethod = "GET")
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "amountAsset", value = "Amount Asset ID or Symbol in Pair", required = true, dataType = "string", paramType = "path"),
    new ApiImplicitParam(name = "priceAsset", value = "Price Asset ID or Symbol in Pair", required = true, dataType = "string", paramType = "path")
  ))
  def ticker: Route = (path("ticker" / Segment / Segment ) & get) { (a1, a2) =>
    complete(apiController.apiGetTicker(a1, a2))
  }

  @Path("/tickers")
  @ApiOperation(value = "Tickers", notes = "Get ticker for all asset pairs", httpMethod = "GET")
  def tickers: Route = path("tickers") {
    complete(apiController.apiTickers)
  }

  @Path("/trades/{amountAsset}/{priceAsset}/{from}/{to}")
  @ApiOperation(value = "Historical trades",
    notes = "Get trades for a given Asset Pair in a specific time period",
    httpMethod = "GET")
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "amountAsset", value = "Amount Asset ID or Symbol in Pair", required = true, dataType = "string", paramType = "path"),
    new ApiImplicitParam(name = "priceAsset", value = "Price Asset ID or Symbol in Pair", required = true, dataType = "string", paramType = "path"),
    new ApiImplicitParam(name = "from", value = "Timestamp from", required = true, dataType = "long", paramType = "path"),
    new ApiImplicitParam(name = "to", value = "Timestamp to", dataType = "long", paramType = "path")
  ))
  def tradesPeriod: Route = (path("trades" / Segment / Segment / LongNumber / LongNumber ) & get) { (a1, a2, t1, t2) =>
    complete(apiController.apiGetTradesRange(a1, a2, t1 ,t2))
  }

  @Path("/trades/{amountAsset}/{priceAsset}/{limit}")
  @ApiOperation(value = "Last trades",
    notes = "Get the last trades for a given Asset Pair",
    httpMethod = "GET")
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "amountAsset", value = "Amount Asset ID or Symbol in Pair", required = true, dataType = "string", paramType = "path"),
    new ApiImplicitParam(name = "priceAsset", value = "Price Asset ID or Symbol in Pair", required = true, dataType = "string", paramType = "path"),
    new ApiImplicitParam(name = "limit", value = "Number of trades (1-1000)", required = true, dataType = "integer", paramType = "path")
  ))
  def tradesLimit: Route = (path("trades" / Segment / Segment / IntNumber ) & get) { (a1, a2, n) =>
    complete(apiController.apiGetTradesLimit(a1, a2, n))
  }

  @Path("/trades/{amountAsset}/{priceAsset}/{address}/{limit}")
  @ApiOperation(value = "Trades by address",
    notes = "Get last trades for a given Address and Asset Pair",
    httpMethod = "GET")
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "amountAsset", value = "Amount Asset ID or Symbol in Pair", required = true, dataType = "string", paramType = "path"),
    new ApiImplicitParam(name = "priceAsset", value = "Price Asset ID or Symbol in Pair", required = true, dataType = "string", paramType = "path"),
    new ApiImplicitParam(name = "address", value = "Address", required = true, dataType = "string", paramType = "path"),
    new ApiImplicitParam(name = "limit", value = "Number of trades (1-1000)", required = true, dataType = "integer", paramType = "path")
  ))
  def tradesByAddress: Route = (path("trades" / Segment / Segment / Segment / IntNumber ) & get) { (a1, a2, addr, n) =>
    complete(apiController.apiGetTradesByAddress(a1, a2, addr, n))
  }

  @Path("/candles/{amountAsset}/{priceAsset}/{timeframe}/{from}/{to}")
  @ApiOperation(value = "Historical candles",
    notes = "Get historical candles for a given Asset Pair in a specific time period and with a specific timeframe",
    httpMethod = "GET")
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "amountAsset", value = "Amount Asset ID or Symbol in Pair", required = true, dataType = "string", paramType = "path"),
    new ApiImplicitParam(name = "priceAsset", value = "Price Asset ID or Symbol in Pair", required = true, dataType = "string", paramType = "path"),
    new ApiImplicitParam(name = "timeframe", value = "Timeframe in minutes (5, 15, 30, 60, 240, 1440)", required = true, dataType = "integer", paramType = "path"),
    new ApiImplicitParam(name = "from", value = "Timestamp from", required = true, dataType = "long", paramType = "path"),
    new ApiImplicitParam(name = "to", value = "Timestamp to", dataType = "long", paramType = "path")
  ))
  def candlesRange: Route = (path("candles" / Segment / Segment / IntNumber / LongNumber / LongNumber ) & get) { (a1, a2, tf, t1, t2) =>
    complete(apiController.apiGetCandlesRange(a1, a2, tf, t1, t2))
  }

  @Path("/candles/{amountAsset}/{priceAsset}/{timeframe}/{limit}")
  @ApiOperation(value = "Last candles",
    notes = "Get the last candles for a given Asset Pair on a specific timeframe",
    httpMethod = "GET")
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "amountAsset", value = "Amount Asset ID or Symbol in Pair", required = true, dataType = "string", paramType = "path"),
    new ApiImplicitParam(name = "priceAsset", value = "Price Asset ID or Symbol in Pair", required = true, dataType = "string", paramType = "path"),
    new ApiImplicitParam(name = "timeframe", value = "Timeframe in minutes (5, 15, 30, 60, 240, 1440)", required = true, dataType = "integer", paramType = "path"),
    new ApiImplicitParam(name = "limit", value = "Number of candles (1-1000)", dataType = "integer", paramType = "path")
  ))
  def candlesLimit: Route = (path("candles" / Segment / Segment / IntNumber / IntNumber ) & get) { (a1, a2, tf, n) =>
    complete(apiController.apiGetCandlesLimit(a1, a2, tf ,n))
   }

}
