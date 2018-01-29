package com.wavesplatform.datafeed.model

import play.api.libs.json._
import scala.collection.mutable.ListBuffer

case class UnconfirmedETX() {

  private val pool = new ListBuffer[Trade]

  def get(amountAsset: String, priceAsset: String, fromTimeStamp: Long, toTimeStamp: Long): List[Trade] =
    pool.toList.filter(tx => (tx.amountAsset==amountAsset && tx.priceAsset==priceAsset && tx.timestamp >= fromTimeStamp && tx.timestamp <= toTimeStamp)).sortBy(-_.timestamp)

  def getByAddress(amountAsset: String, priceAsset: String, address: String): List[Trade] =
    pool.toList.filter(tx => (tx.amountAsset==amountAsset && tx.priceAsset==priceAsset && (tx.buyer == address || tx.seller == address))).sortBy(-_.timestamp)

  def getAll(amountAsset: String, priceAsset: String): List[Trade] =
    pool.toList.filter(tx => (tx.amountAsset==amountAsset && tx.priceAsset==priceAsset)).sortBy(-_.timestamp)

  def last: List[Trade] =
    pool.toList.sortBy(-_.timestamp).take(1)

  def apply(json: List[JsObject]): Unit = {
    val newUETX = json.filter(tx => (tx \ "type").as[Int] == 7)

    pool.clear()
    newUETX.map(tx => pool += tx.as[Trade])

  }

}
