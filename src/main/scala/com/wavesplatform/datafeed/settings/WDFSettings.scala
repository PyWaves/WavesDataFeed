package com.wavesplatform.datafeed.settings

import com.typesafe.config.Config
import net.ceedubs.ficus.Ficus._
import scala.collection.JavaConverters._

object LogLevel extends Enumeration {
  val DEBUG = Value("DEBUG")
  val INFO = Value("INFO")
  val WARN = Value("WARN")
  val ERROR = Value("ERROR")
}

case class WDFSettings(enable: Boolean,
                       nodes: List[String],
                       matchers: List[String],
                       datafeedDirectory: String,
                       restEnable: Boolean,
                       restAddress: String,
                       restPort: Int,
                       websocketEnable: Boolean,
                       websocketAddress: String,
                       websocketPort: Int,
                       symbols: Map[String, String]
                      )

object WDFSettings {

  val configPath: String = "waves.datafeed"

  def fromConfig(config: Config): WDFSettings = {

    val enable = config.as[Boolean](s"$configPath.enable")
    val nodes = config.as[List[String]](s"$configPath.nodes")
    val matchers = config.as[List[String]](s"$configPath.matchers")
    val datafeedDirectory = config.as[String](s"$configPath.datafeed-directory")
    val restEnable = config.as[Boolean](s"$configPath.rest-enable")
    val restAddress = config.as[String](s"$configPath.rest-address")
    val restPort = config.as[Int](s"$configPath.rest-port")
    val websocketEnable = config.as[Boolean](s"$configPath.websocket-enable")
    val websocketAddress = config.as[String](s"$configPath.websocket-address")
    val websocketPort = config.as[Int](s"$configPath.websocket-port")
    val symbols: Map[String, String] = config.getConfigList(s"$configPath.symbols").asScala.map { p: Config => (p.as[String]("symbol"), p.as[String]("asset")) }.toMap

    WDFSettings(enable,
      nodes,
      matchers,
      datafeedDirectory,
      restEnable,
      restAddress,
      restPort,
      websocketEnable,
      websocketAddress,
      websocketPort,
      symbols)
  }
}