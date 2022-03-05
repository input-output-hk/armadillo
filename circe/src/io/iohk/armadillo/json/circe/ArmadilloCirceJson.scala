package io.iohk.armadillo.json.circe

import io.circe.Decoder.Result
import io.circe.generic.semiauto.*
import io.circe.*
import io.iohk.armadillo.tapir.TapirInterpreter.{JsonRpcRequest, JsonRpcResponse}
import sttp.tapir.Codec.JsonCodec
import sttp.tapir.json.circe.*
import sttp.tapir.{DecodeResult, Schema}

trait ArmadilloCirceJson {
  // jsonrpc: String, method: String, params: Raw, id: Int
//  implicit def jsonRpcCodec[H: Encoder: Decoder: Schema]: JsonRpcCodec[H] = new JsonRpcCodec[H] {
//    override type L = Json
//
//    override def decode(l: Json): DecodeResult[H] = {
//      Decoder[H].decodeJson(l) match {
//        case Left(value)  => DecodeResult.Error(l.noSpaces, value)
//        case Right(value) => DecodeResult.Value(value)
//      }
//    }
//
//    override def encode(h: H): Json = Encoder[H].apply(h)
//
//    override def schema: Schema[H] = implicitly[Schema[H]]
//  }

  // to mozna zrobic samemu poprzez tapira nie trzeba dodatkowy rzeczy
//  implicit def jsonRpcRequestPartialCodec[T]: JsonCodec[JsonRpcRequestPartial] =

  implicit def encoderFromJsonCodec[T: JsonCodec]: Encoder[T] = Encoder { t =>
    io.circe.parser.parse(implicitly[JsonCodec[T]].encode(t)).getOrElse(throw new IllegalStateException("cannot happen"))
  }

  implicit def decoderFromJsonCodec[T: JsonCodec]: Decoder[T] = new Decoder[T] {
    override def apply(c: HCursor): Result[T] = {
      implicitly[JsonCodec[T]].decode(Printer.noSpaces.print(c.value)) match {
        case failure: DecodeResult.Failure =>
          failure match {
            case DecodeResult.Missing                => Left(DecodingFailure.apply("Missing", Nil))
            case DecodeResult.Multiple(vs)           => Left(DecodingFailure.apply(vs.mkString, Nil))
            case DecodeResult.Error(original, error) => Left(DecodingFailure.fromThrowable(error, Nil))
            case DecodeResult.Mismatch(expected, actual) =>
              Left(DecodingFailure.apply(s"Mismatch expected $expected, actual: $actual", Nil))
            case DecodeResult.InvalidValue(errors) => Left(DecodingFailure.apply(errors.mkString, Nil))
          }
        case DecodeResult.Value(v) => Right(v)
      }
    }
  }

  // funkcja ktora dla dowolnego T dla ktorego istnieje jsonRpcCodec robi JsonCodec?
  def jsonRpRequestCodec[T](bodyCodec: JsonCodec[T]): JsonCodec[JsonRpcRequest[T]] = {
    implicit val bc: JsonCodec[T] = bodyCodec
    implicit val be: Encoder[T] = encoderFromJsonCodec[T]
    implicit val bd: Decoder[T] = decoderFromJsonCodec[T]
    implicit val bs: Schema[T] = bodyCodec.schema
    val outerSchema: Schema[JsonRpcRequest[T]] = Schema.derived[JsonRpcRequest[T]]
    val outerEncoder: Encoder[JsonRpcRequest[T]] = deriveEncoder[JsonRpcRequest[T]]
    val outerDecoder: Decoder[JsonRpcRequest[T]] = deriveDecoder[JsonRpcRequest[T]]
    circeCodec[JsonRpcRequest[T]](outerEncoder, outerDecoder, outerSchema)
  }

  def jsonRpcResponseCodec[T](bodyCodec: JsonCodec[T]): JsonCodec[JsonRpcResponse[T]] = {
    implicit val bc: JsonCodec[T] = bodyCodec
    implicit val be: Encoder[T] = encoderFromJsonCodec[T]
    implicit val bd: Decoder[T] = decoderFromJsonCodec[T]
    implicit val bs: Schema[T] = bodyCodec.schema
    val outerSchema: Schema[JsonRpcResponse[T]] = Schema.derived[JsonRpcResponse[T]]
    val outerEncoder: Encoder[JsonRpcResponse[T]] = deriveEncoder[JsonRpcResponse[T]]
    val outerDecoder: Decoder[JsonRpcResponse[T]] = deriveDecoder[JsonRpcResponse[T]]
    circeCodec[JsonRpcResponse[T]](outerEncoder, outerDecoder, outerSchema)
  }

}
