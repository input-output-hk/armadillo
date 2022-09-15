package io.iohk.armadillo

import sttp.tapir.{DecodeResult, Validator}

object Validation {
  def from[H](validator: Validator[H])(h: H): DecodeResult[H] = {
    validator.apply(h) match {
      case Nil    => DecodeResult.Value(h)
      case errors => DecodeResult.InvalidValue(errors)
    }
  }
}
