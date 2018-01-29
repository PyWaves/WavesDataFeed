package com.wavesplatform.datafeed

import com.wavesplatform.datafeed.utils.WavesAddress
import com.wavesplatform.datafeed.model._
import com.wavesplatform.datafeed.utils._
import com.wavesplatform.datafeed.api._
import akka.actor._
import play.api.libs.json._
import scala.collection.mutable.Queue

class Synchronizer(nodeApi: NodeApiWrapper, uetx: UnconfirmedETX, timeseries: TimeSeries, router: ActorRef, matchers: List[String]) extends Actor with Logging {

  val FirstMainnetBlock = 435196
  val FirstTestnetBlock = 171000

  val addresses = scala.collection.mutable.SortedSet[String]()

  val mainnet: Boolean = {
    val req = nodeApi.get("/blocks/last")
    if (req != JsNull && (req \ "generator").as[String].take(2)=="3P") true
    else false
  }

  WavesAddress.chainId = if (mainnet) 'W'.toByte else 'T'.toByte

  private def rotate(nodes: List[String]): List[String] = nodes.drop(1) ++ nodes.take(1)

  private def getHeightAndSig: (Int, String) = {
    val req = nodeApi.get("/blocks/headers/last")
    if (req != JsNull) ((req \ "height").as[Int], (req \ "signature").as[String]) else (-1, "")
  }

  private def pushTXMessages(tx: JsObject, confirmed: Boolean): Unit = {
    val txType = if (confirmed) "tx" else "utx"
    router ! WebSocketRouter.TMessage(tx, txType)
    if ((tx \ "type").as[Int] == 7) {
      router ! WebSocketRouter.TMessage(tx.as[Trade].toJson(confirmed), "trades/"+tx.as[Trade].amountAsset+"/"+tx.as[Trade].priceAsset)
      router ! WebSocketRouter.TMessage(tx, "address/"+tx.as[Trade].buyer + "/" + txType)
      router ! WebSocketRouter.TMessage(tx, "address/"+tx.as[Trade].seller + "/" + txType)
      router ! WebSocketRouter.TMessage(tx, "asset/"+tx.as[Trade].amountAsset + "/" + txType)
      router ! WebSocketRouter.TMessage(tx, "asset/"+tx.as[Trade].priceAsset + "/" + txType)
      if (confirmed) {
        addresses += tx.as[Trade].buyer
        addresses += tx.as[Trade].seller
      }
    } else {
      if (tx.keys.contains("sender")) {
        router ! WebSocketRouter.TMessage(tx, "address/" + (tx \ "sender").as[String] + "/" + txType)
        addresses += (tx \ "sender").as[String]
      }
      if (tx.keys.contains("recipient")) {
        router ! WebSocketRouter.TMessage(tx, "address/" + (tx \ "recipient").as[String] + "/" + txType)
        addresses += (tx \ "recipient").as[String]
      }
      if (tx.keys.contains("assetId")) router ! WebSocketRouter.TMessage (tx, "asset/" + ((tx \ "assetId").validate[String] match {
        case s: JsSuccess[String] => s.get
        case e: JsError => "WAVES"
      }) + "/" + txType)
    }

  }

  private def pushBalances: Unit = {
    addresses.foreach(a => {
      try {
        val reqAddrBal = nodeApi.get("/addresses/balance/" + a)
        val wavesBalance = if (reqAddrBal != JsNull) (reqAddrBal \ "balance").as[Long] else 0
        var balances = Json.obj("WAVES" -> wavesBalance)
        val reqAssetBal = nodeApi.get("/assets/balance/" + a)
        if (reqAssetBal != JsNull) {
          (reqAssetBal \ "balances").as[List[JsObject]].sortBy(a => (a \ "assetId").as[String]).foreach(asset => {
            val assetId = (asset \ "assetId").as[String]
            val assetBalance = (asset \ "balance").as[Long]
            balances ++= Json.obj(assetId -> assetBalance)
          })
        }
        router ! WebSocketRouter.TMessage(balances, "balance/" + a)
      } catch {
        case e: Exception => None
      }
      addresses -= a
    })
  }

  private def getUTX: Unit = {
    val req=nodeApi.get("/transactions/unconfirmed")
    if(req!=JsNull) {
      val utx = req.as[List[JsObject]].filter(tx => (tx \ "type").as[Int] != 7 || ((tx \ "type").as[Int] == 7 && (matchers.isEmpty || matchers.contains((tx \ "senderPublicKey").as[String]))))
      uetx(utx)
      utx.sortBy(tx => (tx \ "timestamp").as[Long]).foreach(tx =>
        if ((tx \ "timestamp").as[Long] > lastUtxTs && (tx \ "timestamp").as[Long] < (System.currentTimeMillis + 600000L) ) {
          pushTXMessages(tx, false)
          lastUtxTs = (tx \ "timestamp").as[Long]
        })
    }
  }

  var prevTrade:List[Trade] = Nil
  var lastUtxTs = 0L
  var lastSyncedHeight = if (timeseries.lastSyncedBlock == 0) (if (mainnet) FirstMainnetBlock else FirstTestnetBlock) else timeseries.lastSyncedBlock
  var lastSyncedSignature = ""
  var lastTxQueue = Queue[String]()
  var firstTxQueueBlock = 0
  var MaxBlocksInTxQueue = 100
  val MaxTxQueueSize = 65536

   private def syncBlocks: Unit = {

    val (height, signature) = getHeightAndSig

    if(height>0) {
      val newHeight = Math.min(lastSyncedHeight + 1, height)
      if (newHeight > lastSyncedHeight || (newHeight == lastSyncedHeight && signature != lastSyncedSignature)) {
        (lastSyncedHeight + (if (newHeight > lastSyncedHeight) 1 else 0) to newHeight)
          .map(h => {
            val block = nodeApi.get("/blocks/at/" + h).as[JsObject]
            lastSyncedSignature = (block \ "signature").as[String]
            lastSyncedHeight = h
            log.info("Parsing block " + (block \ "height").as[Int])
            router ! WebSocketRouter.TMessage(block, "block")
            (block \ "transactions").as[List[JsObject]].sortBy(tx => (tx \ "timestamp").as[Long]).foreach(tx => {
              if (!lastTxQueue.contains((tx \ "id").as[String])) {
                if (firstTxQueueBlock == 0 || (h - firstTxQueueBlock) > MaxBlocksInTxQueue) {
                  firstTxQueueBlock = h
                  lastTxQueue.clear
                }
                if (lastTxQueue.size == MaxTxQueueSize) lastTxQueue.dequeue
                lastTxQueue += (tx \ "id").as[String]
                pushTXMessages(tx, true)
                if ((tx \ "type").as[Int] == 7 && (matchers.isEmpty || matchers.contains((tx \ "senderPublicKey").as[String]))) timeseries.addTradeToPair(tx.as[Trade])
            }})
          })
        pushBalances
        timeseries.setLastSyncedBlock(newHeight)
        lastUtxTs = 0L
      }
    }
    getUTX

  }

  def receive = {

    case "sync" => syncBlocks

  }

}