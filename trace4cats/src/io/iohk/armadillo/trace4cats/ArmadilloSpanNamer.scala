package io.iohk.armadillo.trace4cats

object ArmadilloSpanNamer {
  def methodName[I]: ArmadilloSpanNamer[I] = (ep, _) => ep.methodName.asString
}
