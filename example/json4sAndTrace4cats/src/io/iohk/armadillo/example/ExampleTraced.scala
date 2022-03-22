package io.iohk.armadillo.example

import cats.Applicative
import cats.data.Kleisli
import cats.effect.kernel.{MonadCancelThrow, Resource, Sync}
import cats.effect.{ExitCode, IO, IOApp}
import io.iohk.armadillo.Armadillo.{JsonRpcError, jsonRpcEndpoint, param}
import io.iohk.armadillo.json.json4s.*
import io.iohk.armadillo.tapir.TapirInterpreter
import io.iohk.armadillo.trace4cats.syntax.*
import io.iohk.armadillo.{JsonRpcServerEndpoint, MethodName}
import io.janstenpickle.trace4cats.Span
import io.janstenpickle.trace4cats.base.context.Provide
import io.janstenpickle.trace4cats.inject.{EntryPoint, Trace}
import io.janstenpickle.trace4cats.kernel.SpanSampler
import io.janstenpickle.trace4cats.log.LogSpanCompleter
import io.janstenpickle.trace4cats.model.TraceProcess
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

  case class RpcBlockResponse(number: Int)

  class Endpoints[F[_]: Sync, G[_]: MonadCancelThrow: Trace] {

    val getBlockByNumber = jsonRpcEndpoint(MethodName("eth_getBlockByNumber"))
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
    val endpoints = new Endpoints[IO, Kleisli[IO, Span[IO], *]]
    val routesR = for {
      completer <- Resource.eval(LogSpanCompleter.create[IO](TraceProcess("example")))
      ep = EntryPoint(SpanSampler.always[IO], completer)
      tapirInterpreter = TapirInterpreter[IO, JValue](
        endpoints.tracedEndpoints(ep),
        Json4sSupport(org.json4s.jackson.parseJson(_), org.json4s.jackson.compactJson)
      )(new CatsMonadError).getOrElse(???)
      tapirEndpoints = tapirInterpreter.toTapirEndpoint
      routes = Http4sServerInterpreter[IO](Http4sServerOptions.default[IO, IO]).toRoutes(tapirEndpoints)
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
