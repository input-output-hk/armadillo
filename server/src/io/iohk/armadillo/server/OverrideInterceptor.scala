package io.iohk.armadillo.server

import io.iohk.armadillo.server.EndpointOverride.Full
import io.iohk.armadillo.{JsonRpcEndpoint, JsonRpcServerEndpoint}
import sttp.monad.MonadError

class OverrideInterceptor[F[_], Raw](overriddenEndpoints: List[EndpointOverride[F]]) extends EndpointInterceptor[F, Raw] {
  override def apply(
      responder: Responder[F, Raw],
      jsonSupport: JsonSupport[Raw],
      endpointHandler: EndpointHandler[F, Raw]
  ): EndpointHandler[F, Raw] = {
    new EndpointHandler[F, Raw] {
      override def onDecodeSuccess[I, E, O](ctx: EndpointHandler.DecodeSuccessContext[F, I, E, O, Raw])(implicit
          monad: MonadError[F]
      ): F[ServerInterpreter.ResponseHandlingStatus[Raw]] = {
        overriddenEndpoints.find(_.endpoint.methodName == ctx.endpoint.endpoint.methodName) match {
          case Some(oe) =>
            monad.flatMap(oe.asInstanceOf[Full[I, E, O, F]].logic(monad, ctx.input, ctx.endpoint))(s =>
              responder.apply(s, ctx.endpoint.endpoint, ctx.request.id)
            )

          case None => endpointHandler.onDecodeSuccess(ctx)
        }
      }

      override def onDecodeFailure(ctx: EndpointHandler.DecodeFailureContext[F, Raw])(implicit
          monad: MonadError[F]
      ): F[ServerInterpreter.ResponseHandlingStatus[Raw]] = endpointHandler.onDecodeFailure(ctx)
    }
  }
}

case class OverridingEndpoint[I, E, O](e: JsonRpcEndpoint[I, E, O]) {
  def replaceLogic[F[_]](logic: MonadError[F] => I => F[Either[E, O]]): EndpointOverride[F] = {
    EndpointOverride[I, E, O, F](
      e,
      { case (m, i, _) =>
        logic(m)(i)
      }
    )
  }

  def thenReturn[F[_]](o: F[Either[E, O]]): EndpointOverride[F] = {
    EndpointOverride[I, E, O, F](
      e,
      { case (_, _, _) =>
        o
      }
    )
  }

  def runBeforeLogic[F[_]](f: => F[Unit]): EndpointOverride[F] = {
    EndpointOverride[I, E, O, F](
      e,
      { case (m, i, se) =>
        m.flatMap(m.suspend(f))(_ => se.logic(m)(i))
      }
    )
  }

  def runAfterLogic[F[_]](f: => F[Unit]): EndpointOverride[F] = {
    EndpointOverride[I, E, O, F](
      e,
      { case (m, i, se) =>
        m.flatMap(se.logic(m)(i))(output => m.map(m.suspend(f))(_ => output))
      }
    )
  }
}

abstract class EndpointOverride[F[_]]() {
  type INPUT
  type OUTPUT
  type ERROR_OUTPUT

  def endpoint: JsonRpcEndpoint[INPUT, ERROR_OUTPUT, OUTPUT]
  def logic: (MonadError[F], INPUT, JsonRpcServerEndpoint.Full[INPUT, ERROR_OUTPUT, OUTPUT, F]) => F[Either[ERROR_OUTPUT, OUTPUT]]
}

object EndpointOverride {
  type Full[_INPUT, _ERROR_OUTPUT, _OUTPUT, F[_]] = EndpointOverride[F] {
    type INPUT = _INPUT
    type ERROR_OUTPUT = _ERROR_OUTPUT
    type OUTPUT = _OUTPUT
  }

  def apply[INPUT, ERROR_OUTPUT, OUTPUT, F[_]](
      endpoint: JsonRpcEndpoint[INPUT, ERROR_OUTPUT, OUTPUT],
      logic: (MonadError[F], INPUT, JsonRpcServerEndpoint.Full[INPUT, ERROR_OUTPUT, OUTPUT, F]) => F[Either[ERROR_OUTPUT, OUTPUT]]
  ): EndpointOverride.Full[INPUT, ERROR_OUTPUT, OUTPUT, F] = {
    type _INPUT = INPUT
    type _ERROR_OUTPUT = ERROR_OUTPUT
    type _OUTPUT = OUTPUT
    val f = logic
    val _endpoint = endpoint
    new EndpointOverride[F] {
      override type INPUT = _INPUT
      override type ERROR_OUTPUT = _ERROR_OUTPUT
      override type OUTPUT = _OUTPUT

      override def endpoint: JsonRpcEndpoint[_INPUT, _ERROR_OUTPUT, _OUTPUT] = _endpoint

      override def logic
          : (MonadError[F], INPUT, JsonRpcServerEndpoint.Full[INPUT, ERROR_OUTPUT, OUTPUT, F]) => F[Either[ERROR_OUTPUT, OUTPUT]] = f
    }
  }
}
