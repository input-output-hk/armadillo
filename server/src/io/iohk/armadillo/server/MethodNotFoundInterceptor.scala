package io.iohk.armadillo.server

import sttp.monad.MonadError
import sttp.monad.syntax._

class MethodNotFoundInterceptor[F[_], Raw](methodNotFoundHandler: MethodNotFoundHandler[Raw]) extends MethodInterceptor[F, Raw] {
  override def apply(
      responder: Responder[F, Raw],
      jsonSupport: JsonSupport[Raw],
      methodHandler: EndpointInterceptor[F, Raw] => MethodHandler[F, Raw]
  ): MethodHandler[F, Raw] = {
    val next = methodHandler(EndpointInterceptor.noop)
    new MethodHandler[F, Raw] {
      override def onDecodeSuccess[I](ctx: MethodHandler.DecodeSuccessContext[F, Raw])(implicit monad: MonadError[F]): F[Option[Raw]] = {
        next.onDecodeSuccess(ctx).flatMap {
          case Some(value) => Option(value).unit
          case None        => methodNotFoundHandler(ctx.request, jsonSupport).map(jsonSupport.encodeResponse).unit
        }
      }

      override def onDecodeFailure(ctx: MethodHandler.DecodeFailureContext[F, Raw])(implicit monad: MonadError[F]): F[Option[Raw]] = {
        next.onDecodeFailure(ctx)
      }
    }
  }
}
