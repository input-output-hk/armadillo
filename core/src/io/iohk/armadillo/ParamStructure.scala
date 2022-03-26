package io.iohk.armadillo

sealed trait ParamStructure
object ParamStructure {
  case object Either extends ParamStructure
  case object ByName extends ParamStructure
  case object ByPosition extends ParamStructure
}
