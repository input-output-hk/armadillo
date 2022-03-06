package io.iohk.armadillo.tapir

import io.iohk.armadillo.Armadillo.{JsonRpcCodec, JsonRpcRequest, JsonRpcResponse}
import sttp.tapir.Codec.JsonCodec
import sttp.tapir.DecodeResult

trait JsonSupport {
  type Raw
  def requestCodec: JsonCodec[JsonRpcRequest[Raw]]
  def responseCodec: JsonCodec[JsonRpcResponse[Raw]]
  def parse(str: String): Raw
  def stringify(raw: Raw): String
  def combineDecode(in: Vector[JsonRpcCodec[_]]): Raw => DecodeResult[Vector[Any]]
}
