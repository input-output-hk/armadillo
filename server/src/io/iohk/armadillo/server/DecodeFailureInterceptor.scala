package io.iohk.armadillo.server

import io.iohk.armadillo.JsonRpcResponse
import io.iohk.armadillo.server.RequestHandler.DecodeFailureContext
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
      override def onDecodeSuccess(request: JsonSupport.Json[Raw])(implicit monad: MonadError[F]): F[Option[Raw]] = {
        next.onDecodeSuccess(request)
      }

      override def onDecodeFailure(ctx: DecodeFailureContext)(implicit monad: MonadError[F]): F[Option[Raw]] = {
        next.onDecodeFailure(ctx).flatMap {
          case Some(value) => Option(value).unit
          case None        => monad.unit(handler(ctx, jsonSupport).map(jsonSupport.encodeResponse))
        }
      }
    }
  }
}

object DecodeFailureInterceptor {
  def default[F[_], Raw]: DecodeFailureInterceptor[F, Raw] = new DecodeFailureInterceptor[F, Raw](DecodeFailureHandler.default[Raw])
}

trait DecodeFailureHandler[Raw] {
  def apply(context: DecodeFailureContext, jsonSupport: JsonSupport[Raw]): Option[JsonRpcResponse[Raw]]
}
object DecodeFailureHandler {
  def default[Raw]: DecodeFailureHandler[Raw] = (_: DecodeFailureContext, jsonSupport: JsonSupport[Raw]) => {
    Some(JsonRpcResponse.error_v2(jsonSupport.encodeErrorNoData(ServerInterpreter.ParseError), None))
  }
}
