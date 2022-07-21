package io.iohk.armadillo.server

import io.iohk.armadillo.server.ServerInterpreter.ResponseHandlingStatus
import sttp.monad.MonadError
import sttp.monad.syntax._

class InvalidRequestMethodInterceptor[F[_], Raw](invalidRequestHandler: InvalidRequestHandler[Raw]) extends MethodInterceptor[F, Raw] {
  override def apply(
      responder: Responder[F, Raw],
      jsonSupport: JsonSupport[Raw],
      methodHandler: EndpointInterceptor[F, Raw] => MethodHandler[F, Raw]
  ): MethodHandler[F, Raw] = {
    val next = methodHandler(EndpointInterceptor.noop)
    new MethodHandler[F, Raw] {
      override def onDecodeSuccess[I](
          ctx: MethodHandler.DecodeSuccessContext[F, Raw]
      )(implicit monad: MonadError[F]): F[ResponseHandlingStatus[Raw]] = {
        next.onDecodeSuccess(ctx)
      }

      override def onDecodeFailure(
          ctx: MethodHandler.DecodeFailureContext[F, Raw]
      )(implicit monad: MonadError[F]): F[ResponseHandlingStatus[Raw]] = {
        next.onDecodeFailure(ctx).map {
          case ResponseHandlingStatus.Unhandled =>
            invalidRequestHandler(ctx.request, ctx.f, jsonSupport)
          case actionTaken => actionTaken
        }
      }
    }
  }
}
