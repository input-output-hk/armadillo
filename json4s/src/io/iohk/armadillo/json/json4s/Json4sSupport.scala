package io.iohk.armadillo.json.json4s

import io.iohk.armadillo.Armadillo.*
import io.iohk.armadillo.JsonSupport
import io.iohk.armadillo.JsonSupport.Json
import org.json4s.*
import org.json4s.JsonAST.JValue
import sttp.tapir.Codec.JsonCodec
import sttp.tapir.SchemaType.SCoproduct
import sttp.tapir.json.json4s.*
import sttp.tapir.{DecodeResult, Schema}

import scala.util.{Failure, Success, Try}

class Json4sSupport private (parseAsJValue: String => JValue, render: JValue => String)(implicit
    formats: Formats,
    serialization: Serialization
) extends JsonSupport[JValue] {
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

  override def jsNull: JValue = JNull

  override def outRawCodec: JsonCodec[JValue] = json4sCodec[JValue]

  override def encodeError(e: JsonRpcErrorResponse[JValue]): JValue = {
    Extraction.decompose(e)
  }

  override def encodeErrorNoData(error: JsonRpcError[Unit]): JValue = {
    val map = Map("code" -> error.code, "message" -> error.message)
    Extraction.decompose(map)
  }

  override def encodeSuccess(e: JsonRpcSuccessResponse[JValue]): JValue = {
    Extraction.decompose(e)
  }

  override def parse(string: String): DecodeResult[JsonSupport.Json[JValue]] = {
    Try(parseAsJValue(string)) match {
      case Failure(exception) => DecodeResult.Error(string, exception)
      case Success(value) =>
        value match {
          case obj @ JObject(_) => DecodeResult.Value(Json.JsonObject(obj))
          case JArray(arr)      => DecodeResult.Value(Json.JsonArray(arr.toVector))
          case other            => DecodeResult.Value(Json.Other(other))
        }
    }
  }

  override def stringify(raw: JValue): String = render(raw)

  override def decodeJsonRpcRequest(raw: JValue): DecodeResult[JsonRpcRequest[JValue]] = {
    Try(raw.extract[JsonRpcRequest[JValue]]) match {
      case Failure(exception) => DecodeResult.Error(raw.toString, exception)
      case Success(value)     => DecodeResult.Value(value)
    }
  }
}

object Json4sSupport {
  def apply(parseAsJValue: String => JValue, render: JValue => String)(implicit
      formats: Formats,
      serialization: Serialization
  ): Json4sSupport = {
    new Json4sSupport(parseAsJValue, render)(formats + new JsonRpcIdSerializer, serialization)
  }

  private class JsonRpcIdSerializer
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
