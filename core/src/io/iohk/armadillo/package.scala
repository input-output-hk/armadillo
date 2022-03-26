package io.iohk

import io.iohk.armadillo.Armadillo.JsonRpcRequest

package object armadillo {
  type AnyEndpoint = JsonRpcEndpoint[_, _, _]
  type AnyRequest = JsonRpcRequest[_]
}
