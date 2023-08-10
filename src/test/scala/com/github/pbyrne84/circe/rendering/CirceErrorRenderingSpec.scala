package com.github.pbyrne84.circe.rendering

import cats.data.{NonEmptyList, Validated}
import io.circe
import io.circe.Decoder.Result
import io.circe.DecodingFailure.Reason.CustomReason
import io.circe.parser.parse
import io.circe.{parser, Decoder, DecodingFailure, HCursor, Json}
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
      case class X(xField1: A, xField2: B, xField3: Boolean, xField4: List[String], xField5: D, age: TaggedAge)
      implicit val dDecoder: Decoder[D] = Decoder.decodeString.emap { value =>
        Left("custom error message")
      }

      trait SafetyTag
      type TaggedAge = Int with SafetyTag

      class TaggedDecoder[From, To](attempt: From => Either[String, To])(implicit aDecoder: Decoder[From])
          extends Decoder[To] {
        override def apply(c: HCursor): Result[To] = {
          c.as[From]
            .flatMap(
              value => attempt(value).left.map((error: String) => DecodingFailure(CustomReason(error), c))
            )
        }
      }

      object TaggedAge {

        implicit val taggedAgeDecoder: Decoder[TaggedAge] =
          new TaggedDecoder((possibleAge: Int) => attemptAge(possibleAge))

        // You can generify all this code when doing many like we have created a generified decoder
        def attemptAge(age: Int): Either[String, TaggedAge] = {
          if (age < 0) {
            Left(s"Age '$age' cannot be below zero")
          } else {
            Right(age.asInstanceOf[TaggedAge])
          }
        }
      }

      import TaggedAge.taggedAgeDecoder

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
         |   "xField5" : "d",
         |   "age" : -1
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
          |  "xField5" : "custom error message",
          |  "age" : "Age '-1' cannot be below zero"
          |}
        """.stripMargin
      )

    }

  }
}
