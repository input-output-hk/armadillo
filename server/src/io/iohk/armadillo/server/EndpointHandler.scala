package io.iohk.armadillo.server

import io.iohk.armadillo.server.EndpointHandler.{DecodeFailureContext, DecodeSuccessContext}
import io.iohk.armadillo.server.JsonSupport.Json
import io.iohk.armadillo.server.ServerInterpreter.ResponseHandlingStatus
import io.iohk.armadillo.{JsonRpcRequest, JsonRpcServerEndpoint}
import sttp.monad.MonadError
import sttp.tapir.DecodeResult

trait EndpointHandler[F[_], Raw] {
  def onDecodeSuccess[I, E, O](ctx: DecodeSuccessContext[F, I, E, O, Raw])(implicit monad: MonadError[F]): F[ResponseHandlingStatus[Raw]]
  def onDecodeFailure(ctx: DecodeFailureContext[F, Raw])(implicit monad: MonadError[F]): F[ResponseHandlingStatus[Raw]]
}

object EndpointHandler {
  case class DecodeSuccessContext[F[_], I, E, O, Raw](
      endpoint: JsonRpcServerEndpoint.Full[I, E, O, F],
      request: JsonRpcRequest[Json[Raw]],
      input: I
  )

  case class DecodeFailureContext[F[_], Raw](
      endpoint: JsonRpcServerEndpoint[F],
      request: JsonRpcRequest[Json[Raw]],
      f: DecodeResult.Failure
  )
}
