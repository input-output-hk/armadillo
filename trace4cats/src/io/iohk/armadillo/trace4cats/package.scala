package io.iohk.armadillo

import _root_.trace4cats.SpanStatus

package object trace4cats {
  type ArmadilloSpanNamer[I] = (JsonRpcEndpoint[I, _, _], I) => String
  type ArmadilloInputSpanNamer[I] = I => String
  type ArmadilloStatusMapping[E] = E => SpanStatus
}
