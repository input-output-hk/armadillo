package io.iohk.armadillo.trace4cats

import trace4cats.SpanStatus

object ArmadilloStatusMapping {
  def errorStringToInternal[E]: ArmadilloStatusMapping[E] = e => SpanStatus.Internal(e.toString)
}
