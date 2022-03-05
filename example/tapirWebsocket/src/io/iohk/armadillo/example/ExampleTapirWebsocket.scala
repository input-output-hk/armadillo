package io.iohk.armadillo.example

import cats.effect.{ExitCode, IO, IOApp}
import fs2.Pipe
import io.circe.Json
import io.circe.literal._
import io.iohk.armadillo._
import io.iohk.armadillo.json.circe._
import io.iohk.armadillo.server.fs2.Fs2Interpreter
import org.http4s.HttpRoutes
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.server.Router
import org.http4s.server.websocket.WebSocketBuilder2
import sttp.capabilities.WebSockets
import sttp.capabilities.fs2.Fs2Streams
import sttp.client3.asynchttpclient.fs2.AsyncHttpClientFs2Backend
import sttp.client3.{UriContext, asWebSocket, basicRequest}
import sttp.tapir.integ.cats._
import sttp.tapir.json.circe._
import sttp.tapir.server.http4s.Http4sServerInterpreter
import sttp.tapir.{CodecFormat, PublicEndpoint, webSocketBody}
import sttp.ws.WebSocket

import scala.concurrent.ExecutionContext

object ExampleTapirWebsocket extends IOApp {

  val endpoint: JsonRpcServerEndpoint[IO] = jsonRpcEndpoint(m"greet")
    .in(
      param[String]("name")
    )
    .out[String]("greeting")
    .serverLogic[IO](name => IO.delay(Right(s"Hello $name")))

  override def run(args: List[String]): IO[ExitCode] = {
    implicit val catsMonadError: CatsMonadError[IO] = new CatsMonadError
    implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global

    val jsonRpcServer: Pipe[IO, String, Json] = new Fs2Interpreter[IO, Json](new CirceJsonSupport).toFs2Pipe(List(endpoint)).getOrElse(???)

    val wsEndpoint: PublicEndpoint[Unit, Unit, Pipe[IO, String, Json], Fs2Streams[IO] with WebSockets] =
      sttp.tapir.endpoint.get.out(webSocketBody[String, CodecFormat.TextPlain, Json, CodecFormat.Json](Fs2Streams[IO]))

    val wsRoutes: WebSocketBuilder2[IO] => HttpRoutes[IO] =
      Http4sServerInterpreter[IO]().toWebSocketRoutes(wsEndpoint.serverLogicSuccess(_ => IO.pure(jsonRpcServer)))

    BlazeServerBuilder[IO]
      .withExecutionContext(ec)
      .bindHttp(8001, "localhost")
      .withHttpWebSocketApp(wsb => Router("/" -> wsRoutes(wsb)).orNotFound)
      .resource
      .flatMap { _ =>
        AsyncHttpClientFs2Backend.resource[IO]()
      }
      .use { backend =>
        // Client which interacts with the web socket
        basicRequest
          .response(asWebSocket { ws: WebSocket[IO] =>
            for {
              _ <- IO.println("sending request")
              _ <- ws.sendText(json"""{"jsonrpc": "2.0", "method": "greet", "params": ["John"], "id": 1 }""".noSpaces)
              r1 <- ws.receiveText()
              _ = println(s"received response: $r1")
            } yield ()
          })
          .get(uri"ws://localhost:8001/")
          .send(backend)
      }
      .as(ExitCode.Success)
  }
}
