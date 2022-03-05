package io.iohk.armadillo.tapir

import sttp.tapir.Codec.JsonCodec
import sttp.tapir.DecodeResult

trait JsonRpcPartialCodec[W[_]] {

  def decode[Body: JsonCodec](t: String): DecodeResult[W[Body]]

  def encode[Body: JsonCodec](t: W[Body]): String
}
