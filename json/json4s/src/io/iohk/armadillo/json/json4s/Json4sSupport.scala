package io.iohk.armadillo.json.json4s

import io.iohk.armadillo._
import io.iohk.armadillo.server.JsonSupport
import io.iohk.armadillo.server.JsonSupport.Json
import org.json4s.JsonAST.JValue
import org.json4s._
import sttp.tapir.DecodeResult

import scala.util.{Failure, Success, Try}

class Json4sSupport private (parseAsJValue: String => JValue, render: JValue => String)(implicit
    formats: Formats,
    serialization: Serialization
) extends JsonSupport[JValue] {

  override def asArray(seq: Vector[JValue]): JValue = JArray(seq.toList)

  override def jsNull: JValue = JNull

  override def encodeErrorNoData(error: JsonRpcError[Unit]): JValue = {
    val map = Map("code" -> error.code, "message" -> error.message)
    Extraction.decompose(map)
  }

  override def encodeErrorWithData(error: JsonRpcError[JValue]): JValue = { // TODO check
    val map = Map("code" -> error.code, "message" -> error.message, "data" -> error.data)
    Extraction.decompose(map)
  }

  override def encodeResponse(response: JsonRpcResponse[JValue]): JValue = {
    response match {
      case success: JsonRpcSuccessResponse[JValue] => Extraction.decompose(success)
      case err: JsonRpcErrorResponse[JValue]       => Extraction.decompose(err)
    }
  }

  override def parse(string: String): DecodeResult[JsonSupport.Json[JValue]] = {
    Try(parseAsJValue(string)) match {
      case Failure(exception) => DecodeResult.Error(string, exception)
      case Success(value) =>
        DecodeResult.Value(materialize(value))
    }
  }

  override def stringify(raw: JValue): String = render(raw)

  override def decodeJsonRpcRequest(obj: Json.JsonObject[JValue]): DecodeResult[JsonRpcRequest[Json[JValue]]] = {
    val raw = demateralize(obj)
    Try(raw.extract[JsonRpcRequest[JValue]]) match {
      case Failure(exception) => DecodeResult.Error(raw.toString, exception)
      case Success(value)     => DecodeResult.Value(value.copy(params = value.params.map(materialize)))
    }
  }

  override def materialize(raw: JValue): Json[JValue] = {
    raw match {
      case JObject(obj) => Json.JsonObject(obj)
      case JArray(arr)  => Json.JsonArray(arr.toVector)
      case other        => Json.Other(other)
    }
  }

  override def demateralize(json: Json[JValue]): JValue = {
    json match {
      case Json.JsonObject(fields) => JObject(fields)
      case Json.JsonArray(values)  => JArray(values.toList)
      case Json.Other(raw)         => raw
    }
  }
}

object Json4sSupport {
  def apply(parseAsJValue: String => JValue, render: JValue => String)(implicit
      formats: Formats,
      serialization: Serialization
  ): Json4sSupport = {
    new Json4sSupport(parseAsJValue, render)(formats + JsonRpcIdSerializer, serialization)
  }

  object JsonRpcIdSerializer
      extends CustomSerializer[JsonRpcId](_ =>
        (
          {
            case JInt(value)    => JsonRpcId.IntId(value.intValue)
            case JString(value) => JsonRpcId.StringId(value)
          },
          {
            case JsonRpcId.IntId(v)    => JInt(v)
            case JsonRpcId.StringId(v) => JString(v)
          }
        )
      )
}
