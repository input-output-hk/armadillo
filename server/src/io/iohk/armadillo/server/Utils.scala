package io.iohk.armadillo.server

import io.iohk.armadillo.{JsonRpcIO, JsonRpcInput}
import sttp.monad.MonadError
import sttp.monad.syntax._
import sttp.tapir.DecodeResult

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

  implicit class RichDecodeResult[T](decodeResult: DecodeResult[T]) {
    def orElse(other: => DecodeResult[T]): DecodeResult[T] = {
      decodeResult match {
        case firstFailure: DecodeResult.Failure =>
          other match {
            case secondFailure: DecodeResult.Failure => DecodeResult.Multiple(Seq(firstFailure, secondFailure))
            case success: DecodeResult.Value[T]      => success
          }
        case success: DecodeResult.Value[T] => success
      }
    }

    def fold[R](success: T => R, error: DecodeResult.Failure => R): R = {
      decodeResult match {
        case failure: DecodeResult.Failure => error(failure)
        case DecodeResult.Value(v)         => success(v)
      }
    }

    def map2[B, C](fb: DecodeResult[B])(f: (T, B) => C): DecodeResult[C] = {
      decodeResult.flatMap { a =>
        fb.map(b => f(a, b))
      }
    }
  }

  implicit class RichMonadErrorOps[F[_]: MonadError, A](fa: F[A]) {
    def map2[B, C](fb: F[B])(f: (A, B) => C): F[C] = {
      fa.flatMap { a =>
        fb.map(b => f(a, b))
      }
    }
  }
}
