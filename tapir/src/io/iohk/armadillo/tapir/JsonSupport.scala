package io.iohk.armadillo.tapir

import io.iohk.armadillo.Armadillo.{JsonRpcRequest, JsonRpcResponse}
import sttp.tapir.Codec.JsonCodec

trait JsonSupport {
  type Raw
  def requestCodec: JsonCodec[JsonRpcRequest[Raw]]
  def responseCodec: JsonCodec[JsonRpcResponse[Raw]]
  def parse(str: String): Raw
  def stringify(raw: Raw): String
}
