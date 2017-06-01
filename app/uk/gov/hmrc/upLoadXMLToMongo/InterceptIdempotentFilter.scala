package uk.gov.hmrc.upLoadXMLToMongo

import play.api.Logger
import play.api.libs.Files
import play.api.libs.json.JsValue
import play.api.mvc._
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.Future
import scala.xml.NodeSeq

trait InterceptIdempotentFilter extends BaseController {

  implicit val emptyBodyParser: BodyParser[Unit] = parse.empty
  implicit val anyContentBodyParser: BodyParser[AnyContent] = parse.anyContent
  implicit val rawBodyParser: BodyParser[RawBuffer] = parse.raw
  implicit val xmlBodyParser: BodyParser[NodeSeq] = parse.xml
  implicit val textBodyParser: BodyParser[String] = parse.text
  implicit val jsonBodyParser: BodyParser[JsValue] = parse.json
  implicit val urlFormEncodedBodyParser: BodyParser[Map[String, Seq[String]]] = parse.urlFormEncoded
  implicit val tolerantXmlBodyParser: BodyParser[NodeSeq] = parse.tolerantXml
  implicit val tolerantTextBodyParser: BodyParser[String] = parse.tolerantText
  implicit val tolerantJsonBodyParser: BodyParser[JsValue] = parse.tolerantJson
  implicit val tolerantUrlFormEncodedBodyParser: BodyParser[Map[String, Seq[String]]] = parse.tolerantFormUrlEncoded
  implicit val temporaryFileBodyParser: BodyParser[Files.TemporaryFile] = parse.temporaryFile
  implicit val MultipartFormDataBodyParser: BodyParser[MultipartFormData[Files.TemporaryFile]] = parse.multipartFormData

  val idempotent =  List("PUT", "POST", "PATCH", "DELETE")

  def interceptIdempotentAction[T](furtherAction: Request[T] => Future[Result])
                                  (implicit bodyParser: BodyParser[T]): Action[T] =
    Action.async(bodyParser) { implicit request =>

      val startTime = System.currentTimeMillis

      if (idempotent.contains(request.tags.getOrElse("ROUTE_VERB", ""))) {
        Logger.info("this request is an idempotent request")
        addNRepudiationLogger(request.headers)
        Logger.info(s"the request headers are: ${request.headers.toString}")
        Logger.info(s"the request body is: ${request.body.toString}")
      }
      else {
        Logger.info("this request is not an idempotent request")
        addNRepudiationLogger(request.headers)
      }

      val endTime = System.currentTimeMillis
      val requestTime = endTime - startTime

      Logger.info(s"${request.method} ${request.uri} took ${requestTime}ms")

      furtherAction(request)
    }

  def addNRepudiationLogger(headers: Headers): Any = {
    if (headers.get("non-repudiation").contains("true")) Logger.info("this request is requiring non-repudiation")
    else Logger.info("this request is not requiring non-repudiation")
  }

}
