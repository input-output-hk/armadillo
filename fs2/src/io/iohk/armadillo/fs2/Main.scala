package io.iohk.armadillo.fs2

import cats.effect.{ExitCode, IO, IOApp}
import fs2.io.net.unixsocket.{UnixSocketAddress, UnixSockets}
import io.circe.Json
import io.iohk.armadillo.Armadillo.{jsonRpcEndpoint, param}
import io.iohk.armadillo.{JsonRpcEndpoint, MethodName}
import io.iohk.armadillo.json.circe.*

object Main extends IOApp {
  val hello_in_int_out_string: JsonRpcEndpoint[Int, Unit, String] = jsonRpcEndpoint(MethodName("hello"))
    .in(param[Int]("param1"))
    .out[String]("response")

  override def run(args: List[String]): IO[ExitCode] = {
    val address = UnixSocketAddress("fs2-unix-sockets-test.sock")

    val inter = new Fs2Interpreter[IO, Json](new CirceJsonSupport)
    val se = hello_in_int_out_string.serverLogic[IO](int => IO.pure(Right(int.toString)))

    UnixSockets[IO]
      .server(address)
      .flatMap { client =>
        client.reads
          .through(inter.toFs2Pipe(List(se)))
          .through(client.writes)
      }
      .compile
      .drain
      .as(ExitCode.Success)
  }
}
