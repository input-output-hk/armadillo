package io.iohk.armadillo.circefs2

import cats.effect.{ExitCode, IO, IOApp}
import fs2.io.net.unixsocket.{UnixSocketAddress, UnixSockets}
import io.circe.Json
import io.iohk.armadillo._
import io.iohk.armadillo.fs2.Fs2Interpreter
import io.iohk.armadillo.json.circe._

object Main extends IOApp {
  val hello_in_int_out_string: JsonRpcEndpoint[Int, Unit, String] = jsonRpcEndpoint(m"hello")
    .in(param[Int]("param1"))
    .out[String]("response")

  override def run(args: List[String]): IO[ExitCode] = {
    val address = UnixSocketAddress("./example/circeFs2/fs2-unix-sockets-test.sock")

    val se = hello_in_int_out_string.serverLogic[IO](int => IO.pure(Right(int.toString)))
    val jsonRpcServer = new Fs2Interpreter[IO, Json](new CirceJsonSupport).toFs2Pipe(List(se)).getOrElse(???)

    UnixSockets[IO]
      .server(address)
      .flatMap { client =>
        client.reads
          .through(jsonRpcServer)
          .through(client.writes)
      }
      .compile
      .drain
      .as(ExitCode.Success)
  }
}
