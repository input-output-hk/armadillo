package io.iohk.armadillo.server

import io.iohk.armadillo._
import io.iohk.armadillo.server.JsonSupport.Json
import sttp.tapir.DecodeResult

trait JsonSupport[Raw] {
  def encodeErrorNoData(error: JsonRpcError.NoData): Raw
  def encodeErrorWithData(error: JsonRpcError[Raw]): Raw

  def encodeResponse(e: JsonRpcResponse[Raw]): Raw

  def parse(string: String): DecodeResult[Json[Raw]]
  def stringify(raw: Raw): String

  def materialize(raw: Raw): Json[Raw]
  def demateralize(json: Json[Raw]): Raw

  def decodeJsonRpcRequest(raw: Json.JsonObject[Raw]): DecodeResult[JsonRpcRequest[Json[Raw]]]

  def asArray(seq: Seq[Raw]): Raw
  def jsNull: Raw
}

object JsonSupport {
  sealed trait Json[Raw]
  object Json {
    case class JsonObject[Raw](fields: List[(String, Raw)]) extends Json[Raw]
    case class JsonArray[Raw](values: Vector[Raw]) extends Json[Raw]
    case class Other[Raw](raw: Raw) extends Json[Raw]
  }
}
