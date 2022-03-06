package io.iohk.armadillo

import io.iohk.armadillo.Armadillo.JsonRpcCodec
import sttp.monad.MonadError
import sttp.tapir.EndpointIO.Info
import sttp.tapir.internal.{CombineParams, SplitParams, mkCombine, mkSplit}
import sttp.tapir.typelevel.ParamConcat
import sttp.tapir.{DecodeResult, EndpointIO, Schema, SchemaType}

object Armadillo {

  trait JsonRpcCodec[H] {
    type L
    def decode(l: L): DecodeResult[H]
    def encode(h: H): L
    def schema: Schema[H]
  }

  def jsonRpcEndpoint(str: MethodName): JsonRpcEndpoint[Unit, Unit, Unit] =
    JsonRpcEndpoint(
      methodName = str,
      input = JsonRpcInput.emptyInput,
      output = JsonRpcOutput.emptyOutput,
      error = JsonRpcOutput.emptyOutput
    )
  def jsonRpcBody[T: JsonRpcCodec](name: String): JsonRpcIO[T] = JsonRpcIO.Single(implicitly[JsonRpcCodec[T]], Info.empty[T], name)

  case class JsonRpcRequest[Raw](jsonrpc: String, method: String, params: Raw, id: Int)

  case class JsonRpcResponse[Raw](jsonrpc: String, result: Raw, id: Int)
}

case class MethodName(value: String) extends AnyVal
case class JsonRpcEndpoint[I, E, O](methodName: MethodName, input: JsonRpcInput[I], output: JsonRpcOutput[O], error: JsonRpcOutput[E]) {
  def in[J](i: JsonRpcInput[J]): JsonRpcEndpoint[J, E, O] =
    copy(input = i)

  def out[P](i: JsonRpcOutput[P]): JsonRpcEndpoint[I, E, P] =
    copy(output = i)

  def errorOut[F](o: JsonRpcOutput[F]): JsonRpcEndpoint[I, F, O] =
    copy(error = o)

  def serverLogic[F[_]](f: I => F[Either[E, O]]): JsonRpcServerEndpoint[F] = {
    import sttp.monad.syntax.*
    JsonRpcServerEndpoint[I, E, O, F](this, implicit m => i => f(i).map(x => x))
  }
}

sealed trait JsonRpcIO[T] extends JsonRpcInput[T] with JsonRpcOutput[T]

sealed trait JsonRpcInput[T] {
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
      extends JsonRpcInput[TU]

}

sealed trait JsonRpcOutput[T]

object JsonRpcOutput {
  def emptyOutputCodec(s: Schema[Unit] = Schema[Unit](SchemaType.SString())): JsonRpcCodec[Unit] = new JsonRpcCodec[Unit] {
    override type L = Unit

    override def schema: Schema[Unit] = s

    override def decode(l: Unit): DecodeResult[Unit] = throw new RuntimeException("should not be called")

    override def encode(h: Unit): Unit = null
  }
  val emptyOutput: JsonRpcOutput[Unit] = JsonRpcIO.Empty(emptyOutputCodec(), EndpointIO.Info.empty)

}

object JsonRpcIO {

  case class Empty[T](codec: JsonRpcCodec[Unit], info: Info[T]) extends JsonRpcIO[T]

  case class Single[T](codec: JsonRpcCodec[T], info: Info[T], name: String) extends JsonRpcIO[T]
}

abstract class JsonRpcServerEndpoint[F[_]] {
  type I
  type E
  type O

  def endpoint: JsonRpcEndpoint[I, E, O]
  def logic: MonadError[F] => I => F[Either[E, O]]
}
object JsonRpcServerEndpoint {
  def apply[I, E, O, F[_]](
      endpoint: JsonRpcEndpoint[I, E, O],
      function: MonadError[F] => I => F[Either[E, O]]
  ): JsonRpcServerEndpoint[F] = {
    type _I = I
    type _E = E
    type _O = O
    val e = endpoint
    val f = function
    new JsonRpcServerEndpoint[F] {
      override type I = _I
      override type E = _E
      override type O = _O

      override def endpoint: JsonRpcEndpoint[I, E, O] = e

      override def logic: MonadError[F] => I => F[Either[E, O]] = f
    }
  }
}
