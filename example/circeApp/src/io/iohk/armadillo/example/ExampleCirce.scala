package io.iohk.armadillo.example

import cats.effect.{ExitCode, IO, IOApp}
import io.circe.generic.semiauto.*
import io.circe.literal.*
import io.circe.{Decoder, Encoder, Json}
import io.iohk.armadillo.Armadillo.{JsonRpcError, JsonRpcResponse, jsonRpcEndpoint, param}
import io.iohk.armadillo.json.circe.*
import io.iohk.armadillo.server.{EndpointHandler, EndpointInterceptor, JsonSupport, RequestHandler, RequestInterceptor, Responder}
import io.iohk.armadillo.tapir.TapirInterpreter
import io.iohk.armadillo.{JsonRpcServerEndpoint, MethodName}
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.server.Router
import sttp.client3.asynchttpclient.cats.AsyncHttpClientCatsBackend
import sttp.model.Uri
import sttp.monad.MonadError
import sttp.tapir.integ.cats.*
import sttp.tapir.server.http4s.{Http4sServerInterpreter, Http4sServerOptions}
import sttp.tapir.{DecodeResult, Schema}

import scala.concurrent.ExecutionContext

object ExampleCirce extends IOApp {

  implicit val rpcBlockResponseEncoder: Encoder[RpcBlockResponse] = deriveEncoder
  implicit val rpcBlockResponseDecoder: Decoder[RpcBlockResponse] = deriveDecoder
  implicit val rpcBlockResponseSchema: Schema[RpcBlockResponse] = Schema.derived

  case class RpcBlockResponse(number: Int)

  val endpoint: JsonRpcServerEndpoint[IO] = jsonRpcEndpoint(MethodName("eth_getBlockByNumber"))
    .in(
      param[Int]("blockNumber").and(param[String]("includeTransactions"))
    )
    .out[Option[RpcBlockResponse]]("blockResponse")
    .serverLogic[IO] { case (int, string) =>
      println("user logic")
      println(s"with input ${int + 123} ${string.toUpperCase}")
      IO.delay(Left(JsonRpcError[Unit](1, "q", int)))
    }

  override def run(args: List[String]): IO[ExitCode] = {

    val tapirInterpreter =
      new TapirInterpreter[IO, Json](
        new CirceJsonSupport,
        List(new LoggingEndpointInterceptor, new LoggingRequestInterceptor, new GenericIOInterceptor[Json])
      )(
        new CatsMonadError
      )
    val tapirEndpoints = tapirInterpreter.toTapirEndpoint(List(endpoint)).getOrElse(???)
    val routes = Http4sServerInterpreter[IO](Http4sServerOptions.default[IO, IO]).toRoutes(tapirEndpoints)
    implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global
    import sttp.tapir.client.sttp.SttpClientInterpreter

    BlazeServerBuilder[IO]
      .withExecutionContext(ec)
      .bindHttp(8545, "localhost")
      .withHttpApp(Router("/" -> routes).orNotFound)
      .resource
      .flatMap { _ =>
        AsyncHttpClientCatsBackend.resource[IO]()
      }
      .use { client =>
        val sttpClient = SttpClientInterpreter().toClient(tapirEndpoints.endpoint, Some(Uri.apply("localhost", 8545)), client)
        sttpClient.apply(json"""{"jsonrpc": "2.0", "method": "eth_getBlockByNumber", "params": [123, "true"], "id": 1}""".noSpaces).map {
          case failure: DecodeResult.Failure =>
            println(s"response decoding failure $failure")
          case DecodeResult.Value(v) =>
            v match {
              case Left(value) =>
                println(s"error response: $value")
              case Right(value) =>
                println(s"response ${value.noSpaces}")
            }
        } >> IO.never
      }
      .as(ExitCode.Success)
  }
}

class GenericIOInterceptor[Raw] extends EndpointInterceptor[IO, Raw] {
  override def apply(
      responder: Responder[IO, Raw],
      jsonSupport: JsonSupport[Raw],
      endpointHandler: EndpointHandler[IO, Raw]
  ): EndpointHandler[IO, Raw] = {
    new EndpointHandler[IO, Raw] {
      override def onDecodeSuccess[I](
          ctx: EndpointHandler.DecodeSuccessContext[IO, I, Raw]
      )(implicit monad: MonadError[IO]): IO[Option[Raw]] = {
        println(s"onDecodeSuccess ${ctx.endpoint.endpoint.methodName}")
        endpointHandler.onDecodeSuccess(ctx).flatTap(_ => IO.println(s"after onDecodeSuccess ${ctx.endpoint.endpoint.methodName}"))
      }

      override def onDecodeFailure(ctx: EndpointHandler.DecodeFailureContext[IO, Raw])(implicit monad: MonadError[IO]): IO[Option[Raw]] = {
        println(s"onDecodeFailure ${ctx.endpoint.endpoint.methodName}")
        endpointHandler.onDecodeFailure(ctx).flatTap(_ => IO.println(s"after onDecodeFailure ${ctx.endpoint.endpoint.methodName}"))
      }
    }
  }
}

class LoggingEndpointInterceptor extends EndpointInterceptor[IO, Json] {

  override def apply(
      responder: Responder[IO, Json],
      jsonSupport: JsonSupport[Json],
      endpointHandler: EndpointHandler[IO, Json]
  ): EndpointHandler[IO, Json] = {
    new EndpointHandler[IO, Json] {
      override def onDecodeSuccess[I](
          ctx: EndpointHandler.DecodeSuccessContext[IO, I, Json]
      )(implicit monad: MonadError[IO]): IO[Option[Json]] = {
        println(s"onDecodeSuccess ${ctx.endpoint.endpoint.methodName}")
        endpointHandler.onDecodeSuccess(ctx).flatTap(_ => IO.println(s"after onDecodeSuccess ${ctx.endpoint.endpoint.methodName}"))
      }

      override def onDecodeFailure(
          ctx: EndpointHandler.DecodeFailureContext[IO, Json]
      )(implicit monad: MonadError[IO]): IO[Option[Json]] = {
        println(s"onDecodeFailure ${ctx.endpoint.endpoint.methodName}")
        endpointHandler.onDecodeFailure(ctx).flatTap(_ => IO.println(s"after onDecodeFailure ${ctx.endpoint.endpoint.methodName}"))
      }
    }
  }
}

class LoggingRequestInterceptor extends RequestInterceptor[IO, Json] {
  override def apply(
      responder: Responder[IO, Json],
      requestHandler: EndpointInterceptor[IO, Json] => RequestHandler[IO, Json]
  ): RequestHandler[IO, Json] = {
    new RequestHandler[IO, Json] {
      override def onDecodeSuccess(request: JsonSupport.Json[Json])(implicit monad: MonadError[IO]): IO[Option[Json]] = {
        println(s"onDecodeSuccess $request")
        //        responder
        //          .apply(Some(JsonRpcResponse.error_v2[Raw](jsonSupport.jsNull, None)))
        requestHandler
          .apply(EndpointInterceptor.noop)
          .onDecodeSuccess(request)
          .flatTap(_ => IO.println("after onDecodeSuccess"))
      }

      override def onDecodeFailure(ctx: RequestHandler.DecodeFailureContext): IO[Option[Json]] = {
        println(s"onDecodeFailure $ctx")
        requestHandler.apply(EndpointInterceptor.noop).onDecodeFailure(ctx).flatTap(_ => IO.println("after onDecodeFailure"))
      }
    }
  }
}
