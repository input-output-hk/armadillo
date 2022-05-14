package io.iohk.armadillo

import sttp.tapir.SchemaType.SchemaWithValue
import sttp.tapir.{Schema, SchemaType}

case class JsonRpcRequest[Raw](jsonrpc: String, method: String, params: Raw, id: Option[JsonRpcId]) {
  def isNotification: Boolean = id.isEmpty
}
object JsonRpcRequest {
  implicit def schema[Raw: Schema]: Schema[JsonRpcRequest[Raw]] = Schema.derived[JsonRpcRequest[Raw]]

  def v2[Raw](method: String, params: Raw, id: JsonRpcId): JsonRpcRequest[Raw] =
    JsonRpcRequest(JsonRpcVersion_2_0, method, params, Some(id))
}
object Notification {
  def v2[Raw](method: String, params: Raw): JsonRpcRequest[Raw] =
    JsonRpcRequest(JsonRpcVersion_2_0, method, params, None)
}

sealed trait JsonRpcId
object JsonRpcId {
  case class IntId(value: Int) extends JsonRpcId
  case class StringId(value: String) extends JsonRpcId

  implicit def intAsId(v: Int): JsonRpcId.IntId = JsonRpcId.IntId(v)
  implicit def stringAsId(v: String): JsonRpcId.StringId = JsonRpcId.StringId(v)

  implicit val schema: Schema[JsonRpcId] = {
    val s1 = Schema.schemaForInt
    val s2 = Schema.schemaForString
    Schema[JsonRpcId](
      SchemaType.SCoproduct(List(s1, s2), None) {
        case IntId(v)    => Some(SchemaWithValue(s1, v))
        case StringId(v) => Some(SchemaWithValue(s2, v))
      },
      for {
        na <- s1.name
        nb <- s2.name
      } yield Schema.SName("JsonRpcId", List(na.show, nb.show))
    )
  }
}

sealed trait JsonRpcResponse[Raw] {
  def jsonrpc: String
}
object JsonRpcResponse {
  def v2[Raw](result: Raw, id: JsonRpcId): JsonRpcSuccessResponse[Raw] =
    JsonRpcSuccessResponse[Raw](JsonRpcVersion_2_0, result, id)
  def error_v2[Raw](error: Raw, id: Option[JsonRpcId] = None): JsonRpcErrorResponse[Raw] =
    JsonRpcErrorResponse[Raw](JsonRpcVersion_2_0, error, id)
}

case class JsonRpcSuccessResponse[Raw](jsonrpc: String, result: Raw, id: JsonRpcId) extends JsonRpcResponse[Raw]
object JsonRpcSuccessResponse {
  implicit def schema[Raw: Schema]: Schema[JsonRpcSuccessResponse[Raw]] = Schema.derived[JsonRpcSuccessResponse[Raw]]
}

case class JsonRpcErrorResponse[Raw](jsonrpc: String, error: Raw, id: Option[JsonRpcId]) extends JsonRpcResponse[Raw]
object JsonRpcErrorResponse {
  implicit def schema[Raw: Schema]: Schema[JsonRpcErrorResponse[Raw]] = Schema.derived[JsonRpcErrorResponse[Raw]]
}

case class JsonRpcError[Data](code: Int, message: String, data: Data)
object JsonRpcError {
  implicit def schema[Data: Schema]: Schema[JsonRpcError[Data]] = Schema.derived[JsonRpcError[Data]]
  def noData(code: Int, msg: String): JsonRpcError[Unit] = JsonRpcError(code, msg, ())
}
