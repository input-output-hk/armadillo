package io.iohk.armadillo.server

import io.iohk.armadillo.Armadillo.JsonRpcRequest
import io.iohk.armadillo.JsonRpcServerEndpoint
import io.iohk.armadillo.server.EndpointHandler.{DecodeFailureContext, DecodeSuccessContext}
import sttp.monad.MonadError
import sttp.tapir.DecodeResult

trait EndpointHandler[F[_], Raw] {
  def onDecodeSuccess[I](ctx: DecodeSuccessContext[F, I, Raw])(implicit monad: MonadError[F]): F[Option[Raw]]
  def onDecodeFailure(ctx: DecodeFailureContext[F, Raw])(implicit monad: MonadError[F]): F[Option[Raw]]
}

object EndpointHandler {
  case class DecodeSuccessContext[F[_], I, Raw](
      endpoint: JsonRpcServerEndpoint.Full[I, _, _, F],
      request: JsonRpcRequest[Raw],
      input: I
  )

  case class DecodeFailureContext[F[_], Raw](
      endpoint: JsonRpcServerEndpoint[F],
      request: JsonRpcRequest[Raw],
      f: DecodeResult.Failure
  )
}
