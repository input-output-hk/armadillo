package io.iohk.armadillo.example

import cats.effect.{ExitCode, IO, IOApp}
import cats.implicits.{catsSyntaxEitherId, catsSyntaxOptionId}
import io.circe.generic.semiauto.*
import io.circe.{Decoder, Encoder}
import io.iohk.armadillo.Armadillo.{jsonRpcBody, jsonRpcEndpoint}
import io.iohk.armadillo.{JsonRpcServerEndpoint, MethodName}
import io.iohk.armadillo.tapir.{Provider, TapirInterpreter}
import io.iohk.armadillo.json.circe.*
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.server.Router
import sttp.monad.MonadError
import sttp.tapir.Codec.{JsonCodec, boolean}
import sttp.tapir.Schema
import sttp.tapir.json.circe.*
import sttp.tapir.metrics.{EndpointMetric, Metric}
import sttp.tapir.server.http4s.{Http4sServerInterpreter, Http4sServerOptions}
import sttp.tapir.server.interceptor.metrics.MetricsEndpointInterceptor
import sttp.tapir.server.interceptor.{EndpointInterceptor, RequestHandler, RequestInterceptor, Responder}

import scala.concurrent.ExecutionContext

object Main extends IOApp {

  implicit val rpcBlockResponseEncoder: Encoder[RpcBlockResponse] = deriveEncoder
  implicit val rpcBlockResponseDecoder: Decoder[RpcBlockResponse] = deriveDecoder
  implicit val rpcBlockResponseSchema: Schema[RpcBlockResponse] = Schema.derived

  case class RpcBlockResponse(number: BigInt)

  private val endpoint: JsonRpcServerEndpoint[IO] = jsonRpcEndpoint(MethodName("eth_getBlock"))
    .in(jsonRpcBody[BigInt])
    .out(jsonRpcBody[Option[RpcBlockResponse]])
    .serverLogic[IO] { input =>
      IO.delay(RpcBlockResponse(input).some.asRight)
    }

  override def run(args: List[String]): IO[ExitCode] = {
    val value = new TapirInterpreter(
      new Provider[TapirInterpreter.JsonRpcRequest] {
        override def codec[T](bodyCodec: JsonCodec[T]): JsonCodec[TapirInterpreter.JsonRpcRequest[T]] = jsonRpRequestCodec(bodyCodec)
      },
      new Provider[TapirInterpreter.JsonRpcResponse] {
        override def codec[T](bodyCodec: JsonCodec[T]): JsonCodec[TapirInterpreter.JsonRpcResponse[T]] = jsonRpcResponseCodec(bodyCodec)
      }
    )
    val routes = Http4sServerInterpreter[IO](serverOpts).toRoutes(value.apply(List(endpoint)))
    implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global

    BlazeServerBuilder[IO]
      .withExecutionContext(ec)
      .bindHttp(8545, "localhost")
      .withHttpApp(Router("/" -> routes).orNotFound)
      .resource
      .use { _ =>
        IO.never
      }
      .as(ExitCode.Success)
  }
}
