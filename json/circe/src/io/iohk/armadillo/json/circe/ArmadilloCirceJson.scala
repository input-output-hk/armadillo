package io.iohk.armadillo.json.circe

import cats.implicits.toFunctorOps
import io.circe.generic.semiauto._
import io.circe.{Decoder, Encoder, Json}
import io.iohk.armadillo._
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
  }

  implicit val jsonRpcIdDecoder: Decoder[JsonRpcId] = Decoder.decodeInt
    .map(JsonRpcId.IntId)
    .widen
    .or(Decoder.decodeString.map(JsonRpcId.StringId).widen[JsonRpcId])

  implicit val jsonRpcErrorNoDataEncoder: Encoder[JsonRpcError[Unit]] = Encoder { i =>
    Json.obj("code" -> Json.fromInt(i.code), "message" -> Json.fromString(i.message))
  }
  implicit val jsonRpcErrorNoDataDecoder: Decoder[JsonRpcError[Unit]] = Decoder { i =>
    for {
      code <- i.downField("code").as[Int]
      msg <- i.downField("message").as[String]
    } yield JsonRpcError.noData(code, msg)
  }
  implicit def jsonRpcErrorEncoder[T: Encoder]: Encoder[JsonRpcError[T]] = deriveEncoder[JsonRpcError[T]]
  implicit def jsonRpcErrorDecoder[T: Decoder]: Decoder[JsonRpcError[T]] = deriveDecoder[JsonRpcError[T]]

  implicit val jsonRpcSuccessResponseEncoder: Encoder[JsonRpcSuccessResponse[Json]] = deriveEncoder[JsonRpcSuccessResponse[Json]]
  implicit val jsonRpcRequestDecoder: Decoder[JsonRpcRequest[Json]] = deriveDecoder[JsonRpcRequest[Json]]
  implicit val jsonRpcErrorResponseEncoder: Encoder[JsonRpcErrorResponse[Json]] = deriveEncoder[JsonRpcErrorResponse[Json]]
}
