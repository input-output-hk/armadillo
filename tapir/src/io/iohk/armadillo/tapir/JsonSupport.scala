package io.iohk.armadillo.tapir

import io.iohk.armadillo.Armadillo.{
  JsonRpcCodec,
  JsonRpcErrorResponse,
  JsonRpcErrorWithData,
  JsonRpcErrorNoData,
  JsonRpcRequest,
  JsonRpcSuccessResponse
}
import sttp.tapir.Codec.JsonCodec
import sttp.tapir.DecodeResult

trait JsonSupport[Raw] {
  def outRawCodec: JsonCodec[Raw]
  def outCodec: JsonCodec[JsonRpcSuccessResponse[Raw]]
  def errorOutCodec: JsonCodec[JsonRpcErrorResponse[Raw]]

  def encodeError(e: JsonRpcErrorResponse[Raw]): Raw
  def encodeSuccess(e: JsonRpcSuccessResponse[Raw]): Raw

  def parse(string: String): DecodeResult[Raw]

  def asArray(seq: Vector[Raw]): Raw
  def asObject(fields: Map[String, Raw]): Raw

  def emptyObject: Raw

  def getByIndex(arr: Raw, index: Int): DecodeResult[Raw]
  def getByField(obj: Raw, field: String): DecodeResult[Raw]

  // TODO change to decode and implement as codec for simplicity
  def inRpcCodec: JsonRpcCodec[JsonRpcRequest[Raw]]
  def encodeErrorNoData(error: JsonRpcErrorNoData): Raw

  def fold[T](raw: Raw)(asArray: Vector[Raw] => T, asObject: Raw => T, other: Raw => T): T
}
