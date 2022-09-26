# Scala circe error rendering

A recursive rendering of the errors from circe, this allows more informative complete errors from things like akka http
that have **ErrorAccumulatingCirceSupport** from **akka-http-json**.

Full examples for this are shown in **AkkaHttpCirceErrorRenderingSpec** with the rejection handler returning

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



**CirceErrorRendering.renderErrors** simply takes the failed result of a decode attempt which is a **NonEmptyList[Error]**.

Used in akka http rejection handler example it looks like this

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



