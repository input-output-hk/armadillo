package io.iohk.armadillo.tapir.http4s

import cats.effect.IO
import cats.effect.kernel.Resource
import io.circe.{Encoder, Json, parser}
import io.iohk.armadillo.json.circe.CirceJsonSupport
import io.iohk.armadillo.server.AbstractBaseSuite
import io.iohk.armadillo.server.Endpoints.hello_in_int_out_string
import io.iohk.armadillo.server.ServerInterpreter.{InterpretationError, ServerInterpreterResponse}
import io.iohk.armadillo.tapir.TapirInterpreter
import io.iohk.armadillo._
import org.http4s.HttpRoutes
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.server.Router
import sttp.client3.asynchttpclient.cats.AsyncHttpClientCatsBackend
import sttp.client3.circe._
import sttp.client3.{DeserializationException, HttpError, StringBody, SttpBackend, basicRequest}
import sttp.model.{MediaType, StatusCode, Uri}
import sttp.tapir.integ.cats.CatsMonadError
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.http4s.{Http4sServerInterpreter, Http4sServerOptions}

import scala.concurrent.ExecutionContext

trait BaseSuite extends AbstractBaseSuite[StringBody, ServerEndpoint[Any, IO]] {

  override def invalidBody: StringBody =
    StringBody("""{"jsonrpc": "2.0", "method": "foobar, "params": "bar", "baz]""", "utf-8", MediaType.ApplicationJson)

  def testNotification[I, E, O, B: Encoder](
      endpoint: JsonRpcEndpoint[I, E, O],
      suffix: String = ""
  )(
      f: I => IO[Either[JsonRpcError[E], O]]
  )(request: B): Unit = {
    test(endpoint.showDetail + " as notification " + suffix) {
      testSingleEndpoint(endpoint)(f)
        .use { case (backend, baseUri) =>
          basicRequest
            .post(baseUri)
            .body(request)
            .send(backend)
            .map { response =>
              expect.same(StatusCode.Ok, response.code)
            }
        }
    }
  }

  def testInvalidRequest[I, E, O](suffix: String)(request: StringBody, expectedResponse: JsonRpcResponse[Json]): Unit = {
    test(suffix) {
      testSingleEndpoint(hello_in_int_out_string)(int => IO.pure(Right(int.toString)))
        .use { case (backend, baseUri) =>
          basicRequest
            .post(baseUri)
            .body(StringBody("""{"jsonrpc": "2.0", "method": "foobar, "params": "bar", "baz]""", "utf-8", MediaType.ApplicationJson))
            .response(asJson[JsonRpcResponse[Json]])
            .send(backend)
            .map { response =>
              expect.same(
                expectedResponse match {
                  case success @ JsonRpcSuccessResponse(_, _, _) => ServerInterpreterResponse.Result(jsonSupport.encodeResponse(success))
                  case error @ JsonRpcErrorResponse(_, _, _)     => ServerInterpreterResponse.Error(jsonSupport.encodeResponse(error))
                },
                response.body match {
                  case Left(error) =>
                    error match {
                      case HttpError(body, _)             => ServerInterpreterResponse.Error(parser.parse(body).toOption.get)
                      case DeserializationException(_, _) => throw new RuntimeException("DeserializationException was not expected")
                    }
                  case Right(body) =>
                    body match {
                      case result @ JsonRpcSuccessResponse(_, _, _) => ServerInterpreterResponse.Result(jsonSupport.encodeResponse(result))
                      case error @ JsonRpcErrorResponse(_, _, _)    => ServerInterpreterResponse.Error(jsonSupport.encodeResponse(error))
                    }
                }
              )
            }
        }
    }
  }

  def test[I, E, O, B: Encoder](
      endpoint: JsonRpcEndpoint[I, E, O],
      suffix: String = ""
  )(
      f: I => IO[Either[JsonRpcError[E], O]]
  )(request: B, expectedResponse: JsonRpcResponse[Json]): Unit = {
    test(endpoint.showDetail + " " + suffix) {
      testSingleEndpoint(endpoint)(f)
        .use { case (backend, baseUri) =>
          basicRequest
            .post(baseUri)
            .body(request)
            .response(asJson[JsonRpcResponse[Json]])
            .send(backend)
            .map { response =>
              expect.same(
                expectedResponse match {
                  case success @ JsonRpcSuccessResponse(_, _, _) => ServerInterpreterResponse.Result(jsonSupport.encodeResponse(success))
                  case error @ JsonRpcErrorResponse(_, _, _)     => ServerInterpreterResponse.Error(jsonSupport.encodeResponse(error))
                },
                response.body match {
                  case Left(error) =>
                    error match {
                      case HttpError(body, _)             => ServerInterpreterResponse.Error(parser.parse(body).toOption.get)
                      case DeserializationException(_, _) => throw new RuntimeException("DeserializationException was not expected")
                    }
                  case Right(body) =>
                    body match {
                      case result @ JsonRpcSuccessResponse(_, _, _) => ServerInterpreterResponse.Result(jsonSupport.encodeResponse(result))
                      case error @ JsonRpcErrorResponse(_, _, _)    => ServerInterpreterResponse.Error(jsonSupport.encodeResponse(error))
                    }
                }
              )
            }
        }
    }
  }

  def testMultiple[B: Encoder](name: String)(
      se: List[JsonRpcServerEndpoint[IO]]
  )(request: List[B], expectedResponse: List[JsonRpcResponse[Json]]): Unit = {
    test(name) {
      testMultipleEndpoints(se)
        .use { case (backend, baseUri) =>
          if (expectedResponse.isEmpty) {
            basicRequest
              .post(baseUri)
              .body(request)
              .send(backend)
              .map { response =>
                expect.same(StatusCode.Ok, response.code)
                expect.same(Right(""), response.body)
              }
          } else {
            basicRequest
              .post(baseUri)
              .body(request)
              .response(asJson[List[JsonRpcResponse[Json]]])
              .send(backend)
              .map { response =>
                expect.same(StatusCode.Ok, response.code)
                expect.same(Right(expectedResponse), response.body)
              }
          }
        }
    }
  }

  def testSingleEndpoint[I, E, O](
      endpoint: JsonRpcEndpoint[I, E, O]
  )(logic: I => IO[Either[JsonRpcError[E], O]]): Resource[IO, (SttpBackend[IO, Any], Uri)] = {
    testMultipleEndpoints(List(endpoint.serverLogic(logic)))
  }

  private def testMultipleEndpoints(se: List[JsonRpcServerEndpoint[IO]]): Resource[IO, (SttpBackend[IO, Any], Uri)] = {
    val tapirEndpoints = toInterpreter(se).getOrElse(throw new RuntimeException("Error during conversion to tapir"))
    val routes = Http4sServerInterpreter[IO](Http4sServerOptions.default[IO, IO]).toRoutes(tapirEndpoints)
    testServer(routes)
  }

  override def toInterpreter(se: List[JsonRpcServerEndpoint[IO]]): Either[InterpretationError, ServerEndpoint[Any, IO]] = {
    implicit val catsMonadError: CatsMonadError[IO] = new CatsMonadError
    val tapirInterpreter = new TapirInterpreter[IO, Json](new CirceJsonSupport)
    tapirInterpreter.toTapirEndpoint(se)
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
          import sttp.model.Uri._
          (backend, uri"http://localhost:$port")
        }
      }
  }
}
