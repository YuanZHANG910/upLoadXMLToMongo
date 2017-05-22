package uk.gov.hmrc.upLoadXMLToMongo.controllers

import java.io.{BufferedOutputStream, File, FileOutputStream}
import java.nio.file.{Paths, Files => javaFiles}

import com.mongodb.casbah.Imports._
import play.api.libs.Files
import play.api.libs.json._
import play.api.mvc._
import uk.gov.hmrc.play.microservice.controller.BaseController

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

	def uploadXMLToMongoAsFile: Action[MultipartFormData[Files.TemporaryFile]] = Action(parse.multipartFormData) { request =>
		request.body match {
			case formData: MultipartFormData[Files.TemporaryFile] =>
				formData.files.foreach { file =>

					val testFile = saveXMLFileFromUserAndLoadTheXML(file)

					val fileInByteArray = javaFiles.readAllBytes(Paths.get(testFile.filePath))

					val xml = testFile.xml

					printDetail(xml)

					val saveToMongoQuery = MongoDBObject("file" -> fileInByteArray)

					coll.insert(saveToMongoQuery)

					val getByteArrayFromMongoQuery = MongoDBObject("file" -> fileInByteArray)

					val byteDataFromMongo = getByteData(coll.findOne(getByteArrayFromMongoQuery).get)

					val outputFolder = s"./tmp/"

					val pathFile:File = new File(outputFolder)
					if (!pathFile.exists) pathFile.mkdirs

					val bos = new BufferedOutputStream(new FileOutputStream(s"$outputFolder/down-${testFile.fileName}"))
					Stream.continually(bos.write(byteDataFromMongo))
					bos.close()

				}
				Ok("File saved")
			case _ => BadRequest("not saved")

		}
	}

	def uploadXMLToMongoAsBson: Action[MultipartFormData[Files.TemporaryFile]] = Action(parse.multipartFormData) { request =>
		request.body match {
			case formData: MultipartFormData[Files.TemporaryFile] =>
				formData.files.foreach { file =>

					val xml = saveXMLFileFromUserAndLoadTheXML(file).xml

					printDetail(xml)

					toMongoDB(xml)

				}
				Ok("File saved")
			case _ => BadRequest("not saved")

		}
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

	def getByteData(mongoResult: MongoDBObject): Array[Byte] = {
		mongoResult.getAs[Array[Byte]]("file").get
	}

	def toMongoDB(xml: Elem) = {

		val dataToMongo = MongoDBObject(writesString(xml))

		coll.insert(dataToMongo)

		coll.find().foreach(println)
	}

	def printDetail(xml: Elem) = {
		println("The xml you have upload:")
		println(xml)
		println("The json is:")
		println(Json.toJson(xml))
		println()
	}

	def writesString(e: Node): (String, Any) = {
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

	implicit val writer = new Writes[Node] {
		def writes(e: Node): JsValue = {
			JsObject(Map(e.label -> write(e)))
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

}