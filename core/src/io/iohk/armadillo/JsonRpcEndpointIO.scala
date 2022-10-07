package io.iohk.armadillo

import sttp.tapir.typelevel.ParamConcat
import sttp.tapir.{Mapping, Validator}

case class JsonRpcEndpoint[I, E, O](
    methodName: MethodName,
    paramStructure: ParamStructure,
    input: JsonRpcInput[I],
    output: JsonRpcOutput[O],
    error: JsonRpcErrorOutput[E],
    info: JsonRpcEndpointInfo
) {
  def in[J](i: JsonRpcInput[J]): JsonRpcEndpoint[J, E, O] =
    copy(input = i)

  def serverLogic[F[_]](f: I => F[Either[E, O]]): JsonRpcServerEndpoint.Full[I, E, O, F] = {
    import sttp.monad.syntax._
    JsonRpcServerEndpoint[I, E, O, F](this, implicit m => i => f(i).map(x => x))
  }

  def out[P](name: String)(implicit jsonRpcCodec: JsonRpcCodec[P]): JsonRpcEndpoint[I, E, P] =
    copy(output = result[P](name))

  def out[P](o: JsonRpcOutput[P]): JsonRpcEndpoint[I, E, P] = copy(output = o)

  def errorOut[F](error: JsonRpcErrorOutput[F]): JsonRpcEndpoint[I, F, O] =
    copy(error = error)

  def showDetail: String =
    s"JsonRpcEndpoint(method: $methodName, in: ${input.show}, errout: ${error.show}, out: ${output.show})"

  def withInfo(info: JsonRpcEndpointInfo): JsonRpcEndpoint[I, E, O] = copy(info = info)

  def summary(s: String): JsonRpcEndpoint[I, E, O] = withInfo(info.summary(s))
  def description(d: String): JsonRpcEndpoint[I, E, O] = withInfo(info.description(d))
  def deprecated(): JsonRpcEndpoint[I, E, O] = withInfo(info.deprecated(true))
  def tag(t: JsonRpcEndpointTag): JsonRpcEndpoint[I, E, O] = withInfo(info.tag(t))
  def tags(ts: List[JsonRpcEndpointTag]): JsonRpcEndpoint[I, E, O] = withInfo(info.tags(ts))
  def externalDocs(ed: JsonRpcEndpointExternalDocs): JsonRpcEndpoint[I, E, O] = withInfo(info.externalDocs(ed))
}

object JsonRpcEndpoint {
  implicit val ordering: Ordering[JsonRpcEndpoint[_, _, _]] = Ordering.by(_.methodName.asString)
}

case class JsonRpcEndpointInfo(
    summary: Option[String],
    description: Option[String],
    tags: Vector[JsonRpcEndpointTag],
    deprecated: Boolean,
    externalDocs: Option[JsonRpcEndpointExternalDocs]
) {
  def summary(s: String): JsonRpcEndpointInfo = copy(summary = Some(s))
  def description(d: String): JsonRpcEndpointInfo = copy(description = Some(d))
  def deprecated(d: Boolean): JsonRpcEndpointInfo = copy(deprecated = d)
  def tags(ts: List[JsonRpcEndpointTag]): JsonRpcEndpointInfo = copy(tags = tags ++ ts)
  def tag(t: JsonRpcEndpointTag): JsonRpcEndpointInfo = copy(tags = tags :+ t)
  def externalDocs(ed: JsonRpcEndpointExternalDocs): JsonRpcEndpointInfo = copy(externalDocs = Some(ed))
}

object JsonRpcEndpointInfo {
  val Empty: JsonRpcEndpointInfo = JsonRpcEndpointInfo(None, None, Vector.empty, deprecated = false, None)
}

case class JsonRpcEndpointTag(
    name: String,
    summary: Option[String] = None,
    description: Option[String] = None,
    externalDocs: Option[JsonRpcEndpointExternalDocs] = None
) {
  def summary(s: String): JsonRpcEndpointTag = copy(summary = Some(s))
  def description(d: String): JsonRpcEndpointTag = copy(description = Some(d))
  def externalDocs(ed: JsonRpcEndpointExternalDocs): JsonRpcEndpointTag = copy(externalDocs = Some(ed))
}

case class JsonRpcEndpointExternalDocs(url: String, description: Option[String] = None) {
  def description(d: String): JsonRpcEndpointExternalDocs = copy(description = Some(d))
}

sealed trait JsonRpcEndpointTransput[T] {
  private[armadillo] type ThisType[A]

  def show: String
}

object JsonRpcEndpointTransput {
  sealed trait Basic[T] extends JsonRpcEndpointTransput[T] {
    def summary(s: String): ThisType[T] = withInfo(info.summary(s))
    def description(d: String): ThisType[T] = withInfo(info.description(d))

    def withInfo(value: JsonRpcIoInfo): ThisType[T]
    def info: JsonRpcIoInfo
  }
}

sealed trait JsonRpcIO[T] extends JsonRpcInput[T] with JsonRpcOutput[T] with JsonRpcEndpointTransput[T]

sealed trait JsonRpcInput[T] extends JsonRpcEndpointTransput[T] {
  private[armadillo] type ThisType[X] <: JsonRpcInput[X]

  def and[U, TU](param: JsonRpcInput[U])(implicit concat: ParamConcat.Aux[T, U, TU]): JsonRpcInput[TU] = {
    JsonRpcInput.Pair(this, param)
  }
}

object JsonRpcInput {
  val emptyInput: JsonRpcInput[Unit] = JsonRpcIO.Empty()

  sealed trait Basic[T] extends JsonRpcInput[T] with JsonRpcEndpointTransput.Basic[T] {
    override private[armadillo] type ThisType[X] <: JsonRpcInput.Basic[X]

    def deprecated(): ThisType[T] = withInfo(info.deprecated(true))

    def validate(validator: Validator[T]): ThisType[T]
  }

  case class Pair[T, U, TU](left: JsonRpcInput[T], right: JsonRpcInput[U]) extends JsonRpcInput[TU] {
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

sealed trait JsonRpcErrorOutput[T] extends JsonRpcEndpointTransput[T]

object JsonRpcErrorOutput {
  def emptyOutput: JsonRpcErrorOutput[Unit] = JsonRpcErrorOutput.Empty()
  def fixed(code: Int, message: String): JsonRpcErrorOutput[Unit] = JsonRpcErrorOutput.Fixed(code, message)

  sealed trait Atom[T] extends JsonRpcErrorOutput[T] {
    type DATA = T
  }

  case class SingleNoData() extends Atom[JsonRpcError.NoData] {
    override def show: String = s"singleNoData"
  }

  case class SingleWithData[T](codec: JsonRpcCodec[JsonRpcError[T]]) extends Atom[JsonRpcError[T]] {
    override def show: String = s"singleWithData"
  }

  case class Fixed[T](
      code: Int,
      message: String
  ) extends Atom[T] {
    override def show: String = s"FixedJsonRpcError(message: $message, code: $code)"
  }

  case class FixedWithData[T](
      code: Int,
      message: String,
      codec: JsonRpcCodec[T]
  ) extends Atom[T] {
    override def show: String = s"FixedJsonRpcErrorWithData(message: $message, code: $code)"
  }

  case class Empty() extends Atom[Unit] {
    override def show: String = "-"
  }

  case class OneOfVariant[O] private[armadillo] (output: JsonRpcErrorOutput[O], appliesTo: Any => Boolean)

  case class OneOf[O, T](variants: List[OneOfVariant[_ <: O]], mapping: Mapping[O, T]) extends JsonRpcErrorOutput[T] {
    override def show: String = s"OneOfError(${variants.map(_.output.show).mkString(",")})"
  }
}

sealed trait JsonRpcOutput[T] extends JsonRpcEndpointTransput[T]

object JsonRpcOutput {
  def emptyOutput: JsonRpcOutput[Unit] = JsonRpcIO.Empty()

  sealed trait Basic[T] extends JsonRpcOutput[T] with JsonRpcEndpointTransput.Basic[T] {
    override private[armadillo] type ThisType[X] <: JsonRpcOutput.Basic[X]
  }
}

object JsonRpcIO {

  case class Empty[T]() extends JsonRpcIO[T] {
    override def show: String = "-"
  }

  case class Single[T](codec: JsonRpcCodec[T], info: JsonRpcIoInfo, name: String)
      extends JsonRpcIO[T]
      with JsonRpcInput.Basic[T]
      with JsonRpcOutput.Basic[T] {
    override def show: String = s"single($name)"
    override private[armadillo] type ThisType[X] = Single[X]
    override def withInfo(info: JsonRpcIoInfo): Single[T] = copy(info = info)

    override def validate(validator: Validator[T]): Single[T] = copy(codec = codec.withValidator(validator))

    def optional(implicit codec: JsonRpcCodec[Option[T]]): Single[Option[T]] = new Single[Option[T]](codec, info, name)
  }
}

case class JsonRpcIoInfo(description: Option[String], summary: Option[String], deprecated: Option[Boolean] = None) {
  def description(d: String): JsonRpcIoInfo = copy(description = Some(d))
  def summary(s: String): JsonRpcIoInfo = copy(summary = Some(s))
  def deprecated(d: Boolean): JsonRpcIoInfo = copy(deprecated = Some(d))
}

object JsonRpcIoInfo {
  val Empty: JsonRpcIoInfo = JsonRpcIoInfo(None, None)
}
