package io.iohk.armadillo.server.tapir.http4s

import cats.effect.IO
import cats.effect.kernel.Resource
import io.circe.{Encoder, Json, parser}
import io.iohk.armadillo._
import io.iohk.armadillo.json.circe.CirceJsonSupport
import io.iohk.armadillo.server.ServerInterpreter.{InterpretationError, ServerResponse}
import io.iohk.armadillo.server.tapir.TapirInterpreter
import io.iohk.armadillo.server.{AbstractCirceSuite, CirceEndpoints}
import org.http4s.HttpRoutes
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.server.Router
import sttp.client3.armeria.cats.ArmeriaCatsBackend
import sttp.client3.circe._
import sttp.client3.{DeserializationException, HttpError, StringBody, SttpBackend, basicRequest}
import sttp.model.{MediaType, StatusCode, Uri}
import sttp.tapir.integ.cats.CatsMonadError
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.http4s.{Http4sServerInterpreter, Http4sServerOptions}
import weaver.TestName

import scala.concurrent.ExecutionContext

trait BaseSuite extends AbstractCirceSuite[StringBody, ServerEndpoint[Any, IO]] with CirceEndpoints {

  override def invalidJson: StringBody =
    StringBody("""{"jsonrpc": "2.0", "method": "foobar, "params": "bar", "baz]""", "utf-8", MediaType.ApplicationJson)

  override def jsonNotAnObject: StringBody = StringBody("""["asd"]""", "utf-8", MediaType.ApplicationJson)

  def testNotification[I, E, O](
      endpoint: JsonRpcEndpoint[I, E, O],
      suffix: TestName
  )(
      f: I => IO[Either[E, O]]
  )(request: JsonRpcRequest[Json]): Unit = {
    test(suffix.copy(endpoint.showDetail + " as notification " + suffix.name)) {
      testSingleEndpoint(endpoint)(f)
        .use { case (backend, baseUri) =>
          basicRequest
            .post(baseUri)
            .body(request)
            .send(backend)
            .map { response =>
              expect.same(StatusCode.NoContent, response.code)
            }
        }
    }
  }

  def testInvalidRequest[I, E, O](suffix: TestName)(request: StringBody, expectedResponse: JsonRpcResponse[Json]): Unit = {
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
                  case success @ JsonRpcSuccessResponse(_, _, _) => ServerResponse.Success(jsonSupport.encodeResponse(success))
                  case error @ JsonRpcErrorResponse(_, _, _)     => ServerResponse.Failure(jsonSupport.encodeResponse(error))
                },
                response.body match {
                  case Left(error) =>
                    error match {
                      case HttpError(body, _)             => ServerResponse.Failure(parser.parse(body).toOption.get)
                      case DeserializationException(_, _) => throw new RuntimeException("DeserializationException was not expected")
                    }
                  case Right(body) =>
                    body match {
                      case result @ JsonRpcSuccessResponse(_, _, _) => ServerResponse.Success(jsonSupport.encodeResponse(result))
                      case error @ JsonRpcErrorResponse(_, _, _)    => ServerResponse.Failure(jsonSupport.encodeResponse(error))
                    }
                }
              )
            }
        }
    }
  }

  def test[I, E, O, B: Encoder](
      endpoint: JsonRpcEndpoint[I, E, O],
      suffix: TestName = ""
  )(
      f: I => IO[Either[E, O]]
  )(request: B, expectedResponse: JsonRpcResponse[Json]): Unit = {
    test(suffix.copy(name = endpoint.showDetail + " " + suffix.name)) {
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
                  case success @ JsonRpcSuccessResponse(_, _, _) => ServerResponse.Success(jsonSupport.encodeResponse(success))
                  case error @ JsonRpcErrorResponse(_, _, _)     => ServerResponse.Failure(jsonSupport.encodeResponse(error))
                },
                response.body match {
                  case Left(error) =>
                    error match {
                      case HttpError(body, _)             => ServerResponse.Failure(parser.parse(body).toOption.get)
                      case DeserializationException(_, _) => throw new RuntimeException("DeserializationException was not expected")
                    }
                  case Right(body) =>
                    body match {
                      case result @ JsonRpcSuccessResponse(_, _, _) => ServerResponse.Success(jsonSupport.encodeResponse(result))
                      case error @ JsonRpcErrorResponse(_, _, _)    => ServerResponse.Failure(jsonSupport.encodeResponse(error))
                    }
                }
              )
            }
        }
    }
  }

  override def testServerError[I, E, O](
      endpoint: JsonRpcEndpoint[I, E, O],
      suffix: TestName
  )(
      f: I => IO[Either[E, O]]
  )(request: JsonRpcRequest[Json], expectedResponse: JsonRpcResponse[Json]): Unit = {
    test(suffix.copy(name = endpoint.showDetail + " " + suffix.name)) {
      testSingleEndpoint(endpoint)(f)
        .use { case (backend, baseUri) =>
          basicRequest
            .post(baseUri)
            .body(request)
            .response(asJson[JsonRpcResponse[Json]])
            .send(backend)
            .map { response =>
              expect.same(
                ServerResponse.ServerFailure(jsonSupport.encodeResponse(expectedResponse)),
                response.body match {
                  case Left(error) =>
                    error match {
                      case HttpError(body, _)             => ServerResponse.ServerFailure(parser.parse(body).toOption.get)
                      case DeserializationException(_, _) => throw new RuntimeException("DeserializationException was not expected")
                    }
                  case Right(body) =>
                    body match {
                      case result @ JsonRpcSuccessResponse(_, _, _) => ServerResponse.Success(jsonSupport.encodeResponse(result))
                      case error @ JsonRpcErrorResponse(_, _, _)    => ServerResponse.Failure(jsonSupport.encodeResponse(error))
                    }
                }
              )
            }
        }
    }
  }

  def testMultiple[B: Encoder](name: TestName)(
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
                expect.same(StatusCode.NoContent, response.code)
                expect.same(Right(0), response.body.map(_.length))
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
  )(logic: I => IO[Either[E, O]]): Resource[IO, (SttpBackend[IO, Any], Uri)] = {
    testMultipleEndpoints(List(endpoint.serverLogic(logic)))
  }

  private def testMultipleEndpoints(se: List[JsonRpcServerEndpoint[IO]]): Resource[IO, (SttpBackend[IO, Any], Uri)] = {
    val tapirEndpoints = toInterpreter(se).getOrElse(throw new RuntimeException("Error during conversion to tapir"))
    val routes = Http4sServerInterpreter[IO](Http4sServerOptions.default[IO]).toRoutes(tapirEndpoints)
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
        ArmeriaCatsBackend.resource[IO]().map { backend =>
          import sttp.model.Uri._
          (backend, uri"http://localhost:$port")
        }
      }
  }
}
