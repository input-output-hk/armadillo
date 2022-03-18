package io.iohk.armadillo.json.circe

import cats.syntax.all.*
import io.circe.*
import io.circe.generic.semiauto.*
import io.iohk.armadillo.Armadillo.{JsonRpcErrorResponse, JsonRpcRequest, JsonRpcSuccessResponse}
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

  override def outCodec: JsonCodec[JsonRpcSuccessResponse[Json]] = {
    val outerSchema = Schema.derived[JsonRpcSuccessResponse[Json]]
    val outerEncoder = deriveEncoder[JsonRpcSuccessResponse[Json]]
    val outerDecoder = deriveDecoder[JsonRpcSuccessResponse[Json]]
    circeCodec[JsonRpcSuccessResponse[Json]](outerEncoder, outerDecoder, outerSchema)
  }

  override def errorOutCodec: JsonCodec[JsonRpcErrorResponse[Json]] = {
    val outerSchema = Schema.derived[JsonRpcErrorResponse[Json]]
    val outerEncoder = deriveEncoder[JsonRpcErrorResponse[Json]]
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

//  override def emptyList: Json = Json.arr()

  override def asArray(seq: Vector[Json]): Json = Json.arr(seq *)

  override def asObject(fields: Map[String, Json]): Json = Json.obj(fields.toList *)
}
