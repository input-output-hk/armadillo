package io.iohk.armadillo.tapir

import sttp.monad.MonadError
import sttp.tapir.metrics.{EndpointMetric, Metric}
import sttp.tapir.server.interceptor.metrics.MetricsEndpointInterceptor
import sttp.tapir.server.interceptor.{EndpointInterceptor, RequestHandler, RequestInterceptor, Responder}

class JsonRpcRequestInterceptor[F[_]] extends RequestInterceptor[F] {
  override def apply[B](
      responder: Responder[F, B],
      requestHandler: EndpointInterceptor[F] => RequestHandler[F, B]
  ): RequestHandler[F, B] = {
    RequestHandler.from { (request, monad) =>
      implicit val m: MonadError[F] = monad
      request
//      metrics
//        .foldLeft(List.empty[EndpointMetric[F]].unit) { (mAcc, metric) =>
//          for {
//            metrics <- mAcc
//            endpointMetric <- metric match {
//              case Metric(m, onRequest) => onRequest(request, m, monad)
//            }
//          } yield endpointMetric :: metrics
//        }
//        .flatMap { endpointMetrics =>
//          requestHandler(new MetricsEndpointInterceptor[F](endpointMetrics.reverse, ignoreEndpoints)).apply(request)
//        }
    }
  }

}
