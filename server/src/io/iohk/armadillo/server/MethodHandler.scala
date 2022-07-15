package io.iohk.armadillo.server

import io.iohk.armadillo.server.JsonSupport.Json
import io.iohk.armadillo.server.MethodHandler.{DecodeFailureContext, DecodeSuccessContext}
import io.iohk.armadillo.server.ServerInterpreter.DecodeAction
import io.iohk.armadillo.{JsonRpcRequest, JsonRpcServerEndpoint}
import sttp.monad.MonadError
import sttp.tapir.DecodeResult

trait MethodHandler[F[_], Raw] {
  def onDecodeSuccess[I](ctx: DecodeSuccessContext[F, Raw])(implicit monad: MonadError[F]): F[DecodeAction[Raw]]
  def onDecodeFailure(ctx: DecodeFailureContext[F, Raw])(implicit monad: MonadError[F]): F[DecodeAction[Raw]]
}

object MethodHandler {
  case class DecodeSuccessContext[F[_], Raw](
      endpoints: List[JsonRpcServerEndpoint[F]],
      request: JsonRpcRequest[Json[Raw]]
  )

  case class DecodeFailureContext[F[_], Raw](
      endpoints: List[JsonRpcServerEndpoint[F]],
      request: Json[Raw],
      f: DecodeResult.Failure
  )

  def apply[F[_], Raw](
      onSuccess: DecodeSuccessContext[F, Raw] => F[DecodeAction[Raw]],
      onError: DecodeFailureContext[F, Raw] => F[DecodeAction[Raw]]
  ): MethodHandler[F, Raw] = {
    new MethodHandler[F, Raw] {
      override def onDecodeSuccess[I](ctx: DecodeSuccessContext[F, Raw])(implicit monad: MonadError[F]): F[DecodeAction[Raw]] = {
        onSuccess(ctx)
      }

      override def onDecodeFailure(ctx: DecodeFailureContext[F, Raw])(implicit monad: MonadError[F]): F[DecodeAction[Raw]] = {
        onError(ctx)
      }
    }
  }
}
