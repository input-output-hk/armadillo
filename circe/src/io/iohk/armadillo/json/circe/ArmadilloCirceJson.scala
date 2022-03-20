package io.iohk.armadillo.json.circe

import cats.implicits.toFunctorOps
import io.circe.generic.semiauto.*
import io.circe.{Decoder, Encoder, Json}
import io.iohk.armadillo.Armadillo.*
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

  implicit val jsonRpcIdEncoder: Encoder[JsonRpcId] = Encoder.instance[JsonRpcId] {
    case JsonRpcId.IntId(value)    => Encoder.encodeInt(value)
    case JsonRpcId.StringId(value) => Encoder.encodeString(value)
    case JsonRpcId.NullId          => Encoder.encodeNone(None)
  }

  implicit val jsonRpcIdDecoder: Decoder[JsonRpcId] = Decoder.decodeInt
    .map(JsonRpcId.IntId)
    .widen
    .or(Decoder.decodeString.map(JsonRpcId.StringId).widen[JsonRpcId])
    .or(Decoder.decodeNone.map(_ => JsonRpcId.NullId).widen[JsonRpcId])

  implicit def jsonRpcErrorWithDataEncoder[T: Encoder]: Encoder[JsonRpcErrorWithData[T]] = deriveEncoder[JsonRpcErrorWithData[T]]
  implicit def jsonRpcErrorWithDataDecoder[T: Decoder]: Decoder[JsonRpcErrorWithData[T]] = deriveDecoder[JsonRpcErrorWithData[T]]

  implicit val jsonRpcNoDataErrorEncoder: Encoder[JsonRpcErrorNoData] = deriveEncoder[JsonRpcErrorNoData]
  implicit val jsonRpcNoDataErrorDecoder: Decoder[JsonRpcErrorNoData] = deriveDecoder[JsonRpcErrorNoData]

  implicit val jsonRpcSuccessResponseEncoder: Encoder[JsonRpcSuccessResponse[Json]] = deriveEncoder[JsonRpcSuccessResponse[Json]]
  implicit val jsonRpcRequestDecoder: Decoder[JsonRpcRequest[Json]] = deriveDecoder[JsonRpcRequest[Json]]
  implicit val jsonRpcErrorResponseEncoder: Encoder[JsonRpcErrorResponse[Json]] = deriveEncoder[JsonRpcErrorResponse[Json]]
}
