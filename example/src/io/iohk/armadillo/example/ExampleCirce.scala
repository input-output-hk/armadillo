package io.iohk.armadillo.example

import cats.effect.{ExitCode, IO, IOApp}
import cats.implicits.{catsSyntaxEitherId, catsSyntaxOptionId}
import io.circe.generic.semiauto.*
import io.circe.{Decoder, Encoder, Json}
import io.iohk.armadillo.Armadillo.{param, jsonRpcEndpoint}
import io.iohk.armadillo.json.circe.*
import io.iohk.armadillo.tapir.TapirInterpreter
import io.iohk.armadillo.{JsonRpcServerEndpoint, MethodName}
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.server.Router
import sttp.tapir.Schema
import sttp.tapir.integ.cats.*
import sttp.tapir.json.circe.*
import sttp.tapir.server.http4s.{Http4sServerInterpreter, Http4sServerOptions}

import scala.concurrent.ExecutionContext

object ExampleCirce extends IOApp {

  implicit val rpcBlockResponseEncoder: Encoder[RpcBlockResponse] = deriveEncoder
  implicit val rpcBlockResponseDecoder: Decoder[RpcBlockResponse] = deriveDecoder
  implicit val rpcBlockResponseSchema: Schema[RpcBlockResponse] = Schema.derived

  case class RpcBlockResponse(number: Int)

  // List(decode[T1](in: Raw): DecodeResult[T1], decode[T2](in: Raw): DecodeResult[T2])
  //

  val endpoint: JsonRpcServerEndpoint[IO] = jsonRpcEndpoint(MethodName("eth_getBlockByNumber"))
    .in(
      param[Int]("blockNumber").and(param[String]("includeTransactions"))
    )
    .out[Option[RpcBlockResponse]]("blockResponse")
    .serverLogic[IO] { case (int, string) =>
      println("user logic")
      println(s"with input ${int + 123} ${string.toUpperCase}")
      IO.delay(RpcBlockResponse(int).some.asRight)
    }

  override def run(args: List[String]): IO[ExitCode] = {
    val tapirInterpreter = new TapirInterpreter[IO, Json](new CirceJsonSupport)(new CatsMonadError)
    val tapirEndpoints = tapirInterpreter.apply(List(endpoint))
    val routes = Http4sServerInterpreter[IO](Http4sServerOptions.default[IO, IO]).toRoutes(tapirEndpoints)
    implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global

//    IO.unit.as(ExitCode.Success)
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
