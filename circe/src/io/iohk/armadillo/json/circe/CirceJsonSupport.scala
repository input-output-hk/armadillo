package io.iohk.armadillo.json.circe

import io.circe.generic.semiauto.*
import io.circe.{Decoder, Encoder, Json, Printer}
import io.iohk.armadillo.Armadillo.{JsonRpcRequest, JsonRpcResponse}
import io.iohk.armadillo.tapir.JsonSupport
import sttp.tapir.Codec.JsonCodec
import sttp.tapir.Schema
import sttp.tapir.json.circe.circeCodec

class CirceJsonSupport extends JsonSupport {
  override type Raw = Json

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

  override def parse(str: String): Json = io.circe.parser.parse(str) match {
    case Left(value)  => throw new IllegalStateException("cannot happen as string is already parsed", value)
    case Right(value) => value
  }

  override def stringify(raw: Json): String = Printer.noSpaces.print(raw)
}
