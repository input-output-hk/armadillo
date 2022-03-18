package io.iohk.armadillo.tapir

import io.iohk.armadillo
import io.iohk.armadillo.{JsonRpcErrorOutput, JsonRpcErrorPart, JsonRpcIO, JsonRpcInput}

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

  implicit class RichEndpointErrorPart[E](error: JsonRpcErrorPart[E]) {
    def traverseErrorOutputs[T](handle: PartialFunction[JsonRpcErrorPart[_], Vector[T]]): Vector[T] = {
      error match {
        case i: JsonRpcErrorPart[_] if handle.isDefinedAt(i) => handle(i)
        case JsonRpcErrorPart.Pair(left, right, _, _)        => left.traverseErrorOutputs(handle) ++ right.traverseErrorOutputs(handle)
        case _                                               => Vector.empty
      }
    }

    def asVectorOfBasicErrorParts: Vector[JsonRpcErrorPart.Single[_]] =
      traverseErrorOutputs { case b: JsonRpcErrorPart.Single[_] =>
        Vector(b)
      }
  }
}
