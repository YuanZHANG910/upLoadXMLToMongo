package uk.gov.hmrc.upLoadXMLToMongo

import com.typesafe.config.Config
import net.ceedubs.ficus.Ficus._
import play.api.ApplicationLoader.Context
import play.api._
import play.api.libs.ws.ahc.AhcWSComponents
import play.api.mvc._
import play.api.routing.Router
import prod.Routes
import uk.gov.hmrc.play.audit.filters.AuditFilter
import uk.gov.hmrc.play.auth.controllers.AuthParamsControllerConfig
import uk.gov.hmrc.play.auth.microservice.filters.AuthorisationFilter
import uk.gov.hmrc.play.config.{AppName, ControllerConfig, ServicesConfig}
import uk.gov.hmrc.play.filters.MicroserviceFilterSupport
import uk.gov.hmrc.play.http.logging.filters.LoggingFilter

import scala.concurrent.Future

object MicroserviceInterceptIdempotentFilter
  extends InterceptIdempotentFilter with MicroserviceFilterSupport {

  import scala.concurrent.ExecutionContext.Implicits.global

  def apply(nextFilter: (RequestHeader) => Future[Result])(requestHeader: RequestHeader): Future[Result] = {

    val startTime = System.currentTimeMillis

    Logger.info("this is Idempotent Filter")
    if (idempotent.contains(requestHeader.method)) {
      Logger.info("this request is an idempotent request")
      isRequiringNRepudiation(requestHeader.headers)
      Logger.info(s"the request headers are: ${requestHeader.headers.toString}")
    }
    else {
      Logger.info("this request is not an idempotent request")
      isRequiringNRepudiation(requestHeader.headers)
    }

    nextFilter(requestHeader).map{
      result =>
      val endTime = System.currentTimeMillis
      val requestTime = endTime - startTime

      println(result.body.dataStream)  // this is for response body
      Logger.info(s"${requestHeader.method} ${requestHeader.uri} " +
        s"took ${requestTime}ms and returned ${result.header.status} " +
        s"the request body is: not got at this point"
      )

      result.withHeaders("*" -> "*")
    }
  }

}

class MicroserviceGlobal extends play.api.ApplicationLoader {
  def load(context: Context): Application = {
    LoggerConfigurator(context.environment.classLoader).foreach {
      _.configure(context.environment)
    }
    new ApplicationModule(context).application
  }
}

class ApplicationModule(context: Context) extends BuiltInComponentsFromContext(context)
  with AhcWSComponents with AppName with ServicesConfig {

  lazy val router: Router = new Routes

  override lazy val httpFilters: Seq[EssentialFilter] = Seq(
    MicroserviceAuditFilter,
    MicroserviceLoggingFilter,
    MicroserviceAuthFilter,
    MicroserviceInterceptIdempotentFilter
  )

  object ControllerConfiguration extends ControllerConfig {
    lazy val controllerConfigs: Config = configuration.underlying.as[Config]("controllers")
  }

  object AuthParamsControllerConfiguration extends AuthParamsControllerConfig {
    lazy val controllerConfigs: Config = ControllerConfiguration.controllerConfigs
  }

  object MicroserviceAuditFilter extends AuditFilter with AppName with MicroserviceFilterSupport {
    override val auditConnector = MicroserviceAuditConnector
    override def controllerNeedsAuditing(controllerName: String): Boolean = ControllerConfiguration.paramsForController(controllerName).needsAuditing
  }

  object MicroserviceLoggingFilter extends LoggingFilter with MicroserviceFilterSupport {
    override def controllerNeedsLogging(controllerName: String): Boolean = ControllerConfiguration.paramsForController(controllerName).needsLogging
  }

  object MicroserviceAuthFilter extends AuthorisationFilter with MicroserviceFilterSupport {
    override lazy val authParamsConfig = AuthParamsControllerConfiguration
    override lazy val authConnector = MicroserviceAuthConnector
    override def controllerNeedsAuth(controllerName: String): Boolean = ControllerConfiguration.paramsForController(controllerName).needsAuth
  }

}
