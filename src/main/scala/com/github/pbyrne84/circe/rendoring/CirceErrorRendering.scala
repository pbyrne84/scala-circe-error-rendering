package com.github.pbyrne84.circe.rendoring

import cats.data.NonEmptyList
import io.circe.CursorOp.DownField
import io.circe.{DecodingFailure, Error, Json, JsonObject}

object CirceErrorRendering {
  def renderErrors(errors: NonEmptyList[Error]): Json = {
    val decodingFailures = errors.collect {
      case decodingFailure: DecodingFailure => decodingFailure
    }

    val remappedErrorJson = decodingFailures.foldLeft(JsonObject.empty) {
      case (jsonObject: JsonObject, error) =>
        val fields: List[DownField] = error.history.reverse.collect { case a: DownField => a }

        JsonObject(jsonObject.toList ++ processFields(fields, jsonObject, error.message).toList: _*)

    }

    Json.fromJsonObject(remappedErrorJson)
  }

  private def processFields(fields: List[DownField], currentObject: JsonObject, errorMessage: String): JsonObject = {
    fields match {
      case Nil =>
        JsonObject.empty

      case head :: Nil =>
        currentObject.+:((head.k, Json.fromString(remapError(errorMessage))))

      case head :: tail =>
        val maybeObject = currentObject(head.k).flatMap(_.asObject)
        currentObject.+:(
          (head.k, Json.fromJsonObject(processFields(tail, maybeObject.getOrElse(JsonObject.empty), errorMessage)))
        )
    }
  }

  private def remapError(circeError: String) = {
    if (circeError == "Attempt to decode value on failed cursor") {
      "the field is missing"
    } else if (circeError.contains(" ")) {
      circeError
    } else {
      //C[A] comes from type erasure
      s"""the field is not the correct type, expected '${circeError.replaceAll("C\\[A]", "Array")}'"""
    }
  }.trim

}
