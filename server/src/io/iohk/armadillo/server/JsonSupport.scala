package io.iohk.armadillo.server

import io.iohk.armadillo.Armadillo.*
import io.iohk.armadillo.server.JsonSupport.Json
import sttp.tapir.Codec.JsonCodec
import sttp.tapir.DecodeResult

trait JsonSupport[Raw] {
  def outRawCodec: JsonCodec[Raw]

  def encodeError(e: JsonRpcErrorResponse[Raw]): Raw
  def encodeErrorNoData(error: JsonRpcError[Unit]): Raw
  def encodeSuccess(e: JsonRpcSuccessResponse[Raw]): Raw

  def parse(string: String): DecodeResult[Json[Raw]]
  def stringify(raw: Raw): String

  def decodeJsonRpcRequest(raw: Raw): DecodeResult[JsonRpcRequest[Raw]]

  def asArray(seq: Vector[Raw]): Raw
  def jsNull: Raw

  def getByIndex(arr: Raw, index: Int): DecodeResult[Raw]
  def getByField(obj: Raw, field: String): DecodeResult[Raw]
}

object JsonSupport {
  sealed trait Json[Raw]
  object Json {
    case class JsonObject[Raw](raw: Raw) extends Json[Raw]
    case class JsonArray[Raw](values: Vector[Raw]) extends Json[Raw]
    case class Other[Raw](raw: Raw) extends Json[Raw]
  }
}
