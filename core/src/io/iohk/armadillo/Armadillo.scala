package io.iohk.armadillo

import sttp.tapir.Mapping
import sttp.tapir.typelevel.ErasureSameAsType

import scala.reflect.ClassTag

trait Armadillo {

  def jsonRpcEndpoint(
      methodName: MethodName,
      paramStructure: ParamStructure = ParamStructure.Either
  ): JsonRpcEndpoint[Unit, Unit, Unit] =
    JsonRpcEndpoint(
      methodName = methodName,
      paramStructure = paramStructure,
      input = JsonRpcInput.emptyInput,
      output = JsonRpcOutput.emptyOutput,
      error = JsonRpcErrorOutput.emptyOutput,
      info = JsonRpcEndpointInfo.Empty
    )

  def param[T: JsonRpcCodec](name: String): JsonRpcInput.Basic[T] =
    JsonRpcIO.Single(implicitly[JsonRpcCodec[T]], JsonRpcIoInfo.Empty, name)

  def result[T: JsonRpcCodec](name: String): JsonRpcOutput.Basic[T] =
    JsonRpcIO.Single(implicitly[JsonRpcCodec[T]], JsonRpcIoInfo.Empty, name)

  def fixedError[T](code: Int, message: String): JsonRpcErrorOutput[T] =
    JsonRpcErrorOutput.Fixed[T](code, message)

  def fixedErrorWithData[T](code: Int, message: String)(implicit
      codec: JsonRpcCodec[T]
  ): JsonRpcErrorOutput[T] =
    JsonRpcErrorOutput.FixedWithData[T](code, message, codec)

  def error: JsonRpcErrorOutput[JsonRpcError.NoData] =
    JsonRpcErrorOutput.SingleNoData()

  def errorWithData[T](implicit codec: JsonRpcCodec[JsonRpcError[T]]): JsonRpcErrorOutput[JsonRpcError[T]] =
    JsonRpcErrorOutput.SingleWithData(codec)

  def oneOf[T](
      firstVariant: JsonRpcErrorOutput.OneOfVariant[_ <: T],
      otherVariants: JsonRpcErrorOutput.OneOfVariant[_ <: T]*
  ): JsonRpcErrorOutput.OneOf[T, T] =
    JsonRpcErrorOutput.OneOf[T, T](firstVariant +: otherVariants.toList, Mapping.id)

  def oneOfVariant[T: ClassTag: ErasureSameAsType](output: JsonRpcErrorOutput[T]): JsonRpcErrorOutput.OneOfVariant[T] =
    oneOfVariantClassMatcher(output, implicitly[ClassTag[T]].runtimeClass)

  /** Create a one-of-variant which uses `output` if the provided value (when interpreting as a server matches the `matcher` predicate.
    *
    * Should be used in [[oneOf]] output descriptions.
    */
  def oneOfVariantValueMatcher[T](output: JsonRpcErrorOutput[T])(
      matcher: PartialFunction[Any, Boolean]
  ): JsonRpcErrorOutput.OneOfVariant[T] =
    JsonRpcErrorOutput.OneOfVariant(output, matcher.lift.andThen(_.getOrElse(false)))

  def oneOfVariantClassMatcher[T](
      output: JsonRpcErrorOutput[T],
      runtimeClass: Class[_]
  ): JsonRpcErrorOutput.OneOfVariant[T] = {
    // when used with a primitive type or Unit, the class tag will correspond to the primitive type, but at runtime
    // we'll get boxed values
    val rc = primitiveToBoxedClasses.getOrElse(runtimeClass, runtimeClass)
    JsonRpcErrorOutput.OneOfVariant(output, { (a: Any) => rc.isInstance(a) })
  }

  private val primitiveToBoxedClasses = Map[Class[_], Class[_]](
    classOf[Byte] -> classOf[java.lang.Byte],
    classOf[Short] -> classOf[java.lang.Short],
    classOf[Char] -> classOf[java.lang.Character],
    classOf[Int] -> classOf[java.lang.Integer],
    classOf[Long] -> classOf[java.lang.Long],
    classOf[Float] -> classOf[java.lang.Float],
    classOf[Double] -> classOf[java.lang.Double],
    classOf[Boolean] -> classOf[java.lang.Boolean],
    java.lang.Void.TYPE -> classOf[scala.runtime.BoxedUnit]
  )

}
