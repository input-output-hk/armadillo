package io.iohk.armadillo

import io.iohk.armadillo.Armadillo.{JsonRpcCodec, JsonRpcError, param}
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

  def jsonRpcEndpoint(
      str: MethodName
  ): JsonRpcEndpoint[Unit, Unit, Unit] =
    JsonRpcEndpoint(
      methodName = str,
      input = JsonRpcInput.emptyInput,
      output = JsonRpcOutput.emptyOutput(JsonRpcOutput.emptyOutputCodec()),
      error = JsonRpcErrorOutput.emptyErrorOutput(JsonRpcErrorOutput.emptyErrorCodec())
    )
  def param[T: JsonRpcCodec](name: String): JsonRpcIO[T] = JsonRpcIO.Single(implicitly[JsonRpcCodec[T]], Info.empty[T], name)

  case class JsonRpcRequest[Raw](jsonrpc: String, method: String, params: Raw, id: Int)

  case class JsonRpcResponse[Raw](jsonrpc: String, result: Raw, id: Int)

  case class JsonRpcErrorResponse[Raw](jsonrpc: String, error: Raw, id: Int)

  case class JsonRpcError[Raw](code: Int, message: String, data: Raw)

  object JsonRpcError {
    implicit def schema[Raw: Schema]: Schema[JsonRpcError[Raw]] = Schema.derived[JsonRpcError[Raw]]
  }
}

case class MethodName(value: String) extends AnyVal
case class JsonRpcEndpoint[I, E, O](
    methodName: MethodName,
    input: JsonRpcInput[I],
    output: JsonRpcOutput[O],
    error: JsonRpcErrorOutput[E]
) {
  def in[J](i: JsonRpcInput[J]): JsonRpcEndpoint[J, E, O] =
    copy(input = i)

  def serverLogic[F[_]](f: I => F[Either[List[JsonRpcError[E]], O]]): JsonRpcServerEndpoint.Full[I, E, O, F] = {
    import sttp.monad.syntax.*
    JsonRpcServerEndpoint[I, E, O, F](this, implicit m => i => f(i).map(x => x))
  }

  def out[P](name: String)(implicit jsonRpcCodec: JsonRpcCodec[P]): JsonRpcEndpoint[I, E, P] =
    copy(output = param[P](name))

  def errorOut[F](name: String)(implicit jsonRpcCodec: JsonRpcCodec[List[JsonRpcError[F]]]): JsonRpcEndpoint[I, F, O] =
    copy(error = JsonRpcErrorOutput.Single(jsonRpcCodec, Info.empty, name))
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

sealed trait JsonRpcErrorOutput[T]

object JsonRpcErrorOutput {
  def emptyErrorCodec(
      s: Schema[List[JsonRpcError[Unit]]] = Schema[JsonRpcError[Unit]](SchemaType.SString()).asIterable[List]
  ): JsonRpcCodec[List[JsonRpcError[Unit]]] =
    new JsonRpcCodec[List[JsonRpcError[Unit]]] {
      override type L = Nothing

      override def schema: Schema[List[JsonRpcError[Unit]]] = s

      override def decode(l: Nothing): DecodeResult[List[JsonRpcError[Unit]]] = DecodeResult.Value(List.empty)

      override def encode(h: List[JsonRpcError[Unit]]): Nothing = throw new RuntimeException("should not be called")

    }

  def emptyErrorOutput(emptyCodec: JsonRpcCodec[List[JsonRpcError[Unit]]]): JsonRpcErrorOutput[Unit] =
    EmptyError(emptyCodec, Info.empty)

  case class EmptyError[T](codec: JsonRpcCodec[List[JsonRpcError[Unit]]], info: Info[T]) extends JsonRpcErrorOutput[T]

  case class Single[T](codec: JsonRpcCodec[List[JsonRpcError[T]]], info: Info[T], name: String) extends JsonRpcErrorOutput[T]
}

sealed trait JsonRpcOutput[T]

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

  case class Empty[T](codec: JsonRpcCodec[Unit], info: Info[T]) extends JsonRpcIO[T]

  case class Single[T](codec: JsonRpcCodec[T], info: Info[T], name: String) extends JsonRpcIO[T]
}

abstract class JsonRpcServerEndpoint[F[_]] {
  type INPUT
  type ERROR_OUTPUT
  type OUTPUT

  def endpoint: JsonRpcEndpoint[INPUT, ERROR_OUTPUT, OUTPUT]
  def logic: MonadError[F] => INPUT => F[Either[List[JsonRpcError[ERROR_OUTPUT]], OUTPUT]]
}
object JsonRpcServerEndpoint {

  /** The full type of a server endpoint, capturing the types of all input/output parameters. Most of the time, the simpler
    * `JsonRpcServerEndpoint[R, F]` can be used instead.
    */
  type Full[_INPUT, _ERROR_OUTPUT, _OUTPUT, F[_]] = JsonRpcServerEndpoint[F] {
    type INPUT = _INPUT
    type ERROR_OUTPUT = _ERROR_OUTPUT
    type OUTPUT = _OUTPUT
  }

  def apply[INPUT, ERROR_OUTPUT, OUTPUT, F[_]](
      endpoint: JsonRpcEndpoint[INPUT, ERROR_OUTPUT, OUTPUT],
      logic: MonadError[F] => INPUT => F[Either[List[JsonRpcError[ERROR_OUTPUT]], OUTPUT]]
  ): JsonRpcServerEndpoint.Full[INPUT, ERROR_OUTPUT, OUTPUT, F] = {
    type _INPUT = INPUT
    type _ERROR_OUTPUT = ERROR_OUTPUT
    type _OUTPUT = OUTPUT
    val e = endpoint
    val f = logic
    new JsonRpcServerEndpoint[F] {
      override type INPUT = _INPUT
      override type ERROR_OUTPUT = _ERROR_OUTPUT
      override type OUTPUT = _OUTPUT

      override def endpoint: JsonRpcEndpoint[INPUT, ERROR_OUTPUT, OUTPUT] = e

      override def logic: MonadError[F] => INPUT => F[Either[List[JsonRpcError[ERROR_OUTPUT]], OUTPUT]] = f
    }
  }
}
