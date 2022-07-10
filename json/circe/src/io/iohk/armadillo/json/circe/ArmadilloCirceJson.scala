package io.iohk.armadillo.json.circe

import cats.implicits.toFunctorOps
import io.circe.generic.semiauto._
import io.circe.{Decoder, Encoder, Json}
import io.iohk.armadillo._
import sttp.tapir.{DecodeResult, Schema}

trait ArmadilloCirceJson {

  /** Create a codec which decodes/encodes an optional value. The given base codec `c` is used for decoding/encoding.
    *
    * The schema is copied from the base codec.
    */
  implicit def option[T](implicit c: JsonRpcCodec[T]): JsonRpcCodec[Option[T]] = new JsonRpcCodec[Option[T]] {
    override type L = Json

    override def decode(l: Json): DecodeResult[Option[T]] = {
      l match {
        case Json.Null => DecodeResult.Value(None)
        case other     => c.decode(other.asInstanceOf[c.L]).map(Some(_))
      }
    }

    override def encode(h: Option[T]): Json = {
      h match {
        case Some(value) => c.encode(value).asInstanceOf[L]
        case None        => Json.Null
      }
    }

    override def schema: Schema[Option[T]] = c.schema.asOption
  }

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
    } yield JsonRpcError(code, msg, ())
  }
  implicit def jsonRpcErrorEncoder[T: Encoder]: Encoder[JsonRpcError[T]] = deriveEncoder[JsonRpcError[T]]
  implicit def jsonRpcErrorDecoder[T: Decoder]: Decoder[JsonRpcError[T]] = deriveDecoder[JsonRpcError[T]]

  implicit val jsonRpcSuccessResponseEncoder: Encoder[JsonRpcSuccessResponse[Json]] = deriveEncoder[JsonRpcSuccessResponse[Json]]
  implicit val jsonRpcRequestDecoder: Decoder[JsonRpcRequest[Json]] = deriveDecoder[JsonRpcRequest[Json]]
  implicit val jsonRpcErrorResponseEncoder: Encoder[JsonRpcErrorResponse[Json]] = deriveEncoder[JsonRpcErrorResponse[Json]]
}
