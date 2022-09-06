package io.iohk.armadillo.server.stub

import io.circe.generic.auto._
import io.circe.literal._
import io.iohk.armadillo._
import io.iohk.armadillo.json.circe._
import sttp.client3.circe._
import sttp.client3.impl.cats.CatsMonadError
import sttp.client3.testing.SttpBackendStub
import sttp.model.Uri._
import sttp.tapir.generic.auto._
import weaver.SimpleIOSuite

object StubServerInterpreterTest extends SimpleIOSuite {

  case class Greeting(msg: String)

  test("should return stubbed response from endpoint") {
    val testEndpoint = jsonRpcEndpoint(m"hello")
      .in(param[String]("name"))
      .out[Greeting]("greeting")

    val stubbedResponse = Greeting("Hello test subject")
    val backendStub = ArmadilloStubInterpreter(SttpBackendStub(new CatsMonadError()), new CirceJsonSupport)
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
}
