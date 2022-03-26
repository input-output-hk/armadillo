package io.iohk.armadillo.json.circe

import io.circe.*
import io.iohk.armadillo.*
import io.iohk.armadillo.server.JsonSupport
import io.iohk.armadillo.server.JsonSupport.Json as AJson
import sttp.tapir.DecodeResult

class CirceJsonSupport extends JsonSupport[Json] {

  override def asArray(seq: Vector[Json]): Json = Json.arr(seq *)

  override def jsNull: Json = Json.Null

  override def parse(string: String): DecodeResult[AJson[Json]] = {
    io.circe.parser.decode[Json](string) match {
      case Left(value) => DecodeResult.Error(string,value)
      case Right(value) => DecodeResult.Value(materialize(value))
    }
  }

  def materialize(json: Json):AJson[Json] = {
    json.fold(
      jsonNull = AJson.Other(json),
      jsonBoolean = _ => AJson.Other(json),
      jsonNumber = _ => AJson.Other(json),
      jsonString = _ => AJson.Other(json),
      jsonArray = arr => AJson.JsonArray.apply(arr),
      jsonObject = obj => AJson.JsonObject(obj.toList)
    )
  }

  override def demateralize(json: AJson[Json]): Json = {
    json match {
      case AJson.JsonObject(raw) => Json.obj(raw*)
      case AJson.JsonArray(raw) => asArray(raw)
      case AJson.Other(raw) => raw
    }
  }

  override def stringify(raw: Json): String = raw.noSpaces

  override def encodeErrorNoData(error: JsonRpcError[Unit]): Json = Encoder[JsonRpcError[Unit]].apply(error)

  override def encodeResponse(response: JsonRpcResponse[Json]): Json = {
    response match {
      case success: JsonRpcSuccessResponse[Json] => Encoder[JsonRpcSuccessResponse[Json]].apply(success)
      case er : JsonRpcErrorResponse[Json] => Encoder[JsonRpcErrorResponse[Json]].apply(er)
    }
  }

  override def decodeJsonRpcRequest(obj: AJson.JsonObject[Json]): DecodeResult[JsonRpcRequest[AJson[Json]]] = {
    val raw = demateralize(obj)
    Decoder[JsonRpcRequest[Json]].decodeJson(raw) match {
      case Left(value)  => DecodeResult.Error(raw.noSpaces, value)
      case Right(value) => DecodeResult.Value(value.copy(params = materialize(value.params)))
    }
  }
}
