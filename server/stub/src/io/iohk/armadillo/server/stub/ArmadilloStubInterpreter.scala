package io.iohk.armadillo.server.stub

import io.iohk.armadillo.server.{CustomInterceptors, Interceptor, JsonSupport}
import io.iohk.armadillo.{JsonRpcEndpoint, JsonRpcServerEndpoint}
import sttp.client3.testing.SttpBackendStub
import sttp.client3.{Request, SttpBackend}
import sttp.monad.MonadError
import sttp.monad.syntax._

final case class InputCheck[T](expected: T)

class ArmadilloStubInterpreter[F[_], Raw, R](
    ses: List[JsonRpcServerEndpoint[F]],
    interceptors: List[Interceptor[F, Raw]],
    stub: SttpBackendStub[F, R],
    jsonSupport: JsonSupport[Raw],
    private val inputChecksByEndpoints: Map[JsonRpcServerEndpoint[F], InputCheck[_]] = Map.empty[JsonRpcServerEndpoint[F], InputCheck[_]]
) {

  def whenServerEndpoint[I, E, O](se: JsonRpcServerEndpoint.Full[I, E, O, F]): ArmadilloServerEndpointStub[I, E, O] = {
    new ArmadilloServerEndpointStub(se)
  }

  def whenEndpoint[I, E, O](e: JsonRpcEndpoint[I, E, O]): ArmadilloEndpointStub[I, E, O] = {
    new ArmadilloEndpointStub(e)
  }

  class ArmadilloEndpointStub[I, E, O](
      endpoint: JsonRpcEndpoint[I, E, O],
      private val inputCheck: Option[I] = None
  ) {

    def assertInputs(expectedInputs: Option[I]): ArmadilloEndpointStub[I, E, O] =
      new ArmadilloEndpointStub[I, E, O](endpoint, expectedInputs)

    def thenRespond(response: O): ArmadilloStubInterpreter[F, Raw, R] =
      append(endpoint.serverLogic(_ => (Right(response): Either[E, O]).unit), inputCheck)

    def thenRespondError(error: E): ArmadilloStubInterpreter[F, Raw, R] =
      append(endpoint.serverLogic(_ => (Left(error): Either[E, O]).unit), inputCheck)

    def thenThrowError(error: Throwable): ArmadilloStubInterpreter[F, Raw, R] =
      append(endpoint.serverLogic(_ => throw error), inputCheck)
  }

  class ArmadilloServerEndpointStub[I, E, O](
      se: JsonRpcServerEndpoint.Full[I, E, O, F],
      private val inputCheck: Option[I] = None
  ) {

    def assertInputs(expectedInputs: I): ArmadilloServerEndpointStub[I, E, O] =
      new ArmadilloServerEndpointStub[I, E, O](se, Some(expectedInputs))

    def thenRespond(response: O): ArmadilloStubInterpreter[F, Raw, R] =
      append(se.endpoint.serverLogic(_ => (Right(response): Either[E, O]).unit), inputCheck)

    def thenRespondError(error: E): ArmadilloStubInterpreter[F, Raw, R] =
      append(se.endpoint.serverLogic(_ => (Left(error): Either[E, O]).unit), inputCheck)

    def thenThrowError(error: Throwable): ArmadilloStubInterpreter[F, Raw, R] =
      append(se.endpoint.serverLogic(_ => throw error), inputCheck)

    def thenRunLogic(): ArmadilloStubInterpreter[F, Raw, R] = append(se, inputCheck)
  }

  private def append(se: JsonRpcServerEndpoint[F], maybeInputCheck: Option[_]): ArmadilloStubInterpreter[F, Raw, R] = {
    new ArmadilloStubInterpreter[F, Raw, R](
      ses :+ se,
      interceptors,
      stub,
      jsonSupport,
      maybeInputCheck.fold(inputChecksByEndpoints)(inputCheck => inputChecksByEndpoints + (se -> InputCheck(inputCheck)))
    )
  }

  private implicit val monad: MonadError[F] = stub.responseMonad

  def backend(): SttpBackend[F, R] = {
    val updatedServerEndpoints = ses.map { se =>
      inputChecksByEndpoints.get(se) match {
        case Some(inputCheck: InputCheck[_]) =>
          se.endpoint.serverLogic { input =>
            val expected = inputCheck.expected.asInstanceOf[se.INPUT]
            assert(input == expected)
            se.logic(monad)(input)
          }
        case None => se
      }
    }
    stub.whenAnyRequest.thenRespondF(req =>
      new StubServerInterpreter(updatedServerEndpoints, interceptors, jsonSupport, stub).apply(req.asInstanceOf[Request[Any, R]])
    )
  }
}

object ArmadilloStubInterpreter {

  def apply[F[_], R, Raw](stub: SttpBackendStub[F, R], jsonSupport: JsonSupport[Raw]): ArmadilloStubInterpreter[F, Raw, R] =
    new ArmadilloStubInterpreter[F, Raw, R](List.empty, CustomInterceptors().interceptors, stub, jsonSupport)
}
