package uk.gov.hmrc.upLoadXMLToMongo

import play.api.Logger
import play.api.mvc._

import scala.concurrent.Future

trait InterceptIdempotentFilter {

  val idempotent =  List("PUT", "POST", "PATCH", "DELETE")

  def interceptIdempotentAction[T](furtherAction: Request[T] => Future[Result])
                                  (implicit bodyParser: BodyParser[T]): Action[T] =
    Action.async(bodyParser) { implicit request =>

      if (idempotent.contains(request.tags.getOrElse("ROUTE_VERB", ""))) {
        Logger.info("this request is an idempotent request")
        isRequiringNRepudiation(request.headers)
        Logger.info(s"the request headers are: ${request.headers.toString}")
        Logger.info(s"the request body is: ${request.body.toString}")
      }
      else {
        Logger.info("this request is not an idempotent request")
        isRequiringNRepudiation(request.headers)
      }

      furtherAction(request)
    }

  def isRequiringNRepudiation(headers: Headers): Any = {
    if (headers.get("non-repudiation").contains("true")) Logger.info("this request is requiring non-repudiation")
    else Logger.info("this request is not requiring non-repudiation")
  }

}
