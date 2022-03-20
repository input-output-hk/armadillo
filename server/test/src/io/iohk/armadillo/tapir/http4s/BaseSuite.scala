package io.iohk.armadillo.tapir.http4s

import cats.effect.IO
import cats.effect.kernel.Resource
import cats.syntax.all.*
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder, Json}
import io.iohk.armadillo.Armadillo.*
import io.iohk.armadillo.json.circe.*
import io.iohk.armadillo.{JsonRpcEndpoint, JsonRpcServerEndpoint}
import io.iohk.armadillo.json.circe.CirceJsonSupport
import io.iohk.armadillo.tapir.TapirInterpreter
import org.http4s.HttpRoutes
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.server.Router
import sttp.client3.asynchttpclient.cats.AsyncHttpClientCatsBackend
import sttp.client3.circe.*
import sttp.client3.{SttpBackend, basicRequest}
import sttp.model.Uri
import sttp.tapir.integ.cats.CatsMonadError
import sttp.tapir.server.http4s.{Http4sServerInterpreter, Http4sServerOptions}
import weaver.SimpleIOSuite

import scala.concurrent.ExecutionContext

trait BaseSuite extends SimpleIOSuite {
  implicit val jsonRpcResponseDecoder: Decoder[JsonRpcResponse[Json]] =
    deriveDecoder[JsonRpcSuccessResponse[Json]].widen.or(deriveDecoder[JsonRpcErrorResponse[Json]].widen)

  implicit val jsonRpcRequestEncoder: Encoder[JsonRpcRequest[Json]] = deriveEncoder[JsonRpcRequest[Json]]
  implicit val jsonRpcRequestDecoder: Decoder[JsonRpcRequest[Json]] = deriveDecoder[JsonRpcRequest[Json]]

  def test[I, E, O](
      endpoint: JsonRpcEndpoint[I, E, O],
      suffix: String = ""
  )(
      f: I => IO[Either[JsonRpcError[E], O]]
  )(request: JsonRpcRequest[Json], expectedResponse: JsonRpcResponse[Json]): Unit = {
    test(endpoint.showDetail + " " + suffix) {
      testSingleEndpoint(endpoint)(f)
        .use { case (backend, baseUri) =>
          basicRequest
            .post(baseUri)
            .body(request)
            .response(asJson[JsonRpcResponse[Json]])
            .send(backend)
            .map { response =>
              expect.same(Right(expectedResponse), response.body)
            }
        }
    }
  }

  private def testSingleEndpoint[I, E, O](
      endpoint: JsonRpcEndpoint[I, E, O]
  )(logic: I => IO[Either[JsonRpcError[E], O]]): Resource[IO, (SttpBackend[IO, Any], Uri)] = {
    testMultipleEndpoints(List(endpoint.serverLogic(logic)))
  }

  def testMultiple(name: String)(
      se: List[JsonRpcServerEndpoint[IO]]
  )(request: List[JsonRpcRequest[Json]], expectedResponse: List[JsonRpcResponse[Json]]): Unit = {
    test(name) {
      testMultipleEndpoints(se)
        .use { case (backend, baseUri) =>
          basicRequest
            .post(baseUri)
            .body(request)
            .response(asJson[List[JsonRpcResponse[Json]]])
            .send(backend)
            .map { response =>
              expect.same(Right(expectedResponse), response.body)
            }
        }
    }
  }

  private def testMultipleEndpoints(se: List[JsonRpcServerEndpoint[IO]]): Resource[IO, (SttpBackend[IO, Any], Uri)] = {
    val tapirInterpreter = new TapirInterpreter[IO, Json](new CirceJsonSupport)(new CatsMonadError)
    val tapirEndpoints = tapirInterpreter.apply(se)
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
