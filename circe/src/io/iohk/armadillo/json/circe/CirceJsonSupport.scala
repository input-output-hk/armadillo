package io.iohk.armadillo.json.circe

import cats.syntax.all.*
import io.circe.Decoder.Result
import io.circe.generic.semiauto.*
import io.circe.*
import io.iohk.armadillo.Armadillo.{JsonRpcCodec, JsonRpcRequest, JsonRpcResponse}
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

  override def combineDecode(in: Vector[JsonRpcCodec[_]]): Json => DecodeResult[Vector[_]] = { json =>
    val decoder = new Decoder[Vector[_]] {
      override def apply(c: HCursor): Result[Vector[_]] = {
        in.zipWithIndex.traverse { case (item, index) =>
          c.downN(index).focus match {
            case Some(value) =>
              item.decode(value.asInstanceOf[item.L]) match {
                case failure: DecodeResult.Failure => Left(DecodingFailure.fromThrowable(new RuntimeException(failure.toString), Nil))
                case DecodeResult.Value(v)         => Right(v)
              }
            case None => Left(DecodingFailure.apply("missing value", Nil))
          }
        }
      }
    }
    decoder.decodeJson(json) match {
      case Left(value)  => DecodeResult.Error(json.noSpaces, value)
      case Right(value) => DecodeResult.Value(value)
    }
  }
}
