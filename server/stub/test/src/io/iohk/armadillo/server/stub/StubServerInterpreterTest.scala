package io.iohk.armadillo.server.stub

import cats.effect.IO
import io.circe.Json
import io.circe.generic.auto._
import io.circe.literal._
import io.iohk.armadillo._
import io.iohk.armadillo.json.circe._
import io.iohk.armadillo.server.ServerInterpreter
import sttp.client3.HttpError
import sttp.client3.circe._
import sttp.client3.impl.cats.CatsMonadError
import sttp.client3.testing.SttpBackendStub
import sttp.model.StatusCode
import sttp.model.Uri._
import sttp.tapir.generic.auto._
import weaver.SimpleIOSuite

object StubServerInterpreterTest extends SimpleIOSuite {

  case class Greeting(msg: String)

  private val stubInterpreter: ArmadilloStubInterpreter[IO, Json, Nothing] =
    ArmadilloStubInterpreter(SttpBackendStub(new CatsMonadError()), new CirceJsonSupport)

  private val testEndpoint = jsonRpcEndpoint(m"hello")
    .in(param[String]("name"))
    .out[Greeting]("greeting")
    .errorOut(errorNoData)

  test("should return stubbed response from endpoint") {
    val stubbedResponse = Greeting("Hello test subject")
    val backendStub = stubInterpreter
      .whenEndpoint(testEndpoint)
      .thenRespond(stubbedResponse)
      .backend()

    val responseF = backendStub.send(
      sttp.client3.basicRequest
        .post(uri"http://localhost:7654")
        .body(JsonRpcRequest.v2("hello", json"""[ "kasper" ]""", 1))
        .response(asJson[JsonRpcSuccessResponse[Greeting]])
    )
    responseF.map { r =>
      expect.same(Right(JsonRpcResponse.v2(stubbedResponse, 1)), r.body)
    }
  }

  test("should return stubbed error from endpoint") {
    val stubbedError = JsonRpcError.noData(399, "Something went wrong")
    val backendStub = stubInterpreter
      .whenEndpoint(testEndpoint)
      .thenRespondError(stubbedError)
      .backend()

    val responseF = backendStub.send(
      sttp.client3.basicRequest
        .post(uri"http://localhost:7654")
        .body(JsonRpcRequest.v2("hello", json"""[ "kasper" ]""", 1))
        .response(asJsonEither[JsonRpcErrorResponse[JsonRpcError.NoData], JsonRpcSuccessResponse[Greeting]])
    )
    responseF.map { r =>
      expect.same(Left(HttpError(JsonRpcResponse.error_v2(stubbedError, 1), StatusCode.BadRequest)), r.body)
    }
  }

  test("should throw error from endpoint") {
    val backendStub = stubInterpreter
      .whenEndpoint(testEndpoint)
      .thenThrowError(new RuntimeException("Something went wrong"))
      .backend()

    val responseF = backendStub.send(
      sttp.client3.basicRequest
        .post(uri"http://localhost:7654")
        .body(JsonRpcRequest.v2("hello", json"""[ "kasper" ]""", 1))
        .response(asJsonEither[JsonRpcErrorResponse[JsonRpcError.NoData], JsonRpcSuccessResponse[Greeting]])
    )
    responseF.map { r =>
      expect.same(Left(HttpError(JsonRpcResponse.error_v2(ServerInterpreter.InternalError, 1), StatusCode.InternalServerError)), r.body)
    }
  }

  test("should run original logic of the endpoint") {
    val serverEndpoint = testEndpoint.serverLogic { name =>
      IO.pure(Right(Greeting(s"Hello $name")): Either[JsonRpcError.NoData, Greeting])
    }
    val backendStub = stubInterpreter
      .whenServerEndpoint(serverEndpoint)
      .thenRunLogic()
      .backend()

    val responseF = backendStub.send(
      sttp.client3.basicRequest
        .post(uri"http://localhost:7654")
        .body(JsonRpcRequest.v2("hello", json"""[ "kasper" ]""", 1))
        .response(asJson[JsonRpcSuccessResponse[Greeting]])
    )
    responseF.map { r =>
      expect.same(Right(JsonRpcResponse.v2(Greeting("Hello kasper"), 1)), r.body)
    }
  }
}
