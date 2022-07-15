package io.iohk.armadillo.server

import io.iohk.armadillo.JsonRpcResponse
import io.iohk.armadillo.server.JsonSupport.Json
import io.iohk.armadillo.server.ServerInterpreter.{DecodeAction, ServerInterpreterResponse}
import sttp.monad.MonadError

class InvalidRequestStructureInterceptor[F[_], Raw] extends RequestInterceptor[F, Raw] {
  override def apply(
      responder: Responder[F, Raw],
      jsonSupport: JsonSupport[Raw],
      requestHandler: MethodInterceptor[F, Raw] => RequestHandler[F, Raw]
  ): RequestHandler[F, Raw] = {
    val next = requestHandler(MethodInterceptor.noop[F, Raw]())
    new RequestHandler[F, Raw] {
      override def onDecodeSuccess(request: JsonSupport.Json[Raw])(implicit monad: MonadError[F]): F[DecodeAction[Raw]] = {
        request match {
          case obj: Json.JsonObject[Raw] => next.onDecodeSuccess(obj)
          case arr: Json.JsonObject[Raw] => next.onDecodeSuccess(arr)
          case Json.Other(_) =>
            val response = JsonRpcResponse.error_v2(jsonSupport.encodeErrorNoData(ServerInterpreter.InvalidRequest), None)
            monad.unit(DecodeAction.ActionTaken(ServerInterpreterResponse.Error(jsonSupport.encodeResponse(response))))
        }
      }

      override def onDecodeFailure(ctx: RequestHandler.DecodeFailureContext)(implicit monad: MonadError[F]): F[DecodeAction[Raw]] = {
        next.onDecodeFailure(ctx)
      }
    }
  }
}
