package io.iohk.armadillo.trace4cats

import io.janstenpickle.trace4cats.model.SpanStatus

object ArmadilloStatusMapping {
  def errorStringToInternal[E]: ArmadilloStatusMapping[E] = e => SpanStatus.Internal(e.toString)
}
