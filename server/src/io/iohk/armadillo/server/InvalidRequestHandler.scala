package io.iohk.armadillo.server

import io.iohk.armadillo.JsonRpcResponse
import io.iohk.armadillo.server.JsonSupport.Json
import io.iohk.armadillo.server.ServerInterpreter.{ResponseHandlingStatus, ServerResponse}
import sttp.tapir.DecodeResult

trait InvalidRequestHandler[Raw] {
  def apply(request: Json[Raw], failure: DecodeResult.Failure, jsonSupport: JsonSupport[Raw]): ResponseHandlingStatus[Raw]
}

object InvalidRequestHandler {
  def default[Raw]: InvalidRequestHandler[Raw] = (_: Json[Raw], _: DecodeResult.Failure, jsonSupport: JsonSupport[Raw]) => {
    ResponseHandlingStatus.Handled(
      Some(
        ServerResponse.Failure(
          jsonSupport.encodeResponse(JsonRpcResponse.error_v2(jsonSupport.encodeErrorNoData(ServerInterpreter.InvalidRequest)))
        )
      )
    )
  }
}
