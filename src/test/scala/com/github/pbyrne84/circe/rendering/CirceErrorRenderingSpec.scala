package com.github.pbyrne84.circe.rendering

import cats.data.{NonEmptyList, Validated}
import io.circe
import io.circe.parser.parse
import io.circe.{parser, Decoder, Json}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class CirceErrorRenderingSpec extends AnyWordSpec with Matchers {
  private def parseJson(json: String): Json = {
    parse(json) match {
      case Left(error) => fail(error.message + s"\n$json")
      case Right(parsedJson) => parsedJson
    }
  }

  private val missingFieldErrorText = "Missing required field"

  "CirceErrorRendering" should {
    import io.circe.generic.semiauto._

    "render single missing field" in {

      case class X(field1: String)
      implicit val cDecoder: Decoder[X] = deriveDecoder[X]

      val failures = accumulateFailures("""{}""")

      CirceErrorRendering.renderErrors(failures) shouldBe parseJson(
        s"""
        |{
        |  "field1" : "$missingFieldErrorText"
        |}
        """.stripMargin
      )
    }

    def accumulateFailures[TestClass](json: String)(implicit decoder: Decoder[TestClass]): NonEmptyList[circe.Error] =
      parser.decodeAccumulating[TestClass](json) match {
        case Validated.Valid(a) =>
          fail("should not be value")
        case Validated.Invalid(e) =>
          e
      }

    "render multiple missing field" in {
      case class X(field1: String, field2: String)
      implicit val cDecoder: Decoder[X] = deriveDecoder[X]

      val failures = accumulateFailures("""{}""")
      CirceErrorRendering.renderErrors(failures) shouldBe parseJson(
        s"""
        |{
        |  "field1" : "$missingFieldErrorText",
        |  "field2" : "$missingFieldErrorText"
        |}
        """.stripMargin
      )

    }

    "render sub object failure" in {
      case class A(field1: String)
      case class X(field1: A)
      implicit val aDecoder: Decoder[A] = deriveDecoder[A]
      implicit val cDecoder: Decoder[X] = deriveDecoder[X]

      val failures = accumulateFailures[X]("""{ "field1":{} }""")

      CirceErrorRendering.renderErrors(failures) shouldBe parseJson(
        s"""
        |{
        |  "field1" : {
        |    "field1" : "$missingFieldErrorText"
        |  }
        |}
        """.stripMargin
      )

    }

    "render more complicated nesting" in {
      case class D(value: String)
      case class C(cField1: String, cField2: String)
      case class B(bField1: String)
      case class A(aField1: String, aField2: C, aField3: B)
      case class X(xField1: A, xField2: B, xField3: Boolean, xField4: List[String], xField5: D)
      implicit val dDecoder: Decoder[D] = Decoder.decodeString.emap { value =>
        Left("custom error message")
      }
      implicit val cDecoder: Decoder[C] = deriveDecoder[C]
      implicit val bDecoder: Decoder[B] = deriveDecoder[B]
      implicit val aDecoder: Decoder[A] = deriveDecoder[A]
      implicit val xDecoder: Decoder[X] = deriveDecoder[X]

      val failures = accumulateFailures[X](
        """
         |{ 
         |   "xField1":{ 
         |     "aField2": true,
         |     "aField3":{}
         |   },
         |   "xField3" : "",
         |   "xField4" : true,
         |   "xField5" : "d"
         |}""".stripMargin
      )

      CirceErrorRendering.renderErrors(failures) shouldBe parseJson(
        s"""
          |{
          |  "xField1" : {
          |    "aField3" : {
          |      "bField1" : "$missingFieldErrorText"
          |    },
          |    "aField2" : {
          |      "cField2" : "$missingFieldErrorText",
          |      "cField1" : "$missingFieldErrorText"
          |    },
          |    "aField1" : "$missingFieldErrorText"
          |  },
          |  "xField2" : "$missingFieldErrorText",
          |  "xField3" : "Got value '\\"\\"' with wrong type, expecting 'true' or 'false'",
          |  "xField4" : "Got value 'true' with wrong type, expecting array",
          |  "xField5" : "custom error message"
          |}
        """.stripMargin
      )

    }

  }
}
