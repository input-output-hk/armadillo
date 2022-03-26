package io.iohk

package object armadillo extends Armadillo with MethodNameInterpolator {
  val JsonRpcVersion_2_0: String = "2.0"

  type AnyEndpoint = JsonRpcEndpoint[_, _, _]
  type AnyRequest = JsonRpcRequest[_]
}
