package io.iohk.armadillo.example

import cats.effect.{ExitCode, IO, IOApp}
import io.circe.generic.semiauto._
import io.circe.literal._
import io.circe.{Decoder, Encoder, Json}
import io.iohk.armadillo._
import io.iohk.armadillo.json.circe._
import io.iohk.armadillo.server.ServerInterpreter.ResponseHandlingStatus
import io.iohk.armadillo.server._
import io.iohk.armadillo.server.tapir.TapirInterpreter
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.server.Router
import sttp.client3.armeria.cats.ArmeriaCatsBackend
import sttp.model.{StatusCode, Uri}
import sttp.monad.MonadError
import sttp.tapir.integ.cats._
import sttp.tapir.server.http4s.{Http4sServerInterpreter, Http4sServerOptions}
import sttp.tapir.{DecodeResult, Schema}

import scala.concurrent.ExecutionContext

object ExampleCirce extends IOApp {

  implicit val rpcBlockResponseEncoder: Encoder[RpcBlockResponse] = deriveEncoder
  implicit val rpcBlockResponseDecoder: Decoder[RpcBlockResponse] = deriveDecoder
  implicit val rpcBlockResponseSchema: Schema[RpcBlockResponse] = Schema.derived

  case class RpcBlockResponse(number: Int)

  val endpoint: JsonRpcServerEndpoint[IO] = jsonRpcEndpoint(m"eth_getBlockByNumber")
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

    implicit val catsMonadError: CatsMonadError[IO] = new CatsMonadError

    val tapirInterpreter = new TapirInterpreter[IO, Json](
      new CirceJsonSupport,
      List(new LoggingEndpointInterceptor, new LoggingRequestInterceptor, new GenericIOInterceptor[Json])
    )
    val tapirEndpoints = tapirInterpreter.toTapirEndpointUnsafe(List(endpoint))
    val routes = Http4sServerInterpreter[IO](Http4sServerOptions.default[IO]).toRoutes(tapirEndpoints)
    implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global
    import sttp.tapir.client.sttp.SttpClientInterpreter

    BlazeServerBuilder[IO]
      .withExecutionContext(ec)
      .bindHttp(8545, "localhost")
      .withHttpApp(Router("/" -> routes).orNotFound)
      .resource
      .flatMap { _ =>
        ArmeriaCatsBackend.resource[IO]()
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
              case Right((json: Option[Json], code: StatusCode)) =>
                println(s"response ${json.map(_.noSpaces)} code: $code")
              case Right(other: Any) =>
                println(s"response $other")
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
      override def onDecodeSuccess[I, E, O](
          ctx: EndpointHandler.DecodeSuccessContext[IO, I, E, O, Raw]
      )(implicit monad: MonadError[IO]): IO[ResponseHandlingStatus[Raw]] = {
        println(s"onDecodeSuccess ${ctx.endpoint.endpoint.methodName}")
        endpointHandler.onDecodeSuccess(ctx).flatTap(_ => IO.println(s"after onDecodeSuccess ${ctx.endpoint.endpoint.methodName}"))
      }

      override def onDecodeFailure(
          ctx: EndpointHandler.DecodeFailureContext[IO, Raw]
      )(implicit monad: MonadError[IO]): IO[ResponseHandlingStatus[Raw]] = {
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
      override def onDecodeSuccess[I, E, O](
          ctx: EndpointHandler.DecodeSuccessContext[IO, I, E, O, Json]
      )(implicit monad: MonadError[IO]): IO[ResponseHandlingStatus[Json]] = {
        println(s"onDecodeSuccess ${ctx.endpoint.endpoint.methodName}")
        endpointHandler.onDecodeSuccess(ctx).flatTap(_ => IO.println(s"after onDecodeSuccess ${ctx.endpoint.endpoint.methodName}"))
      }

      override def onDecodeFailure(
          ctx: EndpointHandler.DecodeFailureContext[IO, Json]
      )(implicit monad: MonadError[IO]): IO[ResponseHandlingStatus[Json]] = {
        println(s"onDecodeFailure ${ctx.endpoint.endpoint.methodName}")
        endpointHandler.onDecodeFailure(ctx).flatTap(_ => IO.println(s"after onDecodeFailure ${ctx.endpoint.endpoint.methodName}"))
      }
    }
  }
}

class LoggingRequestInterceptor extends RequestInterceptor[IO, Json] {
  override def apply(
      responder: Responder[IO, Json],
      jsonSupport: JsonSupport[Json],
      requestHandler: MethodInterceptor[IO, Json] => RequestHandler[IO, Json]
  ): RequestHandler[IO, Json] = {
    new RequestHandler[IO, Json] {
      override def onDecodeSuccess(request: JsonSupport.Json[Json])(implicit monad: MonadError[IO]): IO[ResponseHandlingStatus[Json]] = {
        requestHandler
          .apply(MethodInterceptor.noop[IO, Json]())
          .onDecodeSuccess(request)
          .flatTap(_ => IO.println("after onDecodeSuccess"))
      }

      override def onDecodeFailure(
          ctx: RequestHandler.DecodeFailureContext
      )(implicit monad: MonadError[IO]): IO[ResponseHandlingStatus[Json]] = {
        requestHandler.apply(MethodInterceptor.noop[IO, Json]()).onDecodeFailure(ctx).flatTap(_ => IO.println("after onDecodeFailure"))
      }
    }
  }
}
