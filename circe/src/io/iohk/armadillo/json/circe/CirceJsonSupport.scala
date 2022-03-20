package io.iohk.armadillo.json.circe

import cats.syntax.all.*
import io.circe.*
import io.circe.generic.semiauto.*
import io.iohk.armadillo.Armadillo.{JsonRpcCodec, JsonRpcErrorResponse, JsonRpcRequest, JsonRpcSuccessResponse}
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
    val outerEncoder = deriveEncoder[JsonRpcRequest[Json]]
    val outerDecoder = deriveDecoder[JsonRpcRequest[Json]]
    circeCodec[JsonRpcRequest[Json]](outerEncoder, outerDecoder, implicitly[Schema[JsonRpcRequest[Json]]])
  }

  override def inRpcCodec: JsonRpcCodec[JsonRpcRequest[Json]] = {
    val outerEncoder = deriveEncoder[JsonRpcRequest[Json]]
    val outerDecoder = deriveDecoder[JsonRpcRequest[Json]]
    jsonRpcCodec[JsonRpcRequest[Json]](outerEncoder, outerDecoder, implicitly[Schema[JsonRpcRequest[Json]]])
  }

  override def rawCodec: JsonCodec[Json] =
    circeCodec[Json](Encoder[Json], Decoder[Json], schemaForCirceJson)

  override def outCodec: JsonCodec[JsonRpcSuccessResponse[Json]] = {
    val outerEncoder = deriveEncoder[JsonRpcSuccessResponse[Json]]
    val outerDecoder = deriveDecoder[JsonRpcSuccessResponse[Json]]
    circeCodec[JsonRpcSuccessResponse[Json]](outerEncoder, outerDecoder, implicitly[Schema[JsonRpcSuccessResponse[Json]]])
  }

  override def errorOutCodec: JsonCodec[JsonRpcErrorResponse[Json]] = {
    val outerEncoder = deriveEncoder[JsonRpcErrorResponse[Json]]
    val outerDecoder = deriveDecoder[JsonRpcErrorResponse[Json]]
    circeCodec[JsonRpcErrorResponse[Json]](outerEncoder, outerDecoder, implicitly[Schema[JsonRpcErrorResponse[Json]]])
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

  override def emptyObject: Json = Json.Null

  override def parse(string: String): DecodeResult[Json] = {
    circeCodec[Json].decode(string)
  }

  override def fold[T](raw: Json)(asArray: Vector[Json] => T, asObject: Json => T, other: Json => T): T = {
    raw.fold(
      jsonNull = other(raw),
      jsonBoolean = _ => other(raw),
      jsonNumber = _ => other(raw),
      jsonString = _ => other(raw),
      jsonArray = asArray,
      jsonObject = _ => asObject(raw)
    )
  }
}
