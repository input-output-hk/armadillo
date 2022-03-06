package io.iohk.armadillo.json.circe

import cats.syntax.all.*
import io.circe.*
import io.circe.generic.semiauto.*
import io.iohk.armadillo.Armadillo.{JsonRpcRequest, JsonRpcResponse}
import io.iohk.armadillo.tapir.JsonSupport
import sttp.tapir.Codec.JsonCodec
import sttp.tapir.json.circe.circeCodec
import sttp.tapir.{DecodeResult, Schema}

class CirceJsonSupport extends JsonSupport[Json] {

  implicit val jsonSchema: Schema[Json] = Schema.schemaForString.as

  override def requestCodec: JsonCodec[JsonRpcRequest[Json]] = {

    val outerSchema: Schema[JsonRpcRequest[Json]] = Schema.derived[JsonRpcRequest[Json]]
    val outerEncoder: Encoder[JsonRpcRequest[Json]] = deriveEncoder[JsonRpcRequest[Json]]
    val outerDecoder: Decoder[JsonRpcRequest[Json]] = deriveDecoder[JsonRpcRequest[Json]]
    circeCodec[JsonRpcRequest[Json]](outerEncoder, outerDecoder, outerSchema)
  }

  override def responseCodec: JsonCodec[JsonRpcResponse[Json]] = {
    val outerSchema: Schema[JsonRpcResponse[Json]] = Schema.derived[JsonRpcResponse[Json]]
    val outerEncoder: Encoder[JsonRpcResponse[Json]] = deriveEncoder[JsonRpcResponse[Json]]
    val outerDecoder: Decoder[JsonRpcResponse[Json]] = deriveDecoder[JsonRpcResponse[Json]]
    circeCodec[JsonRpcResponse[Json]](outerEncoder, outerDecoder, outerSchema)
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
}
