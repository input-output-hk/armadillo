package io.iohk.armadillo.server

import io.iohk.armadillo.server.JsonSupport.Json
import io.iohk.armadillo.server.RequestHandler.DecodeFailureContext
import io.iohk.armadillo.server.ServerInterpreter.ResponseHandlingStatus
import sttp.monad.MonadError
import sttp.tapir.DecodeResult

trait RequestHandler[F[_], Raw] {
  def onDecodeSuccess(request: Json[Raw])(implicit monad: MonadError[F]): F[ResponseHandlingStatus[Raw]]

  def onDecodeFailure(ctx: DecodeFailureContext)(implicit monad: MonadError[F]): F[ResponseHandlingStatus[Raw]]
}

object RequestHandler {
  case class DecodeFailureContext(failure: DecodeResult.Failure, request: String)

  def apply[F[_], Raw](
      onSuccess: Json[Raw] => F[ResponseHandlingStatus[Raw]],
      onError: RequestHandler.DecodeFailureContext => F[ResponseHandlingStatus[Raw]]
  ): RequestHandler[F, Raw] = {
    new RequestHandler[F, Raw] {
      override def onDecodeSuccess(request: Json[Raw])(implicit monad: MonadError[F]): F[ResponseHandlingStatus[Raw]] = {
        onSuccess(request)
      }

      override def onDecodeFailure(
          ctx: RequestHandler.DecodeFailureContext
      )(implicit monad: MonadError[F]): F[ResponseHandlingStatus[Raw]] = {
        onError(ctx)
      }
    }
  }

}
