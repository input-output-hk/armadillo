package io.iohk.armadillo.example

import cats.effect.{ExitCode, IO, IOApp}
import cats.syntax.all._
import io.iohk.armadillo._
import io.iohk.armadillo.json.json4s._
import io.iohk.armadillo.tapir.TapirInterpreter
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.server.Router
import org.json4s.{Formats, JValue, NoTypeHints, Serialization}
import sttp.tapir.Schema
import sttp.tapir.integ.cats.CatsMonadError
import sttp.tapir.server.http4s.{Http4sServerInterpreter, Http4sServerOptions}

import scala.concurrent.ExecutionContext

object ExampleJson4s extends IOApp {
  implicit val rpcBlockResponseSchema: Schema[RpcBlockResponse] = Schema.derived
  implicit val serialization: Serialization = org.json4s.jackson.Serialization
  implicit val formats: Formats = org.json4s.jackson.Serialization.formats(NoTypeHints)

  case class RpcBlockResponse(number: Int)

  val endpoint: JsonRpcServerEndpoint[IO] = jsonRpcEndpoint(m"eth_getBlockByNumber")
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
    implicit val catsMonadError: CatsMonadError[IO] = new CatsMonadError
    val tapirInterpreter = new TapirInterpreter[IO, JValue](
      Json4sSupport(org.json4s.jackson.parseJson(_), org.json4s.jackson.compactJson)
    )
    val tapirEndpoints = tapirInterpreter.toTapirEndpoint(List(endpoint)).getOrElse(???)
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
