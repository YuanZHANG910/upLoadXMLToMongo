package uk.gov.hmrc.upLoadXMLToMongo

import com.mongodb.casbah.Imports.{MongoClient, MongoCollection, MongoDB, MongoDBObject, ObjectId}
import com.mongodb.casbah.commons.Imports

/**
  * http://mongodb.github.io/casbah/3.1/reference/querying/
  * http://mongodb.github.io/casbah/3.1/getting-started/
  */
object QueryingTest {

  // Connect to default mongo - localhost, 27017
  val mongoClient= MongoClient("localhost", 27017)
  //DataBass name "yuanDB"
  val db: MongoDB = mongoClient("yuanDB")
  //Collection name "test", create if not exist
  val coll: MongoCollection = db("test")

  def main(args: Array[String]): Unit = {

    val insert = doNewInsert()

    println("Press Enter to continue: find a date")
    nextQuery(doFindById(insert.get("_id").toString))

    println("Press Enter to continue: show all in the collection")
    nextQuery(showAllInCollection())

    println("Press Enter to continue: insert a new date")
    nextQuery(insertMore())

    println("Press Enter to continue: update a date")
    nextQuery(update(insert.get("_id").toString))

    println("Press Enter to continue: show all in the collection again")
    nextQuery(showAllInCollection())

    println("Press Enter to continue: remove a date from the collection")
    nextQuery(remove(insert.get("_id").toString))

    println("Press Enter to continue: show all in the collection again")
    nextQuery(showAllInCollection())

    println("Press Enter to continue: drop the collection")
    nextQuery(dropCollection())
  }

  def doNewInsert(): Imports.DBObject = doInsert()

  def insertMore(): Imports.DBObject = doInsert()

  def doInsert(): Imports.DBObject = {
    println("inserting a date")
    val newObj = MongoDBObject("foo" -> "bar", "x" -> "y", "pie" -> 3.14, "spam" -> "eggs")
    //println(newObj.toString)
    coll += newObj //the same as coll.insert(newObj), '_id' will be add to the newObj
    //println(newObj.toString)
    println(s"$newObj has inserted to ${db.getName}.$coll collection")
    newObj
  }

  def update(id: String): Unit = {
    val idObject = MongoDBObject("_id" -> new ObjectId(id))
    val update = MongoDBObject("new foo" -> "new bar")
    //Setting upsert=True will insert the document if does not exist, otherwise update it.
    coll.update(idObject, update, upsert=true)
  }

  def remove(id: String): Unit = {
    val idObject = MongoDBObject("_id" -> new ObjectId(id))
    coll.findAndRemove(idObject)
  }

  def doFindById(id: String): Unit = {
    val idObject = MongoDBObject("_id" -> new ObjectId(id))
    println(s"find by id: $id ${coll.findOne(idObject)}")
  }

  def showAllInCollection(): Unit = for { x <- coll} println(x)

  def dropCollection(): Unit = coll.drop()

  def nextQuery(queryMethod: => Unit): Unit = userInput() match {case _ => queryMethod}

  def userInput(): String = scala.io.StdIn.readLine()

}
