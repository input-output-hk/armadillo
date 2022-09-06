package io.iohk.armadillo.server.stub

import io.iohk.armadillo.server.{CustomInterceptors, Interceptor, JsonSupport}
import io.iohk.armadillo.{JsonRpcEndpoint, JsonRpcServerEndpoint}
import sttp.client3.testing.SttpBackendStub
import sttp.client3.{Request, SttpBackend}
import sttp.monad.MonadError
import sttp.monad.syntax._

class ArmadilloStubInterpreter[F[_], Raw, R](
    ses: List[JsonRpcServerEndpoint[F]],
    interceptors: List[Interceptor[F, Raw]],
    stub: SttpBackendStub[F, R],
    jsonSupport: JsonSupport[Raw]
) {

  def whenServerEndpoint[I, E, O](se: JsonRpcServerEndpoint.Full[I, E, O, F]): ArmadilloServerEndpointStub[I, E, O] = {
    new ArmadilloServerEndpointStub(se)
  }

  def whenEndpoint[I, E, O](e: JsonRpcEndpoint[I, E, O]): ArmadilloEndpointStub[I, E, O] = {
    new ArmadilloEndpointStub(e)
  }

  class ArmadilloEndpointStub[I, E, O](endpoint: JsonRpcEndpoint[I, E, O]) {

    def thenRespond(response: O): ArmadilloStubInterpreter[F, Raw, R] = append(
      endpoint.serverLogic(_ => (Right(response): Either[E, O]).unit)
    )

    def thenRespondError(error: E): ArmadilloStubInterpreter[F, Raw, R] = append(
      endpoint.serverLogic(_ => (Left(error): Either[E, O]).unit)
    )

    def thenThrowError(error: Throwable): ArmadilloStubInterpreter[F, Raw, R] = append(endpoint.serverLogic(_ => throw error))
  }

  class ArmadilloServerEndpointStub[I, E, O](se: JsonRpcServerEndpoint.Full[I, E, O, F]) {
    def thenRespond(response: O): ArmadilloStubInterpreter[F, Raw, R] = append(
      se.endpoint.serverLogic(_ => (Right(response): Either[E, O]).unit)
    )

    def thenRespondError(error: E): ArmadilloStubInterpreter[F, Raw, R] = append(
      se.endpoint.serverLogic(_ => (Left(error): Either[E, O]).unit)
    )

    def thenThrowError(error: Throwable): ArmadilloStubInterpreter[F, Raw, R] = append(se.endpoint.serverLogic(_ => throw error))

    def thenRunLogic(): ArmadilloStubInterpreter[F, Raw, R] = append(se)
  }

  private def append(se: JsonRpcServerEndpoint[F]): ArmadilloStubInterpreter[F, Raw, R] = {
    new ArmadilloStubInterpreter[F, Raw, R](ses :+ se, interceptors, stub, jsonSupport)
  }

  private implicit val monad: MonadError[F] = stub.responseMonad

  def backend(): SttpBackend[F, R] =
    stub.whenAnyRequest.thenRespondF(req =>
      new StubServerInterpreter(ses, interceptors, jsonSupport, stub).apply(req.asInstanceOf[Request[Any, R]])
    )
}

object ArmadilloStubInterpreter {

  def apply[F[_], R, Raw](stub: SttpBackendStub[F, R], jsonSupport: JsonSupport[Raw]): ArmadilloStubInterpreter[F, Raw, R] =
    new ArmadilloStubInterpreter[F, Raw, R](List.empty, CustomInterceptors().interceptors, stub, jsonSupport)
}
