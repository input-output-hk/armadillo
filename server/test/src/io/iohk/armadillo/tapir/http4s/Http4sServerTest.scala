package io.iohk.armadillo.tapir.http4s

import cats.effect.IO
import cats.effect.kernel.Resource
import cats.implicits.catsSyntaxEitherId
import io.circe.generic.semiauto.*
import io.circe.literal.*
import io.circe.{Decoder, Encoder, Json}
import io.iohk.armadillo.Armadillo.{JsonRpcError, JsonRpcRequest, JsonRpcResponse}
import io.iohk.armadillo.JsonRpcEndpoint
import io.iohk.armadillo.json.circe.*
import io.iohk.armadillo.tapir.TapirInterpreter
import io.iohk.armadillo.tapir.http4s.Endpoints.*
import org.http4s.HttpRoutes
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.server.Router
import sttp.client3.asynchttpclient.cats.AsyncHttpClientCatsBackend
import sttp.client3.circe.*
import sttp.client3.{SttpBackend, basicRequest}
import sttp.model.Uri
import sttp.tapir.integ.cats.CatsMonadError
import sttp.tapir.server.http4s.{Http4sServerInterpreter, Http4sServerOptions}
import weaver.*

import scala.concurrent.ExecutionContext

object Http4sServerTest extends SimpleIOSuite {
  implicit val jsonRpcResponseEncoder: Encoder[JsonRpcResponse[Json]] = deriveEncoder[JsonRpcResponse[Json]]
  implicit val jsonRpcResponseDecoder: Decoder[JsonRpcResponse[Json]] = deriveDecoder[JsonRpcResponse[Json]]

  implicit val jsonRpcRequestEncoder: Encoder[JsonRpcRequest[Json]] = deriveEncoder[JsonRpcRequest[Json]]
  implicit val jsonRpcRequestDecoder: Decoder[JsonRpcRequest[Json]] = deriveDecoder[JsonRpcRequest[Json]]

  test(hello_in_int_out_string)(int => IO.pure(Right(int.toString)))(
    request = JsonRpcRequest[Json]("2.0", "hello", json"[42]", 1),
    expectedResponse = json"${"42"}"
  )

  private def test[I, E, O](
      in_int_out_string: JsonRpcEndpoint[I, E, O]
  )(f: I => IO[Either[List[JsonRpcError[E]], O]])(request: JsonRpcRequest[Json], expectedResponse: Json): Unit = {
    test(in_int_out_string.showDetail) {
      testServer(in_int_out_string)(f)
        .use { case (backend, baseUri) =>
          basicRequest
            .post(baseUri)
            .body(request)
            .response(asJson[JsonRpcResponse[Json]])
            .send(backend)
            .map { response =>
              expect.same(JsonRpcResponse("2.0", expectedResponse, 1).asRight[List[JsonRpcError[Unit]]], response.body)
            }
        }
    }
  }

  private def testServer[I, E, O](
      endpoint: JsonRpcEndpoint[I, E, O]
  )(logic: I => IO[Either[List[JsonRpcError[E]], O]]): Resource[IO, (SttpBackend[IO, Any], Uri)] = {
    val tapirInterpreter = new TapirInterpreter[IO, Json](new CirceJsonSupport)(new CatsMonadError)
    val tapirEndpoints = tapirInterpreter.apply(List(endpoint.serverLogic(logic)))
    val routes = Http4sServerInterpreter[IO](Http4sServerOptions.default[IO, IO]).toRoutes(tapirEndpoints)
    testServer(routes)
  }

  private def testServer(routes: HttpRoutes[IO]): Resource[IO, (SttpBackend[IO, Any], Uri)] = {
    BlazeServerBuilder[IO]
      .withExecutionContext(ExecutionContext.global)
      .bindHttp(0, "localhost")
      .withHttpApp(Router("/" -> routes).orNotFound)
      .resource
      .map(_.address.getPort)
      .flatMap { port =>
        AsyncHttpClientCatsBackend.resource[IO]().map { backend =>
          import sttp.model.Uri.*
          (backend, uri"http://localhost:$port")
        }
      }
  }
}
