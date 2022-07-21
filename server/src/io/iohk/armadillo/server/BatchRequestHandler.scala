package io.iohk.armadillo.server

import cats.syntax.all._
import io.iohk.armadillo.server.JsonSupport.Json
import io.iohk.armadillo.server.ServerInterpreter.{ResponseHandlingStatus, ServerResponse}
import io.iohk.armadillo.server.Utils.RichMonadErrorOps
import sttp.monad.MonadError
import sttp.monad.syntax._

trait BatchRequestHandler[F[_], Raw] {
  def apply(next: RequestHandler[F, Raw], requests: List[Json.JsonObject[Raw]], jsonSupport: JsonSupport[Raw])(implicit
      monad: MonadError[F]
  ): F[ResponseHandlingStatus[Raw]]
}

object BatchRequestHandler {
  def default[F[_], Raw]: BatchRequestHandler[F, Raw] = new BatchRequestHandler[F, Raw] {
    override def apply(next: RequestHandler[F, Raw], requests: List[Json.JsonObject[Raw]], jsonSupport: JsonSupport[Raw])(implicit
        monad: MonadError[F]
    ): F[ResponseHandlingStatus[Raw]] = {
      requests
        .foldRight(monad.unit(List.empty[ResponseHandlingStatus[Raw]])) { case (req, accF) =>
          val fb = next.onDecodeSuccess(req)
          fb.map2(accF)(_ :: _)
        }
        .map { responseStatuses =>
          val combinedResponseStatus = responseStatuses.traverse { // TODO add test when one batch request element is still unhandled
            case ResponseHandlingStatus.Handled(resp) => Right(resp)
            case ResponseHandlingStatus.Unhandled     => Left(())
          }

          combinedResponseStatus match {
            case Left(_) => ResponseHandlingStatus.Unhandled
            case Right(responses) =>
              val responsesWithoutNotifications = responses.collect { case Some(response) => response.body }
              if (responsesWithoutNotifications.isEmpty) {
                ResponseHandlingStatus.Handled(none)
              } else {
                ResponseHandlingStatus.Handled(ServerResponse.Success(jsonSupport.asArray(responsesWithoutNotifications.toVector)).some)
              }
          }
        }
    }
  }
}
