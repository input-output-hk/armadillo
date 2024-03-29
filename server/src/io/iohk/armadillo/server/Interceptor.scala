package io.iohk.armadillo.server

import io.iohk.armadillo.server.ServerInterpreter.ResponseHandlingStatus
import io.iohk.armadillo.{JsonRpcEndpoint, JsonRpcError, JsonRpcId, JsonRpcServerEndpoint}

trait Interceptor[F[_], Raw]

trait RequestInterceptor[F[_], Raw] extends Interceptor[F, Raw] {
  def apply(
      responder: Responder[F, Raw],
      jsonSupport: JsonSupport[Raw],
      requestHandler: MethodInterceptor[F, Raw] => RequestHandler[F, Raw]
  ): RequestHandler[F, Raw]
}

trait MethodOrEndpointInterceptor[F[_], Raw] extends Interceptor[F, Raw]

trait MethodInterceptor[F[_], Raw] extends MethodOrEndpointInterceptor[F, Raw] {
  def apply(
      responder: Responder[F, Raw],
      jsonSupport: JsonSupport[Raw],
      methodHandler: EndpointInterceptor[F, Raw] => MethodHandler[F, Raw]
  ): MethodHandler[F, Raw]
}

object MethodInterceptor {
  def noop[F[_], Raw](handler: EndpointInterceptor[F, Raw] = EndpointInterceptor.noop[F, Raw]): MethodInterceptor[F, Raw] =
    (_: Responder[F, Raw], _: JsonSupport[Raw], methodHandler: EndpointInterceptor[F, Raw] => MethodHandler[F, Raw]) => {
      methodHandler(handler)
    }
}

trait EndpointInterceptor[F[_], Raw] extends MethodOrEndpointInterceptor[F, Raw] {
  def apply(
      responder: Responder[F, Raw],
      jsonSupport: JsonSupport[Raw],
      endpointHandler: EndpointHandler[F, Raw]
  ): EndpointHandler[F, Raw]
}

object EndpointInterceptor {
  def noop[F[_], Raw]: EndpointInterceptor[F, Raw] =
    (_: Responder[F, Raw], _: JsonSupport[Raw], endpointHandler: EndpointHandler[F, Raw]) => endpointHandler
}

trait Responder[F[_], Raw] {
  def apply[E, O](
      result: Either[E, O],
      endpoint: JsonRpcEndpoint[_, E, O],
      requestId: Option[JsonRpcId]
  ): F[ResponseHandlingStatus[Raw]]
}

object Responder {
  case class TypedOutput[F[_], E, O](endpoint: JsonRpcServerEndpoint.Full[_, E, O, F], output: Either[JsonRpcError[E], O])
}
