package io.iohk.armadillo

package object server {
  implicit class EndpointOps[I, E, O](jsonRpcEndpoint: JsonRpcEndpoint[I, E, O]) {
    def `override`: OverridingEndpoint[I, E, O] = OverridingEndpoint(jsonRpcEndpoint)
  }
  implicit class ServerEndpointOps[I, E, O, F[_]](jsonRpcServerEndpoint: JsonRpcServerEndpoint.Full[I, E, O, F]) {
    def `override`: OverridingEndpoint[I, E, O] = jsonRpcServerEndpoint.endpoint.`override`
  }
}
