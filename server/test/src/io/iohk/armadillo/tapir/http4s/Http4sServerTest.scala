package io.iohk.armadillo.tapir.http4s

import cats.effect.IO
import cats.effect.kernel.Resource
import cats.implicits.catsSyntaxEitherId
import io.circe.{Decoder, Encoder, Json}
import io.circe.literal.*
import io.iohk.armadillo.Armadillo.{JsonRpcError, JsonRpcResponse, jsonRpcEndpoint, param}
import io.iohk.armadillo.json.circe.*
import io.iohk.armadillo.tapir.TapirInterpreter
import io.iohk.armadillo.{JsonRpcEndpoint, MethodName}
import org.http4s.HttpRoutes
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.server.Router
import sttp.client3.asynchttpclient.cats.AsyncHttpClientCatsBackend
import sttp.client3.{SttpBackend, basicRequest}
import sttp.client3.circe.*
import sttp.model.Uri
import sttp.tapir.integ.cats.CatsMonadError
import sttp.tapir.server.http4s.{Http4sServerInterpreter, Http4sServerOptions}
import weaver.*
import io.circe.generic.semiauto.*

import scala.concurrent.ExecutionContext

object Http4sServerTest extends SimpleIOSuite {
  implicit val jsonRpcErrorEncoder: Encoder[JsonRpcResponse[Json]] = deriveEncoder[JsonRpcResponse[Json]]
  implicit val jsonRpcErrorDecoder: Decoder[JsonRpcResponse[Json]] = deriveDecoder[JsonRpcResponse[Json]]

  test("hello") {
    val in_int_out_string = jsonRpcEndpoint(MethodName("hello"))
      .in(param[Int]("param1"))
      .out[String]("response")

    testServer(in_int_out_string)(int => IO.pure(Right(int.toString)))
      .use { case (backend, baseUri) =>
        val value = 42
        basicRequest
          .post(baseUri)
          .body(json"""{"jsonrpc": "2.0", "method": "hello", "params": [$value], "id": 1}""")
          .response(asJson[Json])
          .send(backend)
          .map { response =>
            expect.same(json"""{"jsonrpc": "2.0", "result": ${value.toString}, "id": 1}""".asRight[List[JsonRpcError[Unit]]], response.body)
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
