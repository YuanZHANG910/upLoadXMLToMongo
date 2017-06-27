package uk.gov.hmrc.upLoadXMLToMongo.controllers

import play.api.http.Status
import play.api.test.FakeRequest
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}


class MicroserviceHelloWorldControllerSpec extends UnitSpec with WithFakeApplication{

  val fakeRequest = FakeRequest("GET", "/")

  "GET /" should {
    "return 200" in {
      val result = MicroserviceHelloWorld.getLogRequest()(fakeRequest)
      status(result) shouldBe Status.OK
    }
  }

  "checkMongoQuery" should {
    "return OK" in {
      val readQueries =
        List(
          "db.demodb.find()",
          "db.Student.findOne()",
          "db.inventory.distinct()",
          "db.yourColl.count()",
          "db.userInfo.getDB()",
          "db.collection.aggregate()"
        )
      readQueries.foreach( q => {
        val result = MicroserviceHelloWorld.checkMongoQuery(q)(fakeRequest)
        status(result) shouldBe Status.OK
      } )
    }
    "return BadRequest" in {
      val nonFindQuery =
        List(
          "db.FirstCollection.insert()",
          "db.Student.insert()",
          "db.Student.remove()",
          "db.Student.update()",
          "db.dropDatabase()",
          ""
        )
      nonFindQuery.foreach( q => {
        val result = MicroserviceHelloWorld.checkMongoQuery(q)(fakeRequest)
        status(result) shouldBe Status.BAD_REQUEST
      } )
    }
  }

}
