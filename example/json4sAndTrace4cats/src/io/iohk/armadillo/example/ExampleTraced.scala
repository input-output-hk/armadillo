package io.iohk.armadillo.example

import _root_.trace4cats.context.Provide
import _root_.trace4cats.log.LogSpanCompleter
import _root_.trace4cats.{EntryPoint, Span, SpanSampler, Trace, TraceProcess}
import cats.Applicative
import cats.data.Kleisli
import cats.effect.kernel.{MonadCancelThrow, Resource, Sync}
import cats.effect.{ExitCode, IO, IOApp}
import io.iohk.armadillo.json.json4s._
import io.iohk.armadillo.server.tapir.TapirInterpreter
import io.iohk.armadillo.trace4cats.syntax._
import io.iohk.armadillo.{JsonRpcServerEndpoint, _}
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.server.Router
import org.json4s.{Formats, JValue, NoTypeHints, Serialization}
import sttp.tapir.Schema
import sttp.tapir.integ.cats.CatsMonadError
import sttp.tapir.server.http4s.{Http4sServerInterpreter, Http4sServerOptions}

import scala.concurrent.ExecutionContext

object ExampleTraced extends IOApp {
  implicit val rpcBlockResponseSchema: Schema[RpcBlockResponse] = Schema.derived
  implicit val serialization: Serialization = org.json4s.jackson.Serialization
  implicit val formats: Formats = org.json4s.jackson.Serialization.formats(NoTypeHints)
  implicit val json4sSupport: Json4sSupport = Json4sSupport(org.json4s.jackson.parseJson(_), org.json4s.jackson.compactJson)

  case class RpcBlockResponse(number: Int)

  class Endpoints[F[_]: Sync, G[_]: MonadCancelThrow: Trace] {

    val getBlockByNumber = jsonRpcEndpoint(m"eth_getBlockByNumber")
      .in(
        param[Int]("blockNumber").and(param[String]("includeTransactions"))
      )
      .out[Option[RpcBlockResponse]]("blockResponse")
      .serverLogic[G] { case (int, string) =>
        println("user logic")
        println(s"with input ${int + 123} ${string.toUpperCase}")
        Applicative[G].pure(Left(JsonRpcError(11, "qwe", 11)))
      }

    def tracedEndpoints(entryPoint: EntryPoint[F])(implicit P: Provide[F, G, Span[F]]): List[JsonRpcServerEndpoint[F]] =
      List(getBlockByNumber.inject(entryPoint))
  }

  override def run(args: List[String]): IO[ExitCode] = {
    implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global
    implicit val catsMonadError: CatsMonadError[IO] = new CatsMonadError
    val endpoints = new Endpoints[IO, Kleisli[IO, Span[IO], *]]
    val routesR = for {
      completer <- Resource.eval(LogSpanCompleter.create[IO](TraceProcess("example")))
      ep = EntryPoint(SpanSampler.always[IO], completer)
      tapirInterpreter = new TapirInterpreter[IO, JValue](json4sSupport)
      tapirEndpoints = tapirInterpreter.toTapirEndpoint(endpoints.tracedEndpoints(ep)).getOrElse(???)
      routes = Http4sServerInterpreter[IO](Http4sServerOptions.default[IO]).toRoutes(tapirEndpoints)
    } yield routes

    routesR.use { routes =>
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
}
