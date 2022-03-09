package io.iohk.armadillo.trace4cats

import cats.Show
import cats.implicits.toShow
import io.janstenpickle.trace4cats.model.SpanStatus

object ArmadilloStatusMapping {
  def errorShowToInternal[E: Show]: ArmadilloStatusMapping[E] = e => SpanStatus.Internal(e.show)
  def errorMessageToInternal[E <: Throwable]: ArmadilloStatusMapping[E] = e => SpanStatus.Internal(e.getMessage)
  def errorStringToInternal[E]: ArmadilloStatusMapping[E] = e => SpanStatus.Internal(e.toString)
}
