package com.github.pbyrne84.circe.rendering

import akka.http.javadsl.server.RequestEntityExpectedRejection
import akka.http.scaladsl.model.{ContentTypes, HttpRequest, StatusCodes}
import akka.http.scaladsl.model.StatusCodes.{MethodNotAllowed, NotFound}
import akka.http.scaladsl.server.{MalformedRequestContentRejection, MethodRejection, RejectionHandler}
import akka.http.scaladsl.testkit.ScalatestRouteTest
import com.github.pbyrne84.circe.rendering.AkkaHttpCirceErrorRenderingSpec.{ErrorResponse, TestClassA, TestClassB}
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder, Json}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

object AkkaHttpCirceErrorRenderingSpec {

  case class TestClassA(afield1: TestClassB, afield2: TestClassB)

  implicit val testClassADecoder: Decoder[TestClassA] = deriveDecoder[TestClassA]
  implicit val testClassAEncoder: Encoder[TestClassA] = deriveEncoder[TestClassA]

  case class TestClassB(bfield1: String)

  implicit val testClassBDecoder: Decoder[TestClassB] = deriveDecoder[TestClassB]
  implicit val testClassBEncoder: Encoder[TestClassB] = deriveEncoder[TestClassB]

  case class ErrorResponse(message: String, errors: Json)
  implicit val errorResponseDecoder: Decoder[ErrorResponse] = deriveDecoder[ErrorResponse]
  implicit val errorResponseEncoder: Encoder[ErrorResponse] = deriveEncoder[ErrorResponse]
}

class AkkaHttpCirceErrorRenderingSpec
    extends AnyWordSpec
    with Matchers
    with ScalatestRouteTest
    with ErrorAccumulatingCirceSupport
    with ScalaFutures {

  import akka.http.scaladsl.server.Directives._

  private def rejectionHandler =
    RejectionHandler
      .newBuilder()
      .handle {
        case _: RequestEntityExpectedRejection =>
          complete(StatusCodes.BadRequest, "No request body was received")
      }
      .handle {
        case malformedRequestContentRejection: MalformedRequestContentRejection =>
          malformedRequestContentRejection.cause match {
            case decodingFailuresException: ErrorAccumulatingCirceSupport.DecodingFailures =>
              val renderedErrorJson: Json = CirceErrorRendering.renderErrors(decodingFailuresException.failures)

              complete(StatusCodes.BadRequest,
                       ErrorResponse("The payload was invalid with the following errors", renderedErrorJson))

            case _ =>
              //Something more sensible should be done here
              complete(
                StatusCodes.BadRequest,
                s"The payload was invalid with the following errors ${malformedRequestContentRejection.toString}"
              )
          }
      }
      .handle {
        case a =>
          complete(StatusCodes.BadRequest, a.toString)
      }
      .handleAll[MethodRejection] { methodRejections =>
        val names = methodRejections.map(_.supported.name)
        complete(MethodNotAllowed, s"Can't do that! Supported: ${names mkString " or "}!")
      }
      .handleNotFound {
        complete((NotFound, "Not here!"))
      }
      .result()

  private val decodingRoute = handleRejections(rejectionHandler)(post {
    pathSingleSlash {
      entity(as[TestClassA]) { entity =>
        complete {
          entity
        }
      }
    }
  })

  "The service" should {
    implicit class ErrorResponseOps(errorResponse: ErrorResponse) {
      def asJson: Json = AkkaHttpCirceErrorRenderingSpec.errorResponseEncoder(errorResponse)
    }

    def parseToJson(string: String) = {
      io.circe.parser.parse(string.trim) match {
        case Left(error) => fail(s"failed parsing $string with $error")
        case Right(value) => value
      }

    }

    "return a bad request error when no payload was received" in {
      // tests:
      Post() ~> decodingRoute ~> check {
        response.status shouldBe StatusCodes.BadRequest
        responseAs[String] shouldEqual "No request body was received"
      }
    }

    "return a bad request error when the json payload is an empty object" in {
      // tests:
      postJsonString("{}") ~> decodingRoute ~> check {
        response.status shouldBe StatusCodes.BadRequest

        // comparing json to json is indentation safe and also easier to visualise format
        responseAs[ErrorResponse].asJson shouldEqual parseToJson(
          """
            |{
            |  "message" : "The payload was invalid with the following errors",
            |  "errors" : {
            |    "afield1" : "Missing required field",
            |    "afield2" : "Missing required field"
            |  }
            |} 
            |""".stripMargin
        )
      }
    }

    def postJsonString(payload: String): HttpRequest =
      Post("/").withEntity(ContentTypes.`application/json`, payload)

    "return a bad request error when the json payload has a missing field in the first level and second" in {

      val payloadJson = parseToJson("""
          |{
          |  "afield1" : {}
          |}
          |""".stripMargin)

      postJsonString(payloadJson.spaces2) ~> decodingRoute ~> check {
        response.status shouldBe StatusCodes.BadRequest

        responseAs[ErrorResponse].asJson shouldEqual parseToJson(
          """
            |{
            |  "message" : "The payload was invalid with the following errors",
            |  "errors" : {
            |    "afield1" : {
            |      "bfield1" : "Missing required field"
            |    },
            |    "afield2" : "Missing required field"
            |  }
            |}
            |""".stripMargin
        )
      }
    }

  }

}
