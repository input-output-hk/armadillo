package io.iohk.armadillo.server

import io.iohk.armadillo.JsonRpcResponse
import io.iohk.armadillo.server.RequestHandler.DecodeFailureContext
import io.iohk.armadillo.server.ServerInterpreter.{ResponseHandlingStatus, ServerResponse}
import sttp.monad.MonadError
import sttp.monad.syntax._

class DecodeFailureInterceptor[F[_], Raw](handler: DecodeFailureHandler[Raw]) extends RequestInterceptor[F, Raw] {
  override def apply(
      responder: Responder[F, Raw],
      jsonSupport: JsonSupport[Raw],
      requestHandler: MethodInterceptor[F, Raw] => RequestHandler[F, Raw]
  ): RequestHandler[F, Raw] = {
    val next = requestHandler(MethodInterceptor.noop[F, Raw]())
    new RequestHandler[F, Raw] {
      override def onDecodeSuccess(request: JsonSupport.Json[Raw])(implicit monad: MonadError[F]): F[ResponseHandlingStatus[Raw]] = {
        next.onDecodeSuccess(request)
      }

      override def onDecodeFailure(ctx: DecodeFailureContext)(implicit monad: MonadError[F]): F[ResponseHandlingStatus[Raw]] = {
        next.onDecodeFailure(ctx).map {
          case ResponseHandlingStatus.Unhandled =>
            handler(ctx, jsonSupport)
          case handled => handled
        }
      }
    }
  }
}

object DecodeFailureInterceptor {
  def default[F[_], Raw]: DecodeFailureInterceptor[F, Raw] = new DecodeFailureInterceptor[F, Raw](DecodeFailureHandler.default[Raw])
}

trait DecodeFailureHandler[Raw] {
  def apply(context: DecodeFailureContext, jsonSupport: JsonSupport[Raw]): ResponseHandlingStatus[Raw]
}
object DecodeFailureHandler {
  def default[Raw]: DecodeFailureHandler[Raw] = (_: DecodeFailureContext, jsonSupport: JsonSupport[Raw]) => {
    ResponseHandlingStatus.Handled(
      Some(
        ServerResponse.Failure(
          jsonSupport.encodeResponse(JsonRpcResponse.error_v2(jsonSupport.encodeErrorNoData(ServerInterpreter.ParseError), None))
        )
      )
    )
  }
}
