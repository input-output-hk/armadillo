package io.iohk.armadillo.server

import io.iohk.armadillo.{JsonRpcRequest, JsonRpcServerEndpoint}
import io.iohk.armadillo.server.MethodHandler.{DecodeFailureContext, DecodeSuccessContext}
import sttp.monad.MonadError
import sttp.tapir.DecodeResult

trait MethodHandler[F[_], Raw] {
  def onDecodeSuccess[I](ctx: DecodeSuccessContext[F, Raw])(implicit monad: MonadError[F]): F[Option[Raw]]
  def onDecodeFailure(ctx: DecodeFailureContext[F, Raw])(implicit monad: MonadError[F]): F[Option[Raw]]
}

object MethodHandler {
  case class DecodeSuccessContext[F[_], Raw](
      endpoints: List[JsonRpcServerEndpoint[F]],
      request: JsonRpcRequest[Raw]
  )

  case class DecodeFailureContext[F[_], Raw](
      endpoints: List[JsonRpcServerEndpoint[F]],
      request: Raw,
      f: DecodeResult.Failure
  )

  def apply[F[_], Raw](
      onSuccess: DecodeSuccessContext[F, Raw] => F[Option[Raw]],
      onError: DecodeFailureContext[F, Raw] => F[Option[Raw]]
  ): MethodHandler[F, Raw] = {
    new MethodHandler[F, Raw] {
      override def onDecodeSuccess[I](ctx: DecodeSuccessContext[F, Raw])(implicit monad: MonadError[F]): F[Option[Raw]] = {
        onSuccess(ctx)
      }

      override def onDecodeFailure(ctx: DecodeFailureContext[F, Raw])(implicit monad: MonadError[F]): F[Option[Raw]] = {
        onError(ctx)
      }
    }
  }
}
