package com.wavesplatform.datafeed.model

import com.wavesplatform.datafeed.NodeApiWrapper
import com.wavesplatform.datafeed.settings._
import com.wavesplatform.datafeed.storage._
import play.api.libs.json._

import scala.collection.concurrent.TrieMap
import scala.util.{Failure, Success, Try}

class TimeSeries(settings: WDFSettings, nodeApi: NodeApiWrapper, uetx: UnconfirmedETX) {

  private val pairs = TrieMap[String, AssetPair]()

  val DFDB = MVStoreDataFeedStorage(settings.datafeedDirectory + "/datafeed.dat")

  DFDB.getPairMaps
    .foreach(p => {
      val pair = new AssetPair(settings, nodeApi, p._1, p._2, DFDB, uetx)
      pairs.update(p._1 + "-" + p._2, pair)
      pair.loadPair
    })

  def getPair(amountAsset: String, priceAsset: String): Option[AssetPair] = pairs.get(amountAsset + "-" + priceAsset)

  def addTradeToPair(tx: Trade): Unit =  {
    val pairName = tx.amountAsset + "-" + tx.priceAsset
    pairs.get(pairName) match {
      case Some(pair) => pair.addTrade(tx)
      case None => {
        val pair = new AssetPair(settings, nodeApi, tx.amountAsset, tx.priceAsset, DFDB, uetx)
        pairs.update(pairName, pair)
        pair.addTrade(tx)
      }
    }
  }

  def numberOfMarkets: Int = pairs.size

  def markets: List[JsObject] =
    (pairs.toList.filter(_._2.symbol.nonEmpty).sortBy(_._2.symbol) ++ pairs.toList.filter(_._2.symbol.isEmpty)).map(p => p._2.getMarket)

  def tickers: List[JsObject] =
    (pairs.toList.filter(_._2.symbol.nonEmpty).sortBy(_._2.symbol) ++ pairs.toList.filter(_._2.symbol.isEmpty)).map(p => p._2.getTicker)

  def lastSyncedBlock: Int =  DFDB.getLastBlock

  def setLastSyncedBlock(height: Int) = DFDB.setLastBlock(height)

}

