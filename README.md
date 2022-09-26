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



**CirceErrorRendering.renderErrors** ([code](https://github.com/pbyrne84/scala-circe-error-rendering/blob/main/src/main/scala/com/github/pbyrne84/circe/rendering/CirceErrorRendering.scala)) simply takes the failed result of a decode attempt which is a **NonEmptyList[Error]**.

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



