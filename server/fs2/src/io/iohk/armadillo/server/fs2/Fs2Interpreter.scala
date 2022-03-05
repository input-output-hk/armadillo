package io.iohk.armadillo.server.fs2

import cats.effect.kernel.Async
import io.iohk.armadillo.JsonRpcServerEndpoint
import io.iohk.armadillo.server.ServerInterpreter.{InterpretationError, ServerResponse}
import io.iohk.armadillo.server.{CustomInterceptors, Interceptor, JsonSupport, ServerInterpreter}
import sttp.tapir.integ.cats.CatsMonadError

class Fs2Interpreter[F[_]: Async, Raw](
    jsonSupport: JsonSupport[Raw],
    interceptors: List[Interceptor[F, Raw]] = CustomInterceptors[F, Raw]().interceptors
) {

  def toFs2Pipe(jsonRpcEndpoints: List[JsonRpcServerEndpoint[F]]): Either[InterpretationError, fs2.Pipe[F, String, Raw]] = {
    implicit val monadError: CatsMonadError[F] = new CatsMonadError[F]
    ServerInterpreter(jsonRpcEndpoints, jsonSupport, interceptors).map(si => stream => stream.through(toFs2Unsafe(si)))
  }

  private def toFs2Unsafe(serverInterpreter: ServerInterpreter[F, Raw]): fs2.Pipe[F, String, Raw] = { stream =>
    stream
      .flatMap { request =>
        fs2.Stream
          .eval(serverInterpreter.dispatchRequest(request))
          .collect {
            case Some(ServerResponse.Success(response)) => response
            case Some(ServerResponse.Failure(response)) => response
          }
      }
  }
}
