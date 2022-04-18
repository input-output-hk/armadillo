package io.iohk.armadillo

import sttp.tapir.EndpointIO.Info
import sttp.tapir.{DecodeResult, EndpointIO, Schema, SchemaType}
import sttp.tapir.internal.{CombineParams, SplitParams, mkCombine, mkSplit}
import sttp.tapir.typelevel.ParamConcat

case class JsonRpcEndpoint[I, E, O](
    methodName: MethodName,
    paramStructure: ParamStructure,
    input: JsonRpcInput[I],
    output: JsonRpcOutput[O],
    error: JsonRpcErrorOutput[E]
) {
  def in[J](i: JsonRpcInput[J]): JsonRpcEndpoint[J, E, O] =
    copy(input = i)

  def serverLogic[F[_]](f: I => F[Either[JsonRpcError[E], O]]): JsonRpcServerEndpoint.Full[I, E, O, F] = {
    import sttp.monad.syntax.*
    JsonRpcServerEndpoint[I, E, O, F](this, implicit m => i => f(i).map(x => x))
  }

  def out[P](name: String)(implicit jsonRpcCodec: JsonRpcCodec[P]): JsonRpcEndpoint[I, E, P] =
    copy(output = JsonRpcIO.Single(implicitly[JsonRpcCodec[P]], Info.empty[P], name))

  def errorOut[F](error: JsonRpcErrorPart[F]): JsonRpcEndpoint[I, F, O] =
    copy(error = JsonRpcErrorOutput.Single(error))

  def showDetail: String =
    s"JsonRpcEndpoint(method: $methodName, in: ${input.show}, errout: ${error.show}, out: ${output.show})"
}

sealed trait JsonRpcEndpointTransput[T] {
  def show: String
}

sealed trait JsonRpcIO[T] extends JsonRpcInput[T] with JsonRpcOutput[T]

sealed trait JsonRpcInput[T] extends JsonRpcEndpointTransput[T] {
  def and[U, TU](param: JsonRpcInput[U])(implicit concat: ParamConcat.Aux[T, U, TU]): JsonRpcInput[TU] = {
    JsonRpcInput.Pair(this, param, mkCombine(concat), mkSplit(concat))
  }
}

object JsonRpcInput {
  def idPlain(s: Schema[Unit] = Schema[Unit](SchemaType.SString())): JsonRpcCodec[Unit] = new JsonRpcCodec[Unit] {
    override type L = Nothing

    override def encode(h: Unit): L = throw new RuntimeException("should not be called")

    override def schema: Schema[Unit] = s

    override def decode(l: L): DecodeResult[Unit] = DecodeResult.Value(())
  }
  val emptyInput: JsonRpcInput[Unit] = JsonRpcIO.Empty(idPlain(), EndpointIO.Info.empty)

  case class Pair[T, U, TU](left: JsonRpcInput[T], right: JsonRpcInput[U], combine: CombineParams, split: SplitParams)
      extends JsonRpcInput[TU] {
    override def show: String = {
      def flattenedPairs(et: JsonRpcInput[_]): Vector[JsonRpcInput[_]] =
        et match {
          case p: Pair[_, _, _] => flattenedPairs(p.left) ++ flattenedPairs(p.right)
          case other            => Vector(other)
        }
      flattenedPairs(this).map(_.show).mkString("[", ",", "]")
    }
  }
}

trait JsonRpcErrorPart[T] extends JsonRpcEndpointTransput[T] {
  type DATA
  def codec: JsonRpcCodec[DATA]
  def info: Info[T]
  override def show: String = s"single"
}

sealed trait JsonRpcErrorOutput[T] extends JsonRpcEndpointTransput[T]

object JsonRpcErrorOutput {
  case class Single[T](error: JsonRpcErrorPart[T]) extends JsonRpcErrorOutput[T] {
    override def show: String = s"single(${error.show})"
  }
}

sealed trait JsonRpcOutput[T] extends JsonRpcEndpointTransput[T]

object JsonRpcOutput {
  def emptyOutputCodec(s: Schema[Unit] = Schema[Unit](SchemaType.SString())): JsonRpcCodec[Unit] = new JsonRpcCodec[Unit] {
    override type L = Nothing

    override def schema: Schema[Unit] = s

    override def decode(l: Nothing): DecodeResult[Unit] = DecodeResult.Value(())

    override def encode(h: Unit): Nothing = throw new RuntimeException("should not be called")
  }
  def emptyOutput(emptyCodec: JsonRpcCodec[Unit]): JsonRpcOutput[Unit] = JsonRpcIO.Empty(emptyCodec, Info.empty)

}

object JsonRpcIO {

  case class Empty[T](codec: JsonRpcCodec[Unit], info: Info[T]) extends JsonRpcIO[T] {
    override def show: String = "-"
  }

  case class Single[T](codec: JsonRpcCodec[T], info: Info[T], name: String) extends JsonRpcIO[T] {
    override def show: String = s"single($name)"
  }
}
