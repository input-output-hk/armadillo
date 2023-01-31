package io.iohk.armadillo.example

import cats.effect.IO
import io.circe.Json
import io.circe.generic.auto._
import io.circe.literal._
import io.iohk.armadillo._
import io.iohk.armadillo.json.circe._
import io.iohk.armadillo.server.ServerInterpreter
import io.iohk.armadillo.server.stub.ArmadilloStubInterpreter
import sttp.client3.HttpError
import sttp.client3.circe._
import sttp.client3.impl.cats.CatsMonadError
import sttp.client3.testing.SttpBackendStub
import sttp.model.StatusCode
import sttp.model.Uri._
import weaver.SimpleIOSuite

object ArmadilloStubInterpreterExample extends SimpleIOSuite {

  private val stubInterpreter: ArmadilloStubInterpreter[IO, Json, Nothing] =
    ArmadilloStubInterpreter(SttpBackendStub(new CatsMonadError()), new CirceJsonSupport)

  private val describeMeEndpoint = jsonRpcEndpoint(m"describe_me")
    .in(param[String]("name").and(param[Int]("age")))
    .out[String]("description")
    .errorOut(errorNoData)

  private val stubbedResponse = "Hello John Doe, you are 42 years old"

  private val InvalidParamsError: JsonRpcError[Unit] =
    JsonRpcError(ServerInterpreter.InvalidParams.code, ServerInterpreter.InvalidParams.message, None)

  test("should return a value") {
    val backendStub = stubInterpreter
      .whenEndpoint(describeMeEndpoint)
      .assertInputs(("John Doe", 42))
      .thenRespond(stubbedResponse)
      .backend()

    val expected = Right(JsonRpcResponse.v2(stubbedResponse, 1))

    backendStub
      .send(
        sttp.client3.basicRequest
          .post(uri"http://localhost:1234")
          .body(JsonRpcRequest.v2("describe_me", json"""[ "John Doe", 42 ]""", 1))
          .response(asJson[JsonRpcSuccessResponse[String]])
      )
      .map(r => expect.same(expected, r.body))
  }

  test("should fail because age is not provided") {
    val backendStub = stubInterpreter
      .whenEndpoint(describeMeEndpoint)
      .thenRespondError(InvalidParamsError)
      .backend()

    val expected = Left(
      HttpError(
        JsonRpcErrorResponse("2.0", json"""{"code": -32602, "message": "Invalid params"}""", Some(1)),
        StatusCode(400)
      )
    )

    backendStub
      .send(
        sttp.client3.basicRequest
          .post(uri"http://localhost:1234")
          .body(JsonRpcRequest.v2("describe_me", json"""[ "John Doe" ]""", 1))
          .response(asJsonEither[JsonRpcErrorResponse[Json], JsonRpcSuccessResponse[Json]])
      )
      .map(r => expect.same(expected, r.body))
  }
}
