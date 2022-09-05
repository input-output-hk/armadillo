package io.iohk.armadillo.testing

import io.iohk.armadillo.JsonRpcServerEndpoint
import io.iohk.armadillo.server.{Interceptor, JsonSupport}
import io.iohk.armadillo.tapir.TapirInterpreter
import sttp.client3.testing.SttpBackendStub
import sttp.client3.{Request, Response}
import sttp.model.StatusCode
import sttp.monad.MonadError
import sttp.monad.syntax._
import sttp.tapir.server.ServerEndpoint.Full
import sttp.tapir.server.stub.TapirStubInterpreter

class StubServerInterpreter[F[_]: MonadError, Raw, R](
    endpoints: List[JsonRpcServerEndpoint[F]],
    interceptors: List[Interceptor[F, Raw]],
    jsonSupport: JsonSupport[Raw],
    backendStub: SttpBackendStub[F, R]
) {
  def apply[T](
      req: Request[T, R]
  ): F[Response[_]] = {
    val tapirInterpreter = new TapirInterpreter[F, Raw](jsonSupport, interceptors)
    val tapirEndpoint: Full[Unit, Unit, String, (Raw, StatusCode), (Option[Raw], StatusCode), Any, F] = tapirInterpreter
      .toTapirEndpoint(endpoints)
      .getOrElse(throw new RuntimeException("Error during conversion to tapir"))
    val tapirStubInterpreter =
      TapirStubInterpreter(backendStub).whenServerEndpoint(tapirEndpoint).thenRunLogic().backend()
    tapirStubInterpreter.send(req).map(r => r.asInstanceOf[Response[Any]])
  }
}
