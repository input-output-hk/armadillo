package io.iohk.armadillo.tapir

import io.iohk.armadillo.Armadillo.{JsonRpcErrorResponse, JsonRpcRequest, JsonRpcResponse}
import sttp.tapir.Codec.JsonCodec
import sttp.tapir.DecodeResult

trait JsonSupport[Raw] {
  def inCodec: JsonCodec[JsonRpcRequest[Raw]]
  def outCodec: JsonCodec[JsonRpcResponse[Raw]]
  def errorOutCodec: JsonCodec[JsonRpcErrorResponse[Raw]]

  def empty: Raw
  def emptyList: Raw

  def getByIndex(arr: Raw, index: Int): DecodeResult[Raw]
  def getByField(obj: Raw, field: String): DecodeResult[Raw]
}
