package io.iohk.armadillo.json.json4s

import io.iohk.armadillo.Armadillo.{JsonRpcErrorNoData, JsonRpcErrorResponse, JsonRpcRequest, JsonRpcSuccessResponse}
import io.iohk.armadillo.tapir.JsonSupport
import io.iohk.armadillo.tapir.JsonSupport.Json
import org.json4s.*
import org.json4s.JsonAST.JValue
import sttp.tapir.Codec.JsonCodec
import sttp.tapir.SchemaType.SCoproduct
import sttp.tapir.json.json4s.*
import sttp.tapir.{DecodeResult, Schema}

import scala.util.{Failure, Success, Try}

class Json4sSupport(parseAsJValue: String => JValue)(implicit formats: Formats, serialization: Serialization) extends JsonSupport[JValue] {
  // JValue is a coproduct with unknown implementations
  implicit val schemaForJson4s: Schema[JValue] =
    Schema(
      SCoproduct(Nil, None)(_ => None),
      None
    )

  override def getByIndex(arr: JValue, index: Int): DecodeResult[JValue] = {
    arr match {
      case JArray(arr) =>
        arr.lift(index) match {
          case Some(value) => DecodeResult.Value(value)
          case None        => DecodeResult.Missing
        }
      case _ => DecodeResult.Error(arr.toString, new RuntimeException(s"Expected array but got $arr"))
    }
  }

  override def getByField(obj: JValue, field: String): DecodeResult[JValue] = {
    obj match {
      case JObject(fields) =>
        fields.toMap.get(field) match {
          case Some(value) => DecodeResult.Value(value)
          case None        => DecodeResult.Missing
        }
      case _ => DecodeResult.Error(obj.toString, new RuntimeException(s"Expected object but got $obj"))
    }
  }

  override def asArray(seq: Vector[JValue]): JValue = JArray(seq.toList)

  override def asObject(fields: Map[String, JValue]): JValue = JObject(fields.toList)

  override def jsNull: JValue = JNull

  override def outRawCodec: JsonCodec[JValue] = json4sCodec[JValue]

  override def encodeError(e: JsonRpcErrorResponse[JValue]): JValue = {
    Extraction.decompose(e)
  }

  override def encodeErrorNoData(error: JsonRpcErrorNoData): JValue = {
    Extraction.decompose(error)
  }

  override def encodeSuccess(e: JsonRpcSuccessResponse[JValue]): JValue = {
    Extraction.decompose(e)
  }

  override def parse(string: String): DecodeResult[JsonSupport.Json[JValue]] = {
    Try(parseAsJValue(string)) match {
      case Failure(exception) => DecodeResult.Error(string, exception)
      case Success(value) =>
        value match {
          case JObject(obj) => DecodeResult.Value(Json.JsonObject(obj))
          case JArray(arr)  => DecodeResult.Value(Json.JsonArray(arr.toVector))
          case other        => DecodeResult.Value(Json.Other(other))
        }
    }
  }

  override def decodeJsonRpcRequest(raw: JValue): DecodeResult[JsonRpcRequest[JValue]] = {
    Try(raw.extract[JsonRpcRequest[JValue]]) match {
      case Failure(exception) => DecodeResult.Error(raw.toString, exception)
      case Success(value)     => DecodeResult.Value(value)
    }
  }
}
