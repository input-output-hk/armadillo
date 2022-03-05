package io.iohk.armadillo.circefs2

import cats.effect.{ExitCode, IO, IOApp}
import fs2.Pipe
import fs2.io.net.unixsocket.{UnixSocketAddress, UnixSockets}
import io.circe.Json
import io.iohk.armadillo._
import io.iohk.armadillo.json.circe._
import io.iohk.armadillo.server.fs2.Fs2Interpreter

object Main extends IOApp {
  val hello_in_int_out_string: JsonRpcEndpoint[Int, Unit, String] = jsonRpcEndpoint(m"hello")
    .in(param[Int]("param1"))
    .out[String]("response")

  override def run(args: List[String]): IO[ExitCode] = {
    val address = UnixSocketAddress("./example/circeFs2/fs2-unix-sockets-test.sock")

    val se = hello_in_int_out_string.serverLogic[IO](int => IO.pure(Right(int.toString)))
    val jsonSupport = new CirceJsonSupport
    val jsonRpcServer: Pipe[IO, String, Json] = new Fs2Interpreter[IO, Json](jsonSupport).toFs2Pipe(List(se)).getOrElse(???)

    UnixSockets[IO]
      .server(address)
      .flatMap { client =>
        client.reads
          .through(fs2.text.utf8.decode)
          .through(jsonRpcServer)
          .map(jsonSupport.stringify)
          .through(fs2.text.utf8.encode)
          .through(client.writes)
      }
      .compile
      .drain
      .as(ExitCode.Success)
  }
}
