package uk.gov.hmrc.upLoadXMLToMongo.controllers

import java.nio.file.{Files => javaFiles}

import akka.actor.ActorSystem
import akka.stream.scaladsl.FileIO
import akka.stream.{ActorMaterializer, Materializer}
import play.api.http.HttpEntity
import play.api.libs.Files
import play.api.libs.json._
import play.api.mvc._
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.xml.{Elem, Node, Text}
import scala.xml.XML
import net.liftweb.json.Xml


object MicroserviceHelloWorld extends MicroserviceHelloWorld

trait MicroserviceHelloWorld extends BaseController {

	implicit val system = ActorSystem()
	implicit val materializer: Materializer = ActorMaterializer()

	def upload = Action(parse.multipartFormData) { request =>
		request.body match {
			case formData: MultipartFormData[Files.TemporaryFile] => {
				formData.files.foreach { file =>
					import java.io.File
					val filename = file.filename
					val filePath = new File(s"./tmp/$filename")
					val contentType = file.contentType
					println(contentType)
					val uploadedXMLFile = file.ref
					uploadedXMLFile.moveTo(filePath)

					val xml = scala.xml.XML.loadFile(uploadedXMLFile.moveTo(filePath))
					//xml.foreach(node => {
					//	println(write(node))
					//	println(node.text)
					//})
					//val temp = (xml \\ "MessageSpec").text
					//println(temp)
					//val loadXMLfile = filePath
				}
				Ok("File uploaded")
			}
			case _ => BadRequest("Missing parameter [name]")

		}
	}

	def uploadToMongo = Action(parse.multipartFormData) { request =>
		request.body match {
			case formData: MultipartFormData[Files.TemporaryFile] => {
				formData.files.foreach { file =>
					val filename = file.filename
					val fileSavingPath = s"./tmp/$filename"
					val fileEntity = new java.io.File(fileSavingPath)
					val filePath = fileEntity.toPath

					val sourceInByteString = FileIO.fromPath(filePath)
					val fileInByteArray = javaFiles.readAllBytes(filePath)

					val xml = scala.xml.XML.loadFile(fileEntity)
//					Xml.toJson(xml)
//					for (x <- xml) {
						println(Json.toJson(xml))
//					}
//					println(Json.toJson(compactXml(xml)))

					println(xml)

					val bodyToMongo = HttpEntity.Streamed(sourceInByteString, Some(fileEntity.length), Some("text/xml"))
					println(sourceInByteString)
					println(bodyToMongo)

				}
				Ok("File saved")
			}
			case _ => BadRequest("not saved")

		}
	}
	implicit val writer = new Writes[Node] {
		def writes(e: Node): JsValue = {
			if (e.child.count(_.isInstanceOf[Text]) == 1)
				JsString(e.text)
			else
				JsObject(e.child.collect {
					case e: Elem => e.label -> write(e)
				})
		}
	}


//	implicit val writer = new Writes[Node] {
//		def writes(e: Node): JsValue = {
//			e match {
//				case Elem(prefix, label, attributes, scope, children) => {
//					for (c <- children) {
//						writes(c)
//					}
//				}.asInstanceOf[JsValue]
//				case Text(data) => Json.obj(e.label.trim -> Text(data.trim).text)
//			}
//		}
//	}

	def write(node: Node) =
		JsString(node.text)
}