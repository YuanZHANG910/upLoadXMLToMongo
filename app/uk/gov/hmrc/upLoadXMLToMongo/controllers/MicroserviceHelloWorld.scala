package uk.gov.hmrc.upLoadXMLToMongo.controllers

import play.api.libs.Files
import play.api.libs.json._
import play.api.mvc._
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.xml.{Elem, Node, Text}

object MicroserviceHelloWorld extends MicroserviceHelloWorld

trait MicroserviceHelloWorld extends BaseController {

	def uploadToMongo: Action[MultipartFormData[Files.TemporaryFile]] = Action(parse.multipartFormData) { request =>
		request.body match {
			case formData: MultipartFormData[Files.TemporaryFile] =>
				formData.files.foreach { file =>
					val directoryPath = s"./tmp/"
					val fileDir = new java.io.File(directoryPath)
					if (!fileDir.exists()) fileDir.mkdir()
					val filename = file.filename
					val fileSavingPath = s"$directoryPath$filename"
					val fileEntity = new java.io.File(fileSavingPath)
					if (fileEntity.exists()) fileEntity.delete()

					val uploadedXMLFile = file.ref
					uploadedXMLFile.moveTo(fileEntity)

					val xml = scala.xml.XML.loadFile(fileEntity)
					println("The xml is:")
					println(xml)
					println("The json is:")
					println(Json.toJson(xml))
					println()

				}
				Ok("File saved")
			case _ => BadRequest("not saved")

		}
	}
	implicit val writer = new Writes[Node] {
		def writes(e: Node): JsValue = {
			JsObject(Map(e.label -> write(e)))
		}
	}

	def write(e: Node): JsValue = {
		if (e.child.count(_.isInstanceOf[Text]) == 1)
			JsString(e.text)
		else
			JsObject(e.child.collect {
				case e: Elem => e.label -> write(e)
			} )
	}

}