package io.iohk.armadillo.tapir

import io.iohk.armadillo.Armadillo.{JsonRpcRequest, JsonRpcResponse}
import sttp.tapir.Codec.JsonCodec
import sttp.tapir.DecodeResult

trait JsonSupport[Raw] {
  def requestCodec: JsonCodec[JsonRpcRequest[Raw]]
  def responseCodec: JsonCodec[JsonRpcResponse[Raw]]

  def getByIndex(arr: Raw, index: Int): DecodeResult[Raw]
  def getByField(obj: Raw, field: String): DecodeResult[Raw]
}
