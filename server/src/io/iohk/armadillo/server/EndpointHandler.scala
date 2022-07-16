package io.iohk.armadillo.server

import io.iohk.armadillo.server.EndpointHandler.{DecodeFailureContext, DecodeSuccessContext}
import io.iohk.armadillo.server.JsonSupport.Json
import io.iohk.armadillo.server.ServerInterpreter.ResponseHandlingStatus
import io.iohk.armadillo.{JsonRpcRequest, JsonRpcServerEndpoint}
import sttp.monad.MonadError
import sttp.tapir.DecodeResult

trait EndpointHandler[F[_], Raw] {
  def onDecodeSuccess[I](ctx: DecodeSuccessContext[F, I, Raw])(implicit monad: MonadError[F]): F[ResponseHandlingStatus[Raw]]
  def onDecodeFailure(ctx: DecodeFailureContext[F, Raw])(implicit monad: MonadError[F]): F[ResponseHandlingStatus[Raw]]
}

object EndpointHandler {
  case class DecodeSuccessContext[F[_], I, Raw](
      endpoint: JsonRpcServerEndpoint.Full[I, _, _, F],
      request: JsonRpcRequest[Json[Raw]],
      input: I
  )

  case class DecodeFailureContext[F[_], Raw](
      endpoint: JsonRpcServerEndpoint[F],
      request: JsonRpcRequest[Json[Raw]],
      f: DecodeResult.Failure
  )
}
