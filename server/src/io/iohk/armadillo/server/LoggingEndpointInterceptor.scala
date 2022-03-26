package io.iohk.armadillo.server

import io.iohk.armadillo.JsonRpcRequest
import io.iohk.armadillo.JsonRpcServerEndpoint
import sttp.monad.MonadError
import sttp.monad.syntax.*

class LoggingEndpointInterceptor[F[_], Raw](serverLog: ServerLog[F, Raw]) extends EndpointInterceptor[F, Raw] {
  override def apply(
      responder: Responder[F, Raw],
      jsonSupport: JsonSupport[Raw],
      endpointHandler: EndpointHandler[F, Raw]
  ): EndpointHandler[F, Raw] = {
    new EndpointHandler[F, Raw] {
      override def onDecodeSuccess[I](
          ctx: EndpointHandler.DecodeSuccessContext[F, I, Raw]
      )(implicit monad: MonadError[F]): F[Option[Raw]] = {
        endpointHandler
          .onDecodeSuccess(ctx)
          .flatTap(response => serverLog.requestHandled(ctx, response))
          .handleError { case e: Throwable =>
            serverLog.exception(ctx.endpoint, ctx.request, e).flatMap(_ => monad.error(e))
          }
      }

      override def onDecodeFailure(ctx: EndpointHandler.DecodeFailureContext[F, Raw])(implicit monad: MonadError[F]): F[Option[Raw]] = {
        endpointHandler
          .onDecodeFailure(ctx)
          .flatTap(response => serverLog.decodeFailure(ctx, response))
          .handleError { case e: Throwable =>
            serverLog.exception(ctx.endpoint, ctx.request, e).flatMap(_ => monad.error(e))
          }
      }
    }
  }
}

trait ServerLog[F[_], Raw] {
  def requestHandled(ctx: EndpointHandler.DecodeSuccessContext[F, _, Raw], response: Option[Raw]): F[Unit]
  def exception(endpoint: JsonRpcServerEndpoint[F], request: JsonRpcRequest[Raw], e: Throwable): F[Unit]
  def decodeFailure(ctx: EndpointHandler.DecodeFailureContext[F, Raw], response: Option[Raw]): F[Unit]
}
