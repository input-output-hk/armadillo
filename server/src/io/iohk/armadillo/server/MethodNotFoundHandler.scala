package io.iohk.armadillo.server

import io.iohk.armadillo.server.JsonSupport.Json
import io.iohk.armadillo.server.ServerInterpreter.{ResponseHandlingStatus, ServerResponse}
import io.iohk.armadillo.{JsonRpcRequest, JsonRpcResponse}

trait MethodNotFoundHandler[Raw] {
  def apply(request: JsonRpcRequest[Json[Raw]], jsonSupport: JsonSupport[Raw]): ResponseHandlingStatus[Raw]
}

object MethodNotFoundHandler {
  def default[Raw]: MethodNotFoundHandler[Raw] = (request: JsonRpcRequest[Json[Raw]], jsonSupport: JsonSupport[Raw]) => {
    request.id match {
      case Some(id) =>
        ResponseHandlingStatus.Handled(
          Some(
            ServerResponse.Failure(
              jsonSupport.encodeResponse(
                JsonRpcResponse.error_v2(jsonSupport.encodeErrorNoData(ServerInterpreter.MethodNotFound), id)
              )
            )
          )
        )
      case None => ResponseHandlingStatus.Handled(None)
    }
  }
}
