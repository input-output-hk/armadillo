package io.iohk.armadillo.server

import io.iohk.armadillo.server.JsonSupport.Json
import io.iohk.armadillo.server.Utils.RichMonadErrorOps
import sttp.monad.MonadError
import sttp.monad.syntax._

trait BatchRequestHandler[F[_], Raw] {
  def apply(next: RequestHandler[F, Raw], requests: List[Json.JsonObject[Raw]], jsonSupport: JsonSupport[Raw])(implicit
      monad: MonadError[F]
  ): F[Option[Raw]]
}

object BatchRequestHandler {
  def default[F[_], Raw]: BatchRequestHandler[F, Raw] = new BatchRequestHandler[F, Raw] {
    override def apply(next: RequestHandler[F, Raw], requests: List[Json.JsonObject[Raw]], jsonSupport: JsonSupport[Raw])(implicit
        monad: MonadError[F]
    ): F[Option[Raw]] = {
      requests
        .foldRight(monad.unit(List.empty[Option[Raw]])) { case (req, accF) =>
          val fb = next.onDecodeSuccess(req)
          fb.map2(accF)(_ :: _)
        }
        .map { responses =>
          val withoutNotifications = responses.flatten
          if (withoutNotifications.isEmpty) {
            Option.empty[Raw]
          } else {
            Some(jsonSupport.asArray(withoutNotifications.toVector))
          }
        }
    }
  }
}
