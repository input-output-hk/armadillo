package io.iohk.armadillo

import io.iohk.armadillo.Armadillo.{JsonRpcCodec, JsonRpcError, param}
import sttp.monad.MonadError
import sttp.tapir.EndpointIO.Info
import sttp.tapir.SchemaType.SchemaWithValue
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
  )(implicit _codec: JsonRpcCodec[JsonRpcErrorNoData]): JsonRpcEndpoint[Unit, Unit, Unit] =
    JsonRpcEndpoint(
      methodName = str,
      input = JsonRpcInput.emptyInput,
      output = JsonRpcOutput.emptyOutput(JsonRpcOutput.emptyOutputCodec()),
      error = JsonRpcErrorOutput.Single(noDataError)
    )

  def param[T: JsonRpcCodec](name: String): JsonRpcIO[T] = JsonRpcIO.Single(implicitly[JsonRpcCodec[T]], Info.empty[T], name)

  def error[T](implicit _codec: JsonRpcCodec[JsonRpcErrorWithData[T]]): JsonRpcErrorPart[T] =
    new JsonRpcErrorPart[T] {
      override type DATA = JsonRpcErrorWithData[T]

      override def codec: JsonRpcCodec[JsonRpcErrorWithData[T]] = _codec

      override def info: Info[T] = Info.empty[T]
    }

  def noDataError(implicit _codec: JsonRpcCodec[JsonRpcErrorNoData]): JsonRpcErrorPart[Unit] = {
    new JsonRpcErrorPart[Unit] {
      override type DATA = JsonRpcErrorNoData

      override def codec: JsonRpcCodec[JsonRpcErrorNoData] = _codec

      override def info: Info[Unit] = Info.empty
    }
  }

  case class JsonRpcRequest[Raw](jsonrpc: String, method: String, params: Raw, id: JsonRpcId)
  object JsonRpcRequest {
    implicit def schema[Raw: Schema]: Schema[JsonRpcRequest[Raw]] = Schema.derived[JsonRpcRequest[Raw]]
  }

  sealed trait JsonRpcResponse[Raw] {
    def jsonrpc: String
    def id: JsonRpcId
  }

  sealed trait JsonRpcId
  object JsonRpcId {
    case class IntId(value: Int) extends JsonRpcId
    case class StringId(value: String) extends JsonRpcId
    case object NullId extends JsonRpcId

    implicit val schema: Schema[JsonRpcId] = {
      val s1 = Schema.schemaForInt.asOption
      val s2 = Schema.schemaForString.asOption
      Schema[JsonRpcId](
        SchemaType.SCoproduct(List(s1, s2), None) {
          case IntId(v)    => Some(SchemaWithValue(s1, Some(v)))
          case StringId(v) => Some(SchemaWithValue(s2, Some(v)))
          case NullId      => Some(SchemaWithValue(s1, None))
        },
        for {
          na <- s1.name
          nb <- s2.name
        } yield Schema.SName("JsonRpcId", List(na.show, nb.show))
      )
    }
  }

  case class JsonRpcSuccessResponse[Raw](jsonrpc: String, result: Raw, id: JsonRpcId) extends JsonRpcResponse[Raw]
  object JsonRpcSuccessResponse {
    implicit def schema[Raw: Schema]: Schema[JsonRpcSuccessResponse[Raw]] = Schema.derived[JsonRpcSuccessResponse[Raw]]
  }

  case class JsonRpcErrorResponse[Raw](jsonrpc: String, error: Raw, id: JsonRpcId) extends JsonRpcResponse[Raw]
  object JsonRpcErrorResponse {
    implicit def schema[Raw: Schema]: Schema[JsonRpcErrorResponse[Raw]] = Schema.derived[JsonRpcErrorResponse[Raw]]
  }

  sealed trait JsonRpcError[Data]

  case class JsonRpcErrorWithData[Data](code: Int, message: String, data: Data) extends JsonRpcError[Data]
  object JsonRpcErrorWithData {
    implicit def schema[Data: Schema]: Schema[JsonRpcErrorWithData[Data]] = Schema.derived[JsonRpcErrorWithData[Data]]
  }

  case class JsonRpcErrorNoData(code: Int, message: String) extends JsonRpcError[Unit]
  object JsonRpcErrorNoData {
    implicit val schema: Schema[JsonRpcErrorNoData] = Schema.derived[JsonRpcErrorNoData]
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

  def serverLogic[F[_]](f: I => F[Either[JsonRpcError[E], O]]): JsonRpcServerEndpoint.Full[I, E, O, F] = {
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

abstract class JsonRpcServerEndpoint[F[_]] {
  type INPUT
  type ERROR_OUTPUT
  type OUTPUT

  def endpoint: JsonRpcEndpoint[INPUT, ERROR_OUTPUT, OUTPUT]
  def logic: MonadError[F] => INPUT => F[Either[JsonRpcError[ERROR_OUTPUT], OUTPUT]]
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
      logic: MonadError[F] => INPUT => F[Either[JsonRpcError[ERROR_OUTPUT], OUTPUT]]
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

      override def logic: MonadError[F] => INPUT => F[Either[JsonRpcError[ERROR_OUTPUT], OUTPUT]] = f
    }
  }
}
