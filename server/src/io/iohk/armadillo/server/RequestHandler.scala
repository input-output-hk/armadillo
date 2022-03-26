package io.iohk.armadillo.server

import io.iohk.armadillo.server.JsonSupport.Json
import io.iohk.armadillo.server.RequestHandler.DecodeFailureContext
import sttp.monad.MonadError
import sttp.tapir.DecodeResult

trait RequestHandler[F[_], Raw] {
  def onDecodeSuccess(request: Json[Raw])(implicit monad: MonadError[F]): F[Option[Raw]]

  def onDecodeFailure(ctx: DecodeFailureContext)(implicit monad: MonadError[F]): F[Option[Raw]]
}

object RequestHandler {
  case class DecodeFailureContext(failure: DecodeResult.Failure, request: String)

  def apply[F[_], Raw](
      onSuccess: Json[Raw] => F[Option[Raw]],
      onError: RequestHandler.DecodeFailureContext => F[Option[Raw]]
  ): RequestHandler[F, Raw] = {
    new RequestHandler[F, Raw] {
      override def onDecodeSuccess(request: Json[Raw])(implicit monad: MonadError[F]): F[Option[Raw]] = {
        onSuccess(request)
      }

      override def onDecodeFailure(ctx: RequestHandler.DecodeFailureContext)(implicit monad: MonadError[F]): F[Option[Raw]] = {
        onError(ctx)
      }
    }
  }

}
