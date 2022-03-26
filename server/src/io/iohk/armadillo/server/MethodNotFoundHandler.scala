package io.iohk.armadillo.server

import io.iohk.armadillo.{JsonRpcRequest, JsonRpcResponse}

trait MethodNotFoundHandler[Raw] {
  def apply(request: JsonRpcRequest[Raw], jsonSupport: JsonSupport[Raw]): Option[JsonRpcResponse[Raw]]
}

object MethodNotFoundHandler {
  def default[Raw]: MethodNotFoundHandler[Raw] = (request: JsonRpcRequest[Raw], jsonSupport: JsonSupport[Raw]) => {
    request.id.map(id => JsonRpcResponse.error_v2(jsonSupport.encodeErrorNoData(ServerInterpreter.MethodNotFound), Some(id)))
  }
}
