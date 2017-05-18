package uk.gov.hmrc.upLoadXMLToMongo.controllers

import java.io.InputStream

import akka.actor.ActorSystem
import akka.stream.scaladsl.Sink
import akka.stream.{ActorMaterializer, Materializer}
import akka.util.ByteString
import play.api.libs.iteratee.Iteratee
import play.api.libs.json._
import play.api.libs.streams.{Accumulator, Streams}
import play.api.mvc.MultipartFormData.FilePart
import play.api.mvc._
import play.core.parsers.Multipart.{FileInfo, FilePartHandler}
import uk.gov.hmrc.play.microservice.controller.BaseController
import uk.gov.hmrc.upLoadXMLToMongo.controllers.InMemoryMultipartFileHandler.{FileCachedInMemory, InMemoryMultiPartBodyParser}
import _root_.play.api.libs.json.JsValue
import org.joda.time.DateTime
import uk.gov.hmrc.upLoadXMLToMongo.controllers.fileupload.ByteStream

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

object MicroserviceHelloWorld extends MicroserviceHelloWorld

trait MicroserviceHelloWorld extends BaseController {

	val uploadParser: InMemoryMultiPartBodyParser = InMemoryMultipartFileHandler.parser
	implicit val system = ActorSystem()
	implicit val materializer: Materializer = ActorMaterializer()

	def upload(envelopeId: String, fileId: String)
		: Action[Either[MaxSizeExceeded, MultipartFormData[FileCachedInMemory]]] = {

		Action.async(parse.maxLength(10*1024*1024, uploadParser())) { implicit request =>
			request.body match {
				case Left(_) => Future.successful(EntityTooLarge)
				case Right(formData) => Future.successful(Ok("uploaded"))
			}
		}
	}

}

object InMemoryMultipartFileHandler {

	type InMemoryMultiPartBodyParser = () => BodyParser[MultipartFormData[FileCachedInMemory]]

	case class FileCachedInMemory(data: ByteString) {
		def size: Int = data.size
		def inputStream: InputStream = data.iterator.asInputStream
	}

	def cacheFileInMemory(implicit ec: ExecutionContext): FilePartHandler[FileCachedInMemory] = {
		case FileInfo(partName, filename, contentType) =>
			Accumulator(Sink.fold[ByteString, ByteString](ByteString.empty)(_ ++ _)).map { fullFile =>
				FilePart(partName, filename, contentType, FileCachedInMemory(fullFile))
			}
	}

	def parser(implicit ec: ExecutionContext): InMemoryMultiPartBodyParser = {
		() => BodyParsers.parse.multipartFormData(cacheFileInMemory)
	}

}

object JsonUtils {
	def oFormat[A](format: Format[A]): OFormat[A] = new OFormat[A] {
		def reads(json: JsValue): JsResult[A] = format.reads(json)
		def writes(o: A): JsObject = format.writes(o).as[JsObject]
	}

	def optional[A : Writes](k: String, value: Option[A]): JsObject =
		value.map(v => Json.obj(k -> v)).getOrElse(Json.obj())

	def jsonBodyParser[A : Reads](implicit ec: ExecutionContext): BodyParser[A] = new BodyParser[A] {
		def apply(v1: RequestHeader): Accumulator[ByteString, Either[Result, A]] = {
			StreamUtils.iterateeToAccumulator(Iteratee.consume[Array[Byte]]()).map { data =>
				Try(Json.fromJson[A](Json.parse(data)).get) match {
					case Success(report) => Right(report)
					case Failure(NonFatal(e)) => Left(throw e)
				}
			}
		}
	}
}

object errorAsJson {
	def apply(msg: String) = JsObject(Seq("error" -> Json.obj("msg" -> msg)))
}

object StreamUtils {

	def iterateeToAccumulator[T](iteratee: Iteratee[ByteStream, T])(implicit ec: ExecutionContext): Accumulator[ByteString, T] = {
		val sink = Streams.iterateeToAccumulator(iteratee).toSink
		Accumulator(sink.contramap[ByteString](_.toArray[Byte]))
	}

}

object fileupload {
	type ByteStream = Array[Byte]
}

case class CreateEnvelopeRequest(callbackUrl: Option[String] = None,
																 expiryDate: Option[DateTime] = None,
																 metadata: Option[JsObject] = None)

object CreateEnvelopeRequest {
	implicit val dateReads = Reads.jodaDateReads("yyyy-MM-dd'T'HH:mm:ss'Z'")
	implicit val formats = Json.format[CreateEnvelopeRequest]
}