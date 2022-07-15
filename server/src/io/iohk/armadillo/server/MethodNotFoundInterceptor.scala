package io.iohk.armadillo.server

import io.iohk.armadillo.{JsonRpcErrorResponse, JsonRpcSuccessResponse}
import io.iohk.armadillo.server.ServerInterpreter.{DecodeAction, ServerInterpreterResponse}
import sttp.monad.MonadError
import sttp.monad.syntax._

class MethodNotFoundInterceptor[F[_], Raw](methodNotFoundHandler: MethodNotFoundHandler[Raw]) extends MethodInterceptor[F, Raw] {
  override def apply(
      responder: Responder[F, Raw],
      jsonSupport: JsonSupport[Raw],
      methodHandler: EndpointInterceptor[F, Raw] => MethodHandler[F, Raw]
  ): MethodHandler[F, Raw] = {
    val next = methodHandler(EndpointInterceptor.noop)
    new MethodHandler[F, Raw] {
      override def onDecodeSuccess[I](
          ctx: MethodHandler.DecodeSuccessContext[F, Raw]
      )(implicit monad: MonadError[F]): F[DecodeAction[Raw]] = {
        next.onDecodeSuccess(ctx).map {
          case DecodeAction.None() =>
            methodNotFoundHandler(ctx.request, jsonSupport) match {
              case Some(response) =>
                response match {
                  case JsonRpcSuccessResponse(_, _, _) =>
                    DecodeAction.ActionTaken(ServerInterpreterResponse.Result(jsonSupport.encodeResponse(response)))
                  case JsonRpcErrorResponse(_, _, _) =>
                    DecodeAction.ActionTaken(ServerInterpreterResponse.Error(jsonSupport.encodeResponse(response)))
                }
              case None => DecodeAction.None()
            }
          case actionTaken => actionTaken
        }
      }

      override def onDecodeFailure(ctx: MethodHandler.DecodeFailureContext[F, Raw])(implicit monad: MonadError[F]): F[DecodeAction[Raw]] = {
        next.onDecodeFailure(ctx)
      }
    }
  }
}
