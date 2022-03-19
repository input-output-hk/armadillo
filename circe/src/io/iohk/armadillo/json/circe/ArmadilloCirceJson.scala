package io.iohk.armadillo.json.circe

import io.circe.generic.semiauto.*
import io.circe.{Decoder, Encoder, Json}
import io.iohk.armadillo.Armadillo.{JsonRpcCodec, JsonRpcError, JsonRpcNoDataError}
import sttp.tapir.{DecodeResult, Schema}

trait ArmadilloCirceJson {
  implicit def jsonRpcCodec[H: Encoder: Decoder: Schema]: JsonRpcCodec[H] = new JsonRpcCodec[H] {
    override type L = Json

    override def encode(h: H): Json = Encoder[H].apply(h)

    override def schema: Schema[H] = implicitly[Schema[H]]

    override def decode(l: Json): DecodeResult[H] = {
      implicitly[Decoder[H]].decodeJson(l) match {
        case Left(value)  => DecodeResult.Error(l.noSpaces, value)
        case Right(value) => DecodeResult.Value(value)
      }
    }
  }

  implicit def jsonRpcErrorEncoder[T: Encoder]: Encoder[JsonRpcError[T]] = deriveEncoder[JsonRpcError[T]]
  implicit def jsonRpcErrorDecoder[T: Decoder]: Decoder[JsonRpcError[T]] = deriveDecoder[JsonRpcError[T]]

  implicit def jsonRpcNoDataErrorEncoder: Encoder[JsonRpcNoDataError] = deriveEncoder[JsonRpcNoDataError]
  implicit def jsonRpcNoDataErrorDecoder: Decoder[JsonRpcNoDataError] = deriveDecoder[JsonRpcNoDataError]
}
