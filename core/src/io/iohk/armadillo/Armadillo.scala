package io.iohk.armadillo

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

  def fixedError(code: Int, message: String): JsonRpcErrorOutput[Unit] =
    JsonRpcErrorOutput.Fixed[Unit](code, message)

  def errorNoData(implicit codec: JsonRpcCodec[JsonRpcError[Unit]]): JsonRpcErrorOutput[JsonRpcError.NoData] =
    JsonRpcErrorOutput.SingleNoData(codec)

  def customError[T](implicit codec: JsonRpcCodec[JsonRpcError[T]]): JsonRpcErrorOutput[JsonRpcError[T]] =
    JsonRpcErrorOutput.SingleWithData(codec)
}
