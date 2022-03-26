package io.iohk.armadillo.server

import io.iohk.armadillo.JsonRpcResponse
import io.iohk.armadillo.server.JsonSupport.Json
import sttp.tapir.DecodeResult

trait InvalidRequestHandler[Raw] {
  def apply(request: Json[Raw], failure: DecodeResult.Failure, jsonSupport: JsonSupport[Raw]): Option[JsonRpcResponse[Raw]]
}

object InvalidRequestHandler {
  def default[Raw]: InvalidRequestHandler[Raw] = (_: Json[Raw], _: DecodeResult.Failure, jsonSupport: JsonSupport[Raw]) => {
    Some(JsonRpcResponse.error_v2(jsonSupport.encodeErrorNoData(ServerInterpreter.InvalidRequest), None))
  }
}
