package io.iohk.armadillo

import sttp.tapir.{DecodeResult, Schema, Validator}

trait JsonRpcCodec[H] {
  type L
  type T = H
  def decode(l: L): DecodeResult[H]
  def encode(h: T): L
  def schema: Schema[T]

  def show(l: L): String
}
object JsonRpcCodec {
  implicit class JsonRpcCodecOps[H](val codec: JsonRpcCodec[H]) {
    def withValidator(validator: Validator[H]): JsonRpcCodec[H] = new JsonRpcCodec[H] {
      override type L = codec.L

      override def decode(l: L): DecodeResult[H] = codec.decode(l)

      override def encode(h: H): L = codec.encode(h)

      override def schema: Schema[H] = codec.schema.copy(validator = codec.schema.validator.and(validator))

      override def show(l: codec.L): String = codec.show(l)
    }
  }
}
