package io.iohk.armadillo.openrpc

import sttp.apispec.AnySchema

package object circe extends ArmadilloOpenRpcCirce {
  override val anyObjectEncoding: AnySchema.Encoding = AnySchema.Encoding.Boolean
}
