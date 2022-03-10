package io.iohk.armadillo.json.circe

import cats.syntax.all.*
import io.circe.*
import io.circe.generic.semiauto.*
import io.iohk.armadillo.Armadillo.{JsonRpcError, JsonRpcErrorResponse, JsonRpcRequest, JsonRpcResponse}
import io.iohk.armadillo.tapir.JsonSupport
import sttp.tapir.Codec.JsonCodec
import sttp.tapir.SchemaType.SCoproduct
import sttp.tapir.json.circe.circeCodec
import sttp.tapir.{DecodeResult, Schema}

class CirceJsonSupport extends JsonSupport[Json] {
  // Json is a coproduct with unknown implementations
  implicit val schemaForCirceJson: Schema[Json] =
    Schema(
      SCoproduct(Nil, None)(_ => None),
      None
    )

  override def inCodec: JsonCodec[JsonRpcRequest[Json]] = {
    val outerSchema = Schema.derived[JsonRpcRequest[Json]]
    val outerEncoder = deriveEncoder[JsonRpcRequest[Json]]
    val outerDecoder = deriveDecoder[JsonRpcRequest[Json]]
    circeCodec[JsonRpcRequest[Json]](outerEncoder, outerDecoder, outerSchema)
  }

  override def outCodec: JsonCodec[JsonRpcResponse[Json]] = {
    val outerSchema = Schema.derived[JsonRpcResponse[Json]]
    val outerEncoder = deriveEncoder[JsonRpcResponse[Json]]
    val outerDecoder = deriveDecoder[JsonRpcResponse[Json]]
    circeCodec[JsonRpcResponse[Json]](outerEncoder, outerDecoder, outerSchema)
  }

  override def errorOutCodec: JsonCodec[JsonRpcErrorResponse[Json]] = {
    implicit val jsonRpcErrorSchema: Schema[JsonRpcError[Json]] = Schema.derived[JsonRpcError[Json]]
    val outerSchema = Schema.derived[JsonRpcErrorResponse[Json]]
    implicit val jsonRpcErrorEncoder: Encoder.AsObject[JsonRpcError[Json]] = deriveEncoder[JsonRpcError[Json]]
    val outerEncoder = deriveEncoder[JsonRpcErrorResponse[Json]]
    implicit val jsonRpcErrorDecoder: Decoder[JsonRpcError[Json]] = deriveDecoder[JsonRpcError[Json]]
    val outerDecoder = deriveDecoder[JsonRpcErrorResponse[Json]]
    circeCodec[JsonRpcErrorResponse[Json]](outerEncoder, outerDecoder, outerSchema)
  }

  override def getByIndex(arr: Json, index: Int): DecodeResult[Json] = {
    arr.asArray match {
      case Some(value) =>
        value.get(index) match {
          case Some(value) => DecodeResult.Value(value)
          case None        => DecodeResult.Missing
        }
      case None => DecodeResult.Mismatch("JsonArray", arr.toString())
    }
  }

  override def getByField(obj: Json, field: String): DecodeResult[Json] = {
    obj.asObject match {
      case Some(value) =>
        value.toList.toMap.get(field) match {
          case Some(value) => DecodeResult.Value(value)
          case None        => DecodeResult.Missing
        }
      case None => DecodeResult.Mismatch("JsonObject", obj.toString())
    }
  }

  override def empty: Json = Json.Null

  override def emptyList: Json = Json.arr()
}
