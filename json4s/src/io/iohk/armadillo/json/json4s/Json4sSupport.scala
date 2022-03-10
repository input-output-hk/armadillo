package io.iohk.armadillo.json.json4s

import io.iohk.armadillo.Armadillo
import io.iohk.armadillo.Armadillo.{JsonRpcError, JsonRpcErrorResponse, JsonRpcRequest, JsonRpcResponse}
import io.iohk.armadillo.tapir.JsonSupport
import org.json4s.JsonAST.JValue
import org.json4s.{Formats, JArray, JNull, JObject, JValue, Serialization}
import sttp.tapir.Codec.JsonCodec
import sttp.tapir.SchemaType.SCoproduct
import sttp.tapir.json.json4s.*
import sttp.tapir.{DecodeResult, Schema}

class Json4sSupport(implicit formats: Formats, serialization: Serialization) extends JsonSupport[JValue] {
  // JValue is a coproduct with unknown implementations
  implicit val schemaForJson4s: Schema[JValue] =
    Schema(
      SCoproduct(Nil, None)(_ => None),
      None
    )

  override def inCodec: JsonCodec[JsonRpcRequest[JValue]] = {
    implicit val outerSchema: Schema[JsonRpcRequest[JValue]] = Schema.derived[JsonRpcRequest[JValue]]
    json4sCodec[JsonRpcRequest[JValue]]
  }

  override def outCodec: JsonCodec[JsonRpcResponse[JValue]] = {
    implicit val outerSchema: Schema[JsonRpcResponse[JValue]] = Schema.derived[JsonRpcResponse[JValue]]
    json4sCodec[JsonRpcResponse[JValue]]
  }

  override def errorOutCodec: JsonCodec[JsonRpcErrorResponse[JValue]] = {
    implicit val jsonRpcErrorSchema: Schema[JsonRpcError[JValue]] = Schema.derived[JsonRpcError[JValue]] // TODO move to public place
    implicit val outerSchema: Schema[JsonRpcErrorResponse[JValue]] = Schema.derived[JsonRpcErrorResponse[JValue]]
    json4sCodec[JsonRpcErrorResponse[JValue]]
  }

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

  override def empty: JValue = JNull

  override def emptyList: JValue = JArray(List.empty)
}
