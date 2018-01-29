package com.wavesplatform.datafeed.api

import scala.reflect.runtime.universe.Type
import akka.actor.ActorSystem
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.HttpMethods._
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{Directive0, RejectionHandler, ValidationRejection}
import akka.stream.ActorMaterializer
import com.wavesplatform.datafeed.settings.WDFSettings

case class CompositeHttpService(system: ActorSystem, apiTypes: Seq[Type], routes: Seq[RestApiRoute], settings: WDFSettings) {

  val swaggerService = new SwaggerDocService(system, ActorMaterializer()(system), apiTypes, settings)

  def withCors: Directive0 = respondWithHeader(`Access-Control-Allow-Origin`.*)


  val compositeRoute =
    withCors(routes.map(_.route).reduce(_ ~ _)) ~
    swaggerService.routes ~
    (pathEndOrSingleSlash | path("swagger")) {
      redirect("/api-docs/index.html", StatusCodes.PermanentRedirect)
    } ~
    pathPrefix("api-docs") {
      pathEndOrSingleSlash {
        redirect("/api-docs/index.html", StatusCodes.PermanentRedirect)
      } ~
        getFromResourceDirectory("swagger-ui")
    } ~ options {
    respondWithDefaultHeaders(
      `Access-Control-Allow-Credentials`(true),
      `Access-Control-Allow-Headers`("Authorization", "Content-Type", "X-Requested-With"),
      `Access-Control-Allow-Methods`(OPTIONS, POST, PUT, GET, DELETE))(withCors(complete(StatusCodes.OK)))
     } ~ complete(StatusCodes.NotFound)

}
