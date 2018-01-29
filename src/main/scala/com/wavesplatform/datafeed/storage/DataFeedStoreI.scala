package com.wavesplatform.datafeed.storage

import com.wavesplatform.datafeed.model._
import scala.collection.mutable.ArrayBuffer

trait DataFeedStoreI {

  def setLastBlock(height: Int): Unit

  def getLastBlock: Int


  def putTrade(trade: Trade): Unit

  def toTrade(amountAsset: String, priceAsset: String, index: Long): Trade

  def getTrades(amountAsset: String, priceAsset: String, fromTimeStamp: Long, toTimeStamp: Long, MaxTrades: Int): List[Trade]

  def getLastNTrades(amountAsset: String, priceAsset: String, n:Int) : List[Trade]

  def getTradeKey(amountAsset: String, priceAsset: String, index: Long): (Long, String)

  def getTrade(amountAsset: String, priceAsset: String, timestamp: Long, id: String): Trade

  def getTradesMapSize(amountAsset: String, priceAsset: String): Int


  def putAddress(trade: Trade): Unit


  def putCandles(amountAsset: String, priceAsset: String, day: Long, data: ArrayBuffer[Long]): Unit

  def getCandlesKey(amountAsset: String, priceAsset: String, index: Long): Long

  def getCandles(amountAsset: String, priceAsset: String, day: Long): ArrayBuffer[Long]

  def getCandlesMapSize(amountAsset: String, priceAsset: String): Int

  def getPairMaps: List[(String, String)]

}
