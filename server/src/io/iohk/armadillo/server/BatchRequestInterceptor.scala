package io.iohk.armadillo.server

import io.iohk.armadillo.server.JsonSupport.Json
import io.iohk.armadillo.server.Utils._
import sttp.monad.MonadError
import sttp.tapir.DecodeResult

class BatchRequestInterceptor[F[_], Raw](handler: BatchRequestHandler[F, Raw]) extends RequestInterceptor[F, Raw] {
  override def apply(
      responder: Responder[F, Raw],
      jsonSupport: JsonSupport[Raw],
      requestHandler: MethodInterceptor[F, Raw] => RequestHandler[F, Raw]
  ): RequestHandler[F, Raw] = {
    val next = requestHandler(MethodInterceptor.noop[F, Raw]())
    new RequestHandler[F, Raw] {
      override def onDecodeSuccess(request: JsonSupport.Json[Raw])(implicit monad: MonadError[F]): F[Option[Raw]] = {
        request match {
          case Json.JsonArray(values) =>
            val results = values.map(jsonSupport.materialize).map {
              case obj: Json.JsonObject[Raw] => DecodeResult.Value(obj)
              case arr: Json.JsonArray[Raw] =>
                DecodeResult.Mismatch("expected object got array", jsonSupport.stringify(jsonSupport.demateralize(arr)))
              case Json.Other(raw) => DecodeResult.Mismatch("expected object got", jsonSupport.stringify(raw))
            }
            val combinedResults = results.foldRight(DecodeResult.Value(List.empty): DecodeResult[List[Json.JsonObject[Raw]]]) {
              (item, acc) => item.map2(acc)(_ :: _)
            }
            combinedResults match {
              case failure: DecodeResult.Failure =>
                next.onDecodeFailure(RequestHandler.DecodeFailureContext(failure, jsonSupport.stringify(jsonSupport.demateralize(request))))
              case DecodeResult.Value(requests) => handler(next, requests, jsonSupport)
            }
          case _ => next.onDecodeSuccess(request)
        }
      }

      override def onDecodeFailure(ctx: RequestHandler.DecodeFailureContext)(implicit monad: MonadError[F]): F[Option[Raw]] = {
        next.onDecodeFailure(ctx)
      }
    }
  }
}
