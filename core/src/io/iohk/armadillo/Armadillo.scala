package io.iohk.armadillo

import io.iohk.armadillo.Armadillo.JsonRpcCodec
import io.iohk.armadillo.JsonRpcIO.idPlain
import sttp.monad.MonadError
import sttp.tapir.Codec.{JsonCodec, id}
import sttp.tapir.CodecFormat.{Json, TextPlain}
import sttp.tapir.DecodeResult.Value
import sttp.tapir.EndpointIO.Info
import sttp.tapir.internal.{CombineParams, SplitParams, mkCombine, mkSplit}
import sttp.tapir.typelevel.ParamConcat
import sttp.tapir.{Codec, CodecFormat, DecodeResult, EndpointIO, Schema, SchemaType}

object Armadillo {

//  type JsonRpcCodec[L, H] = Codec[L, H, CodecFormat.Json]

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
  def jsonRpcBody[T: JsonCodec]: JsonRpcIO[T] = JsonRpcIO.Single(implicitly[JsonCodec[T]], Info.empty[T])

}

case class MethodName(value: String) extends AnyVal
case class JsonRpcEndpoint[I, E, O](methodName: MethodName, input: JsonRpcInput[I], output: JsonRpcOutput[O], error: JsonRpcOutput[E]) {
  def in[J, IJ](i: JsonRpcInput[J])(implicit concat: ParamConcat.Aux[I, J, IJ]): JsonRpcEndpoint[IJ, E, O] =
    copy(input = input.and(i))

  def out[P, OP](i: JsonRpcOutput[P])(implicit ts: ParamConcat.Aux[O, P, OP]): JsonRpcEndpoint[I, E, OP] =
    copy(output = output.and(i))

  def errorOut[F, EF](o: JsonRpcOutput[F])(implicit ts: ParamConcat.Aux[E, F, EF]): JsonRpcEndpoint[I, EF, O] =
    copy(error = error.and(o))

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
  def idPlain(s: Schema[Unit] = Schema[Unit](SchemaType.SString())): JsonCodec[Unit] = new Codec[String, Unit, CodecFormat.Json] {
    override def rawDecode(l: String): DecodeResult[Unit] = Value(())

    override def encode(h: Unit): String = ???

    override def schema: Schema[Unit] = s

    override def format: Json = CodecFormat.Json()
  }
  val emptyInput: JsonRpcInput[Unit] = JsonRpcIO.Empty(idPlain(), EndpointIO.Info.empty)
  case class Pair[T, U, TU](left: JsonRpcInput[T], right: JsonRpcInput[U], combine: CombineParams, split: SplitParams)
      extends JsonRpcInput[TU]

}

sealed trait JsonRpcOutput[T] {
  def and[J, IJ](other: JsonRpcOutput[J])(implicit concat: ParamConcat.Aux[T, J, IJ]): JsonRpcOutput[IJ] =
    JsonRpcOutput.Pair(this, other, mkCombine(concat), mkSplit(concat))
}

object JsonRpcOutput {
  def emptyOutputCodec(s: Schema[Unit] = Schema[Unit](SchemaType.SString())): JsonCodec[Unit] = new Codec[String, Unit, CodecFormat.Json] {

    override def format: CodecFormat.Json = CodecFormat.Json()

    override def rawDecode(l: String): DecodeResult[Unit] = Value(())

    override def encode(h: Unit): String = null

    override def schema: Schema[Unit] = s
  }

  val emptyOutput: JsonRpcOutput[Unit] = JsonRpcIO.Empty(emptyOutputCodec(), EndpointIO.Info.empty)
  case class Pair[T, U, TU](left: JsonRpcOutput[T], right: JsonRpcOutput[U], combine: CombineParams, split: SplitParams)
      extends JsonRpcOutput[TU]
}

object JsonRpcIO {

  case class Empty[T](codec: JsonCodec[Unit], info: Info[T]) extends JsonRpcIO[T]

  case class Single[T](codec: JsonCodec[T], info: Info[T]) extends JsonRpcIO[T]
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
