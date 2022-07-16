package io.iohk.armadillo

import io.janstenpickle.trace4cats.inject.SpanName
import io.janstenpickle.trace4cats.model.SpanStatus

package object trace4cats {
  type ArmadilloSpanNamer[I] = (JsonRpcEndpoint[I, _, _], I) => SpanName
  type ArmadilloInputSpanNamer[I] = I => SpanName
  type ArmadilloStatusMapping[E] = E => SpanStatus
}
