package io.iohk.armadillo

import sttp.tapir.EndpointIO.Info

trait Armadillo {

  def jsonRpcEndpoint(
      methodName: MethodName,
      paramStructure: ParamStructure = ParamStructure.Either
  )(implicit _codec: JsonRpcCodec[JsonRpcError[Unit]]): JsonRpcEndpoint[Unit, Unit, Unit] =
    JsonRpcEndpoint(
      methodName = methodName,
      paramStructure = paramStructure,
      input = JsonRpcInput.emptyInput,
      output = JsonRpcOutput.emptyOutput,
      error = JsonRpcErrorOutput.Single(noDataError),
      info = JsonRpcEndpointInfo.Empty
    )

  def param[T: JsonRpcCodec](name: String): JsonRpcInput.Basic[T] =
    JsonRpcIO.Single(implicitly[JsonRpcCodec[T]], JsonRpcIoInfo.Empty, name)

  def result[T: JsonRpcCodec](name: String): JsonRpcOutput.Basic[T] =
    JsonRpcIO.Single(implicitly[JsonRpcCodec[T]], JsonRpcIoInfo.Empty, name)

  def error[T](implicit _codec: JsonRpcCodec[JsonRpcError[T]]): JsonRpcErrorPart[T] =
    new JsonRpcErrorPart[T] {
      override type DATA = JsonRpcError[T]

      override def codec: JsonRpcCodec[JsonRpcError[T]] = _codec

      override def info: Info[T] = Info.empty[T]
    }

  def noDataError(implicit _codec: JsonRpcCodec[JsonRpcError[Unit]]): JsonRpcErrorPart[Unit] = {
    new JsonRpcErrorPart[Unit] {
      override type DATA = JsonRpcError[Unit]

      override def codec: JsonRpcCodec[JsonRpcError[Unit]] = _codec

      override def info: Info[Unit] = Info.empty
    }
  }
}
