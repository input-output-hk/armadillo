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

  def error[T](implicit _codec: JsonRpcCodec[JsonRpcError[T]]): JsonRpcErrorPart[JsonRpcError[T]] =
    new JsonRpcErrorPart.Single[JsonRpcError[T]] {
      override type DATA = JsonRpcError[T]

      override def codec: JsonRpcCodec[JsonRpcError[T]] = _codec

      override def info: Info[JsonRpcError[T]] = Info.empty[JsonRpcError[T]]
    }

  def noDataError(implicit _codec: JsonRpcCodec[JsonRpcNoDataError]): JsonRpcErrorPart[JsonRpcNoDataError] = {
    new JsonRpcErrorPart.Single[JsonRpcNoDataError] {
      override type DATA = JsonRpcNoDataError

      override def codec: JsonRpcCodec[JsonRpcNoDataError] = _codec

      override def info: Info[JsonRpcNoDataError] = Info.empty[JsonRpcNoDataError]
    }
  }

  case class JsonRpcRequest[Raw](jsonrpc: String, method: String, params: Raw, id: Int)
  object JsonRpcRequest {
    implicit def schema[Raw: Schema]: Schema[JsonRpcRequest[Raw]] = Schema.derived[JsonRpcRequest[Raw]]
  }

  sealed trait JsonRpcResponse[Raw] {
    def jsonrpc: String
    def id: Int
  }

  case class JsonRpcSuccessResponse[Raw](jsonrpc: String, result: Raw, id: Int) extends JsonRpcResponse[Raw]
  object JsonRpcSuccessResponse {
    implicit def schema[Raw: Schema]: Schema[JsonRpcSuccessResponse[Raw]] = Schema.derived[JsonRpcSuccessResponse[Raw]]
  }

  case class JsonRpcErrorResponse[Raw](jsonrpc: String, error: Raw, id: Int) extends JsonRpcResponse[Raw]
  object JsonRpcErrorResponse {
    implicit def schema[Raw: Schema]: Schema[JsonRpcErrorResponse[Raw]] = Schema.derived[JsonRpcErrorResponse[Raw]]
  }

  case class JsonRpcError[Data](code: Int, message: String, data: Data)
  object JsonRpcError {
    implicit def schema[Data: Schema]: Schema[JsonRpcError[Data]] = Schema.derived[JsonRpcError[Data]]
  }

  case class JsonRpcNoDataError(code: Int, message: String)
  object JsonRpcNoDataError {
    implicit val schema: Schema[JsonRpcNoDataError] = Schema.derived[JsonRpcNoDataError]
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

  def serverLogic[F[_]](f: I => F[Either[E, O]]): JsonRpcServerEndpoint.Full[I, E, O, F] = {
    import sttp.monad.syntax.*
    JsonRpcServerEndpoint[I, E, O, F](this, implicit m => i => f(i).map(x => x))
  }

  def out[P](name: String)(implicit jsonRpcCodec: JsonRpcCodec[P]): JsonRpcEndpoint[I, E, P] =
    copy(output = param[P](name))

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

sealed trait JsonRpcErrorPart[T] extends JsonRpcEndpointTransput[T] {
  def and[U, TU](param: JsonRpcErrorPart[U])(implicit concat: ParamConcat.Aux[T, U, TU]): JsonRpcErrorPart[TU] = {
    JsonRpcErrorPart.Pair(this, param, mkCombine(concat), mkSplit(concat))
  }
}

object JsonRpcErrorPart {
  trait Single[T] extends JsonRpcErrorPart[T] {
    type DATA
    def codec: JsonRpcCodec[DATA]
    def info: Info[T]
    override def show: String = s"single"
  }

  case class Pair[T, U, TU](left: JsonRpcErrorPart[T], right: JsonRpcErrorPart[U], combine: CombineParams, split: SplitParams)
      extends JsonRpcErrorPart[TU] {
    override def show: String = {
      def flattenedPairs(et: JsonRpcErrorPart[_]): Vector[JsonRpcErrorPart[_]] =
        et match {
          case p: Pair[_, _, _] => flattenedPairs(p.left) ++ flattenedPairs(p.right)
          case other            => Vector(other)
        }
      flattenedPairs(this).map(_.show).mkString("[", ",", "]")
    }
  }
}

sealed trait JsonRpcErrorOutput[T] extends JsonRpcEndpointTransput[T]

object JsonRpcErrorOutput {
  def emptyErrorCodec(
      s: Schema[Unit] = Schema[Unit](SchemaType.SString())
  ): JsonRpcCodec[Unit] =
    new JsonRpcCodec[Unit] {
      override type L = Nothing

      override def schema: Schema[Unit] = s

      override def decode(l: Nothing): DecodeResult[Unit] = DecodeResult.Value(())

      override def encode(h: Unit): Nothing = throw new RuntimeException("should not be called")

    }

  def emptyErrorOutput(emptyCodec: JsonRpcCodec[Unit]): JsonRpcErrorOutput[Unit] =
    EmptyError(emptyCodec, Info.empty)

  case class EmptyError[T](codec: JsonRpcCodec[Unit], info: Info[T]) extends JsonRpcErrorOutput[T] {
    override def show: String = "-"
  }

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

abstract class JsonRpcServerEndpoint[F[_]] {
  type INPUT
  type ERROR_OUTPUT
  type OUTPUT

  def endpoint: JsonRpcEndpoint[INPUT, ERROR_OUTPUT, OUTPUT]
  def logic: MonadError[F] => INPUT => F[Either[ERROR_OUTPUT, OUTPUT]]
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
      logic: MonadError[F] => INPUT => F[Either[ERROR_OUTPUT, OUTPUT]]
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

      override def logic: MonadError[F] => INPUT => F[Either[ERROR_OUTPUT, OUTPUT]] = f
    }
  }
}
