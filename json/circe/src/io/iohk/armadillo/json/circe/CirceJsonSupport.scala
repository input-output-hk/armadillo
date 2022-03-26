package io.iohk.armadillo.json.circe

import cats.syntax.all.*
import io.circe.*
import io.iohk.armadillo.Armadillo.*
import io.iohk.armadillo.server.JsonSupport
import io.iohk.armadillo.server.JsonSupport.Json as AJson
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

  override def asArray(seq: Vector[Json]): Json = Json.arr(seq *)

  override def jsNull: Json = Json.Null

  override def parse(string: String): DecodeResult[AJson[Json]] = {
    circeCodec[Json].decode(string).map { json =>
      json.fold(
        jsonNull = AJson.Other(json),
        jsonBoolean = _ => AJson.Other(json),
        jsonNumber = _ => AJson.Other(json),
        jsonString = _ => AJson.Other(json),
        jsonArray = AJson.JsonArray.apply,
        jsonObject = _ => AJson.JsonObject(json)
      )
    }
  }

  override def stringify(raw: Json): String = raw.noSpaces

  override def encodeErrorNoData(error: JsonRpcError[Unit]): Json = Encoder[JsonRpcError[Unit]].apply(error)

  override def outRawCodec: JsonCodec[Json] = circeCodec[Json]

  override def encodeResponse(response: JsonRpcResponse[Json]): Json = {
    response match {
      case success: JsonRpcSuccessResponse[Json] => Encoder[JsonRpcSuccessResponse[Json]].apply(success)
      case er : JsonRpcErrorResponse[Json] => Encoder[JsonRpcErrorResponse[Json]].apply(er)
    }
  }

  override def decodeJsonRpcRequest(raw: Json): DecodeResult[JsonRpcRequest[Json]] = {
    Decoder[JsonRpcRequest[Json]].decodeJson(raw) match {
      case Left(value)  => DecodeResult.Error(raw.noSpaces, value)
      case Right(value) => DecodeResult.Value(value)
    }
  }
}