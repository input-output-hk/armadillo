package io.iohk.armadillo

import sttp.tapir.{DecodeResult, Schema}

trait JsonRpcCodec[H] {
  type L
  def decode(l: L): DecodeResult[H]
  def encode(h: H): L
  def schema: Schema[H]
}
