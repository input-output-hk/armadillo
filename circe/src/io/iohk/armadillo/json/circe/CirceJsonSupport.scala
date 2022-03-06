package io.iohk.armadillo.json.circe

import cats.Traverse.ops.toAllTraverseOps
import io.circe.Decoder.Result
import io.circe.generic.semiauto.*
import io.circe.{Decoder, DecodingFailure, Encoder, HCursor, Json, Printer}
import io.iohk.armadillo.Armadillo.{JsonRpcCodec, JsonRpcRequest, JsonRpcResponse}
import io.iohk.armadillo.tapir.JsonSupport
import sttp.tapir.Codec.JsonCodec
import sttp.tapir.{DecodeResult, EndpointInput, Schema}
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

  override def combineDecode(in: Vector[JsonRpcCodec[_]]): Raw => DecodeResult[Vector[_]] = { json =>
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
