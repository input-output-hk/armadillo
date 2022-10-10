package io.iohk.armadillo.server.stub

import io.iohk.armadillo.JsonRpcServerEndpoint.Full
import io.iohk.armadillo.server.{CustomInterceptors, Interceptor, JsonSupport}
import io.iohk.armadillo.{JsonRpcEndpoint, JsonRpcServerEndpoint}
import sttp.client3.testing.SttpBackendStub
import sttp.client3.{Request, SttpBackend}
import sttp.monad.MonadError
import sttp.monad.syntax._

final case class InputCheck[T](f: T => Either[String, T]) {
  def apply(input: T): Either[String, T] = f(input)
}

object InputCheck {
  def pass[T]: InputCheck[T] = InputCheck[T](i => Right(i))

  def exact[T](expected: T): InputCheck[T] =
    InputCheck[T](input => Either.cond(input == expected, input, s"Invalid input received $input, expected $expected"))
}

class ArmadilloStubInterpreter[F[_], Raw, R] private (
    ses: List[JsonRpcServerEndpoint[F]],
    interceptors: List[Interceptor[F, Raw]],
    stub: SttpBackendStub[F, R],
    jsonSupport: JsonSupport[Raw]
) {

  def whenServerEndpoint[I, E, O](se: JsonRpcServerEndpoint.Full[I, E, O, F]): ArmadilloServerEndpointStub[I, E, O] = {
    ArmadilloServerEndpointStub(se, InputCheck.pass)
  }

  def whenEndpoint[I, E, O](e: JsonRpcEndpoint[I, E, O]): ArmadilloEndpointStub[I, E, O] = {
    ArmadilloEndpointStub(e, InputCheck.pass)
  }

  final case class ArmadilloEndpointStub[I, E, O](
      endpoint: JsonRpcEndpoint[I, E, O],
      inputCheck: InputCheck[I]
  ) {

    def assertInputs(expectedInputs: I): ArmadilloEndpointStub[I, E, O] =
      copy(inputCheck = InputCheck.exact(expectedInputs))
    def assertInputs(inputCheck: InputCheck[I]): ArmadilloEndpointStub[I, E, O] =
      copy(inputCheck = inputCheck)

    def thenRespond(response: O): ArmadilloStubInterpreter[F, Raw, R] =
      append(endpoint.serverLogic(_ => (Right(response): Either[E, O]).unit), inputCheck)

    def thenRespondError(error: E): ArmadilloStubInterpreter[F, Raw, R] =
      append(endpoint.serverLogic(_ => (Left(error): Either[E, O]).unit), inputCheck)

    def thenThrowError(error: Throwable): ArmadilloStubInterpreter[F, Raw, R] =
      append(endpoint.serverLogic(_ => throw error), inputCheck)
  }

  final case class ArmadilloServerEndpointStub[I, E, O](
      se: JsonRpcServerEndpoint.Full[I, E, O, F],
      inputCheck: InputCheck[I]
  ) {

    def assertInputs(expectedInputs: I): ArmadilloServerEndpointStub[I, E, O] =
      copy(inputCheck = InputCheck.exact(expectedInputs))
    def assertInputs(inputCheck: InputCheck[I]): ArmadilloServerEndpointStub[I, E, O] =
      copy(inputCheck = inputCheck)

    def thenRespond(response: O): ArmadilloStubInterpreter[F, Raw, R] =
      append(se.endpoint.serverLogic(_ => (Right(response): Either[E, O]).unit), inputCheck)

    def thenRespondError(error: E): ArmadilloStubInterpreter[F, Raw, R] =
      append(se.endpoint.serverLogic(_ => (Left(error): Either[E, O]).unit), inputCheck)

    def thenThrowError(error: Throwable): ArmadilloStubInterpreter[F, Raw, R] =
      append(se.endpoint.serverLogic(_ => throw error), inputCheck)

    def thenRunLogic(): ArmadilloStubInterpreter[F, Raw, R] = append(se, inputCheck)

  }

  private def asserEndpointInput[I, E, O](se: Full[I, E, O, F], inputCheck: InputCheck[I]): Full[I, E, O, F] = {
    se.endpoint.serverLogic(i => monadErrorFromEither(inputCheck(i)).flatMap(i => se.logic(monad)(i)))
  }

  private def append[I, E, O](
      se: JsonRpcServerEndpoint.Full[I, E, O, F],
      inputCheck: InputCheck[I]
  ): ArmadilloStubInterpreter[F, Raw, R] = {
    new ArmadilloStubInterpreter[F, Raw, R](
      ses :+ asserEndpointInput(se, inputCheck),
      interceptors,
      stub,
      jsonSupport
    )
  }

  private implicit val monad: MonadError[F] = stub.responseMonad

  private def monadErrorFromEither[T](either: Either[String, T]) = either match {
    case Left(value)  => monad.error(new IllegalArgumentException(value))
    case Right(value) => monad.unit(value)
  }

  def backend(): SttpBackend[F, R] = {
    stub.whenAnyRequest.thenRespondF(req =>
      new StubServerInterpreter(ses, interceptors, jsonSupport, stub).apply(req.asInstanceOf[Request[Any, R]])
    )
  }
}

object ArmadilloStubInterpreter {

  def apply[F[_], R, Raw](stub: SttpBackendStub[F, R], jsonSupport: JsonSupport[Raw]): ArmadilloStubInterpreter[F, Raw, R] =
    new ArmadilloStubInterpreter[F, Raw, R](List.empty, CustomInterceptors().interceptors, stub, jsonSupport)
}
