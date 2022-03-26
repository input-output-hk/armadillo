package io.iohk.armadillo.server

import io.iohk.armadillo.Armadillo.JsonRpcResponse
import io.iohk.armadillo.{AnyEndpoint, AnyRequest}
import sttp.monad.MonadError

import scala.util.control.NonFatal

class ExceptionInterceptor[F[_], Raw](handler: ExceptionHandler[Raw]) extends EndpointInterceptor[F, Raw] {
  override def apply(
      responder: Responder[F, Raw],
      jsonSupport: JsonSupport[Raw],
      endpointHandler: EndpointHandler[F, Raw]
  ): EndpointHandler[F, Raw] = {
    new EndpointHandler[F, Raw] {
      override def onDecodeSuccess[I](ctx: EndpointHandler.DecodeSuccessContext[F, I, Raw])(implicit
          monad: MonadError[F]
      ): F[Option[Raw]] = {
        monad.handleError(endpointHandler.onDecodeSuccess(ctx)) { case NonFatal(e) =>
          onException(e, ctx.endpoint.endpoint, ctx.request)
        }
      }

      override def onDecodeFailure(ctx: EndpointHandler.DecodeFailureContext[F, Raw])(implicit monad: MonadError[F]): F[Option[Raw]] = {
        monad.handleError(endpointHandler.onDecodeFailure(ctx)) { case NonFatal(e) =>
          onException(e, ctx.endpoint.endpoint, ctx.request)
        }
      }

      private def onException(e: Throwable, endpoint: AnyEndpoint, request: AnyRequest)(implicit
          monad: MonadError[F]
      ): F[Option[Raw]] = {
        handler(ExceptionContext(e, endpoint, request), jsonSupport) match {
          case Right(response) => responder(response)
          case Left(_)         => monad.error(e)
        }
      }
    }
  }
}
object ExceptionInterceptor {
  def default[F[_], Raw]: ExceptionInterceptor[F, Raw] = new ExceptionInterceptor[F, Raw](new DefaultExceptionHandler[Raw])
}

case class ExceptionContext(e: Throwable, endpoint: AnyEndpoint, request: AnyRequest)

trait ExceptionHandler[Raw] {
  // Left means unhandled
  def apply(ctx: ExceptionContext, jsonSupport: JsonSupport[Raw]): Either[Unit, Option[JsonRpcResponse[Raw]]]
}

class DefaultExceptionHandler[Raw] extends ExceptionHandler[Raw] {
  override def apply(ctx: ExceptionContext, jsonSupport: JsonSupport[Raw]): Either[Unit, Option[JsonRpcResponse[Raw]]] = {
    ctx.request.id match {
      case Some(value) => Right(Some(JsonRpcResponse.error_v2(jsonSupport.encodeErrorNoData(ServerInterpreter.InternalError), Some(value))))
      case None        => Right(Option.empty)
    }
  }
}
