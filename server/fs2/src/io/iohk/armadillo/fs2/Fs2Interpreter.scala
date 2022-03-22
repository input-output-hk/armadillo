package io.iohk.armadillo.fs2

import cats.effect.kernel.Async
import io.iohk.armadillo.JsonRpcServerEndpoint
import io.iohk.armadillo.server.ServerInterpreter.{InterpretationError, Result}
import io.iohk.armadillo.server.{JsonSupport, ServerInterpreter}
import sttp.tapir.integ.cats.CatsMonadError

class Fs2Interpreter[F[_]: Async, Raw] private (serverInterpreter: ServerInterpreter[F, Raw], jsonSupport: JsonSupport[Raw]) {

  def toFs2Pipe: fs2.Pipe[F, Byte, Byte] = { stream =>
    stream
      .through(fs2.text.utf8.decode)
      .flatMap { request =>
        fs2.Stream
          .eval(serverInterpreter.dispatchRequest(request))
          .collect { case Result.RequestResponse(response) => response }
          .map(jsonSupport.stringify)
      }
      .through(fs2.text.utf8.encode)
  }
}

object Fs2Interpreter {
  def apply[F[_]: Async, Raw](
      jsonRpcEndpoints: List[JsonRpcServerEndpoint[F]],
      jsonSupport: JsonSupport[Raw]
  ): Either[InterpretationError, Fs2Interpreter[F, Raw]] = {
    implicit val monadError: CatsMonadError[F] = new CatsMonadError[F]
    ServerInterpreter(jsonRpcEndpoints, jsonSupport).map(new Fs2Interpreter(_, jsonSupport))
  }
}
