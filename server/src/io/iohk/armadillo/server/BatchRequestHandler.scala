package io.iohk.armadillo.server

import io.iohk.armadillo.server.JsonSupport.Json
import io.iohk.armadillo.server.ServerInterpreter.{DecodeAction, ServerInterpreterResponse}
import io.iohk.armadillo.server.Utils.RichMonadErrorOps
import sttp.monad.MonadError
import sttp.monad.syntax._

trait BatchRequestHandler[F[_], Raw] {
  def apply(next: RequestHandler[F, Raw], requests: List[Json.JsonObject[Raw]], jsonSupport: JsonSupport[Raw])(implicit
      monad: MonadError[F]
  ): F[DecodeAction[Raw]]
}

object BatchRequestHandler {
  def default[F[_], Raw]: BatchRequestHandler[F, Raw] = new BatchRequestHandler[F, Raw] {
    override def apply(next: RequestHandler[F, Raw], requests: List[Json.JsonObject[Raw]], jsonSupport: JsonSupport[Raw])(implicit
        monad: MonadError[F]
    ): F[DecodeAction[Raw]] = {
      requests
        .foldRight(monad.unit(List.empty[DecodeAction[Raw]])) { case (req, accF) =>
          val fb = next.onDecodeSuccess(req)
          fb.map2(accF)(_ :: _)
        }
        .map { decodeActions =>
          val responsesWithoutNotification = decodeActions.collect {
            case result @ DecodeAction.ActionTaken(ServerInterpreterResponse.Result(raw)) => raw
            case error @ DecodeAction.ActionTaken(ServerInterpreterResponse.Error(raw))   => raw
          }

          if (responsesWithoutNotification.isEmpty) {
            DecodeAction.ActionTaken(ServerInterpreterResponse.None())
          } else {
            DecodeAction.ActionTaken(ServerInterpreterResponse.Result(jsonSupport.asArray(responsesWithoutNotification.toVector)))
          }
        }
    }
  }
}
