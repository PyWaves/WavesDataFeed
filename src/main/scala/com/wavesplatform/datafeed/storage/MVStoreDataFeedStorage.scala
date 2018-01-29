package com.wavesplatform.datafeed.storage

import java.io.File

import com.wavesplatform.datafeed.model._
import org.h2.mvstore.{MVMap, MVStore}
import scala.collection.mutable.ArrayBuffer
import collection.JavaConverters._
class MVStoreDataFeedStorage(db: MVStore) extends DataFeedStoreI {


  // ============= Sync State
  val syncState = "syncstate"
  private lazy val TSState: MVMap[String, Int] = db.openMap(syncState)

  def setLastBlock(height: Int): Unit = TSState.put("lastblock", height)

  def getLastBlock: Int = TSState.get("lastblock")


  // ============= Time Series (Trades)
  private def tradesMap(amountAsset: String, priceAsset: String): MVMap[(Long, String), (Byte, Long, Long, String, String, String)] = db.openMap("trades-" + amountAsset +"-" + priceAsset)

  def putTrade(trade: Trade): Unit = {
    tradesMap(trade.amountAsset, trade.priceAsset).put((trade.timestamp, trade.id), (trade.orderType, trade.price, trade.amount, trade.buyer, trade.seller, trade.matcher))
    putAddress(trade)
  }

  def toTrade(amountAsset: String, priceAsset: String, index: Long): Trade = {
    val key = tradesMap(amountAsset, priceAsset).getKey(index)
    val value = tradesMap(amountAsset, priceAsset).get(key)
    Trade(amountAsset, priceAsset, key._1, key._2, value._1, value._2, value._3, value._4, value._5, value._6)
  }

  def getTrades(amountAsset: String, priceAsset: String, fromTimeStamp: Long, toTimeStamp: Long, MaxTrades: Int): List[Trade] = {
    val fromIndex = Math.abs(tradesMap(amountAsset, priceAsset).getKeyIndex((fromTimeStamp,""))) - 1
    val toIndex =  Math.abs(tradesMap(amountAsset, priceAsset).getKeyIndex((toTimeStamp + 1,""))) - 2
    (Math.max(fromIndex, 0) to Math.min(tradesMap(amountAsset, priceAsset).size - 1, Math.min(fromIndex + MaxTrades - 1, toIndex)))
      .map(index => toTrade(amountAsset, priceAsset, index)).toList.sortBy(-_.timestamp)
  }

  def getLastNTrades(amountAsset: String, priceAsset: String, n:Int) : List[Trade] =
      ((tradesMap(amountAsset, priceAsset).size -1) to Math.max((tradesMap(amountAsset, priceAsset).size - n), 0) by -1)
        .map(i => toTrade(amountAsset, priceAsset, i)).toList

  def getTradeKey(amountAsset: String, priceAsset: String, index: Long): (Long, String) =
    tradesMap(amountAsset, priceAsset).getKey(index)

  def getTrade(amountAsset: String, priceAsset: String, timestamp: Long, id: String): Trade = {
    val value = tradesMap(amountAsset, priceAsset).get((timestamp, id))
    Trade(amountAsset, priceAsset, timestamp, id, value._1, value._2, value._3, value._4, value._5, value._6)
  }

  def getTradesMapSize(amountAsset: String, priceAsset: String): Int =
    tradesMap(amountAsset, priceAsset).size


  // ============= Time Series (Addresses)
  private def addressesMap(amountAsset: String, priceAsset: String): MVMap[(String, Long), (Long, String)] = db.openMap("addressesmap-" + amountAsset +"-" + priceAsset)

  def putAddress(trade: Trade): Unit = {
    val lastBuyer = addressesMap(trade.amountAsset, trade.priceAsset).lowerKey((trade.buyer, Long.MaxValue))
    val n1 = if (lastBuyer == null || lastBuyer._1 != trade.buyer) 0L else lastBuyer._2 + 1L
    val lastSeller = addressesMap(trade.amountAsset, trade.priceAsset).lowerKey((trade.seller, Long.MaxValue))
    val n2 = if (lastSeller == null || lastSeller._1 != trade.seller) 0L else lastSeller._2 + 1L
    addressesMap(trade.amountAsset, trade.priceAsset).put((trade.buyer, n1), (trade.timestamp, trade.id))
    addressesMap(trade.amountAsset, trade.priceAsset).put((trade.seller, n2), (trade.timestamp, trade.id))
  }

  def getLastNTradesByAddress(amountAsset: String, priceAsset: String, address: String, n:Int) : List[Trade] = {
    val last = addressesMap(amountAsset, priceAsset).lowerKey((address, Long.MaxValue))
    if (last == null || last._1 != address) Nil else
    (last._2 to Math.max(0, last._2 - n) by -1)
      .map(index => {
        val key = addressesMap(amountAsset, priceAsset).get((address, index))
        val trade = tradesMap(amountAsset, priceAsset).get(key)
        Trade(amountAsset, priceAsset, key._1, key._2, trade._1, trade._2, trade._3, trade._4, trade._5, trade._6)
      }
      ).toList.sortBy(-_.timestamp)
  }

  // ============= Time Series (Candles)
  private def candlesMap(amountAsset: String, priceAsset: String): MVMap[Long, ArrayBuffer[Long]] = db.openMap("candles-" + amountAsset +"-" + priceAsset)

  def putCandles(amountAsset: String, priceAsset: String, day: Long, data: ArrayBuffer[Long]): Unit =
    candlesMap(amountAsset, priceAsset).put(day, data)

  def getCandlesKey(amountAsset: String, priceAsset: String, index: Long): Long =
    candlesMap(amountAsset, priceAsset).getKey(index)

  def getCandles(amountAsset: String, priceAsset: String, day: Long): ArrayBuffer[Long] =
    candlesMap(amountAsset, priceAsset).get(day)

  def getCandlesMapSize(amountAsset: String, priceAsset: String): Int =
    candlesMap(amountAsset, priceAsset).size


  // ===================================

  def getPairMaps: List[(String, String)] =
    db.getMapNames.asScala.toList
      .filter(_.take(6)=="trades")
      .map(p => (p.split('-')(1), p.split('-')(2)))

}

object MVStoreDataFeedStorage {

  private def stringToOption(s: String) = Option(s).filter(_.trim.nonEmpty)

  private def createMVStore(fileName: String): MVStore = {
    stringToOption(fileName) match {
      case Some(s) =>
        val file = new File(s)
        file.getParentFile.mkdirs().ensuring(file.getParentFile.exists())

        new MVStore.Builder().fileName(s).compress().open()
      case None =>
        new MVStore.Builder().open()
    }
  }

  def apply(filename: String): MVStoreDataFeedStorage = {
    val db: MVStore = createMVStore(filename)
    if (db.getStoreVersion > 0) db.rollback()
    new MVStoreDataFeedStorage(db)
  }

}

