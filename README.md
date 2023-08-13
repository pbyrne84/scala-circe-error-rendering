# Scala circe error rendering

A recursive rendering of the errors from circe, this allows more informative complete errors from things like akka http
that have **ErrorAccumulatingCirceSupport** from **akka-http-json**.

Full examples for this are shown in [AkkaHttpCirceErrorRenderingSpec](https://github.com/pbyrne84/scala-circe-error-rendering/blob/main/src/test/scala/com/github/pbyrne84/circe/rendering/AkkaHttpCirceErrorRenderingSpec.scala) with the rejection handler returning


```json
{
  "message" : "The payload was invalid with the following errors",
  "errors" : {
    "afield1" : {
      "bfield1" : "Missing required field"
    },
    "afield2" : "Missing required field"
  }
}
```

when sent 

```json
{
  "afield1" : {}
}
```

with an expected payload of something like

```json
{
  "afield1" : {
    "bfield1" : "xxxxx" 
  },
  "afield2" : {
    "bfield1" : "xxxxxx"
  }
}
```



** CirceErrorRendering.renderErrors** ([code](https://github.com/pbyrne84/scala-circe-error-rendering/blob/main/src/main/scala/com/github/pbyrne84/circe/rendering/CirceErrorRendering.scala)) simply takes the failed result of a decode attempt which is a **NonEmptyList[Error]**.

Used in akka http rejection handler example it looks like this

[AkkaHttpCirceErrorRenderingSpec.scala#L43](https://github.com/pbyrne84/scala-circe-error-rendering/blob/c300c414cde00b3ee5bc8778ef38df1fce095a90/src/test/scala/com/github/pbyrne84/circe/rendering/AkkaHttpCirceErrorRenderingSpec.scala#L43)

```scala
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
```

## CirceErrorRenderingSpec

<https://github.com/pbyrne84/scala-circe-error-rendering/blob/main/src/test/scala/com/github/pbyrne84/circe/rendering/CirceErrorRenderingSpec.scala#L98>

Has an example of based on Tagged decoding

```scala 
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
```

This allows us to guarantee data stringency on entrance to the api, so we can reject clearly on entrance
and have a trusted value from that point on in the system.


In this case, the value was not above 0 **Age '-1' cannot be below zero**

```json
{
  "xField1" : {
    "aField3" : {
      "bField1" : "$missingFieldErrorText"
    },
    "aField2" : {
      "cField2" : "$missingFieldErrorText",
      "cField1" : "$missingFieldErrorText"
    },
    "aField1" : "$missingFieldErrorText"
  },
  "xField2" : "$missingFieldErrorText",
  "xField3" : "Got value '\"\"' with wrong type, expecting 'true' or 'false'",
  "xField4" : "Got value 'true' with wrong type, expecting array",
  "xField5" : "custom error message",
  "age" : "Age '-1' cannot be below zero"
}
```