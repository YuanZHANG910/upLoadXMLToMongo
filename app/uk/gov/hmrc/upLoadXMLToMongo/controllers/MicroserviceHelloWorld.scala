package uk.gov.hmrc.upLoadXMLToMongo.controllers

import java.io.{BufferedOutputStream, File, FileOutputStream}
import java.nio.file.{Paths, Files => javaFiles}

import com.mongodb.casbah.Imports._
import org.json4s
import org.json4s.Xml.{toJson, toXml}
import org.json4s.jackson.JsonMethods._
import play.api.Logger
import play.api.libs.Files
import play.api.mvc._
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.Future
import scala.xml.Utility.trim
import scala.xml.{Elem, Node, Text}


case class TestFile(fileName: String, filePath:String, xml: Elem)

object MicroserviceHelloWorld extends MicroserviceHelloWorld

trait MicroserviceHelloWorld extends BaseController {

	// Connect to default - localhost, 27017
	val mongoClient= MongoClient("localhost", 27017)
	//DataBass name
	val db: MongoDB = mongoClient("yuanDB")
	//Collection name
	val coll: MongoCollection = db("test")

	implicit val anyContentBodyParser: BodyParser[AnyContent] = parse.anyContent
	implicit val MultipartFormDataBodyParser: BodyParser[MultipartFormData[Files.TemporaryFile]] = parse.multipartFormData

	def uploadXMLToMongo: Action[MultipartFormData[Files.TemporaryFile]] = Action.async(MultipartFormDataBodyParser) {
		request => Logger.info(s"the request body is: ${request.body.toString}")
		request.body match {
			case formData: MultipartFormData[Files.TemporaryFile] =>
				val file = formData.files.head
				val testFile = saveXMLFileFromUserAndLoadTheXML(file)
				val fileInByteArray = javaFiles.readAllBytes(Paths.get(testFile.filePath))
				val xml = testFile.xml

				printDetail(xml)

				val saveToMongoQuery = MongoDBObject("FileName" -> testFile.fileName, xmlToJson(xml), "FileEntity" -> fileInByteArray)
				coll.insert(saveToMongoQuery)

				val idInMongo = coll.findOne(saveToMongoQuery, MongoDBObject("_id" -> 1)).head.get("_id").toString

				Future.successful(Ok(s"File saved at $idInMongo"))
			case _ => Future.successful(BadRequest("not saved"))

		}
	}

	def downXMLFromMongoById(idInMongo: String) = Action.async(MultipartFormDataBodyParser) { request =>

		val byteDataFromMongo = coll.findOne(MongoDBObject("_id" -> new ObjectId(idInMongo)),
			MongoDBObject("FileEntity" -> 1)).head.getAs[Array[Byte]]("FileEntity").get

		val fileName = coll.findOne(MongoDBObject("_id" -> new ObjectId(idInMongo))).head.get("FileName").toString

		saveXMLFileFromMongoAndLoadTheXML(fileName, byteDataFromMongo)

	}

	def downXMLFromMongoByName(nameInMongo: String) = Action.async(MultipartFormDataBodyParser) { request =>

		val byteDataFromMongo = coll.findOne(MongoDBObject("FileName" -> nameInMongo),
			MongoDBObject("FileEntity" -> 1)).head.getAs[Array[Byte]]("FileEntity").get

		saveXMLFileFromMongoAndLoadTheXML(nameInMongo, byteDataFromMongo)
	}

	def getLogRequest: Action[AnyContent] = Action.async(anyContentBodyParser) { implicit request =>
		Future.successful(Ok("hi"))
	}

	def saveXMLFileFromUserAndLoadTheXML(file: MultipartFormData.FilePart[Files.TemporaryFile]): TestFile = {
		val directoryPath = s"./tmp/"
		val fileDir = new java.io.File(directoryPath)
		if (!fileDir.exists()) fileDir.mkdir()
		val filename = file.filename
		val fileSavingPath = s"${directoryPath}upload-$filename"
		val fileEntity = new java.io.File(fileSavingPath)
		if (fileEntity.exists()) fileEntity.delete()

		val uploadedXMLFile = file.ref
		uploadedXMLFile.moveTo(fileEntity)

		val xml = scala.xml.XML.loadFile(fileEntity)
		TestFile(filename, fileSavingPath, xml)
	}

	def saveXMLFileFromMongoAndLoadTheXML(fileName: String, byteDataFromMongo: Array[Byte]): Future[Result] = {
		val outputFolder = s"./tmp/"

		val pathFile: File = new File(outputFolder)
		if (!pathFile.exists) pathFile.mkdirs

		val bos = new BufferedOutputStream(new FileOutputStream(s"$outputFolder/download-$fileName"))
		Stream.continually(bos.write(byteDataFromMongo))
		bos.close()

		Future.successful(Ok("File downloaded"))
	}

	def printDetail(xml: Elem) = {
		println()
		println("The xml you have upload:")
		println(xml)
		println()
		println("The json is:")
		val json: json4s.JValue = toJson(xml)
		println(pretty(json))
		println()
		println("Back to the XML is:")
		val xml2 = toXml(json)
		println(toXml(json))
		println()

		println("Are those XML files the same:")
		println("The xml you have upload:")
		println(trim(xml.head))
		println("The xml after convert:")
		println(trim(xml2.head))
		println(if(trim(xml.head) == trim(xml2.head)) true
						else false)
	}

	def xmlToJson(e: Node): (String, Any) = {
		e.label -> writeString(e)
	}

	def writeString(e: Node): Any = {
		if (e.child.count(_.isInstanceOf[Text]) == 1)
			e.text
		else
			e.child.collect {
				case e: Elem => MongoDBObject(e.label -> writeString(e))
			}
	}

//	implicit object NodeFormat extends JsonFormat[Node] {
//		def write(node: Node) =
//			if (node.child.count(_.isInstanceOf[Text]) == 1)
//				JsString(node.text)
//			else
//				JsObject(node.child.collect {
//					case e: Elem => e.label -> write(e)
//				}: _*)
//
//		def read(jsValue: JsValue) = null // not implemented
//	}

//	implicit val writer = new Writes[Node] {
//		def writes(e: Node): JsValue = {
//			JsObject(Map(e.label -> write(e)))
//		}
//		def write(e: Node): JsValue = {
//			if (e.child.count(_.isInstanceOf[Text]) == 1)
//				JsString(e.text)
//			else
//				JsObject(e.child.collect {
//					case e: Elem => e.label -> write(e)
//				} )
//		}
//	}

}