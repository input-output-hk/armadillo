package io.iohk.armadillo.tapir

import io.iohk.armadillo.{JsonRpcIO, JsonRpcInput}

object Utils {
  implicit class RichEndpointInput[I](input: JsonRpcInput[I]) {
    def traverseInputs[T](handle: PartialFunction[JsonRpcInput[_], Vector[T]]): Vector[T] =
      input match {
        case i: JsonRpcInput[_] if handle.isDefinedAt(i) => handle(i)
        case JsonRpcInput.Pair(left, right, _, _)        => left.traverseInputs(handle) ++ right.traverseInputs(handle)
        case _                                           => Vector.empty
      }

    def asVectorOfBasicInputs: Vector[JsonRpcIO.Single[_]] =
      traverseInputs { case b: JsonRpcIO.Single[_] =>
        Vector(b)
      }
  }
}
