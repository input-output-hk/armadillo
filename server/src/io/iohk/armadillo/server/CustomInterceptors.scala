package io.iohk.armadillo.server

case class CustomInterceptors[F[_], Raw](
    decodeFailureHandler: DecodeFailureHandler[Raw] = DecodeFailureHandler.default[Raw],
    methodNotFoundHandler: MethodNotFoundHandler[Raw] = MethodNotFoundHandler.default[Raw],
    exceptionHandler: ExceptionHandler[Raw] = ExceptionHandler.default[Raw],
    invalidRequestHandler: InvalidRequestHandler[Raw] = InvalidRequestHandler.default[Raw],
    additionalInterceptors: List[Interceptor[F, Raw]] = Nil
) {
  def interceptors: List[Interceptor[F, Raw]] = additionalInterceptors ++ List(
    new DecodeFailureInterceptor[F, Raw](decodeFailureHandler),
    new ExceptionInterceptor[F, Raw](exceptionHandler),
    new MethodNotFoundInterceptor[F, Raw](methodNotFoundHandler),
    new InvalidRequestMethodInterceptor[F, Raw](invalidRequestHandler)
  )
}
