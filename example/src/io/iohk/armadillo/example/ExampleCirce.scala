package io.iohk.armadillo.example

import cats.effect.{ExitCode, IO, IOApp}
import io.circe.generic.semiauto.*
import io.circe.{Decoder, Encoder, Json}
import io.iohk.armadillo.Armadillo.{JsonRpcError, JsonRpcErrorWithData, JsonRpcRequest, jsonRpcEndpoint, param}
import io.iohk.armadillo.json.circe.*
import io.iohk.armadillo.tapir.TapirInterpreter
import io.iohk.armadillo.{JsonRpcServerEndpoint, MethodName}
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.server.Router
import sttp.client3.asynchttpclient.cats.AsyncHttpClientCatsBackend
import sttp.model.Uri
import sttp.tapir.integ.cats.*
import sttp.tapir.internal.ParamsAsVector
import sttp.tapir.server.http4s.{Http4sServerInterpreter, Http4sServerOptions}
import sttp.tapir.{DecodeResult, Schema}

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
      IO.delay(Left(JsonRpcErrorWithData[Unit](1, "q", int)))
    }

  override def run(args: List[String]): IO[ExitCode] = {
    val tapirInterpreter = new TapirInterpreter[IO, Json](new CirceJsonSupport)(new CatsMonadError)
    val tapirEndpoints = tapirInterpreter.apply(List(endpoint))
    val routes = Http4sServerInterpreter[IO](Http4sServerOptions.default[IO, IO]).toRoutes(tapirEndpoints)
    implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global
    import sttp.tapir.client.sttp.SttpClientInterpreter

    import cats.syntax.all._
    val x = List[Int](1).traverse(a => Option.empty[Int])

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
        sttpClient.apply(JsonRpcRequest("a", "eth_getBlockByNumber", ParamsAsVector(Vector(123, "true")), 1)).map {
          case failure: DecodeResult.Failure => println(s"response decoding failure $failure")
          case DecodeResult.Value(v) =>
            v match {
              case Left(value) =>
                println(s"error response: ${value.error.noSpaces}")
              case Right(value) =>
                println(s"response ${value.result.noSpaces}")
            }
        } >> IO.never
      }
      .as(ExitCode.Success)
  }
}
