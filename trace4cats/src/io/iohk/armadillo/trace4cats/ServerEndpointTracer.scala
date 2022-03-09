package io.iohk.armadillo.trace4cats

import cats.Monad
import cats.data.EitherT
import cats.effect.kernel.MonadCancelThrow
import io.iohk.armadillo.JsonRpcServerEndpoint
import io.janstenpickle.trace4cats.base.context.Provide
import io.janstenpickle.trace4cats.inject.{ResourceKleisli, Trace}
import sttp.tapir.integ.cats.MonadErrorSyntax.*
import cats.syntax.all._

import scala.reflect.ClassTag

object ServerEndpointTracer {
  def inject[I, E, O, F[_], G[_], Ctx](
      serverEndpoint: JsonRpcServerEndpoint.Full[I, E, O, G],
      k: ResourceKleisli[F, I, Either[E, Ctx]],
      errorToSpanStatus: ArmadilloStatusMapping[E]
  )(implicit
      P: Provide[F, G, Ctx],
      F: MonadCancelThrow[F],
      G: Monad[G],
      T: Trace[G]
  ): JsonRpcServerEndpoint.Full[I, E, O, F] =
    JsonRpcServerEndpoint.apply(
      endpoint = serverEndpoint.endpoint,
      logic = MEF =>
        input => {
          k(input).use {
            EitherT
              .fromEither[F](_)
              .flatMap { ctx =>
                val lower = P.provideK(ctx)
                val MEG = MonadErrorImapK(MEF).imapK(P.liftK)(lower)
                EitherT {
                  serverEndpoint.logic(MEG)(input)
                }.leftSemiflatTap { err =>
                  Trace[G].setStatus(errorToSpanStatus(err))
                }.mapK(lower)
              }
              .value
          }
        }
    )

  def injectRecoverErrors[I, E <: Throwable, O, R, F[_], G[_], Ctx](
      serverEndpoint: JsonRpcServerEndpoint.Full[I, E, O, G],
      k: ResourceKleisli[F, I, Ctx],
      errorToSpanStatus: ArmadilloStatusMapping[E]
  )(implicit
      P: Provide[F, G, Ctx],
      F: MonadCancelThrow[F],
      G: Monad[G],
      T: Trace[G],
      eClassTag: ClassTag[E]
  ): JsonRpcServerEndpoint.Full[I, E, O, F] =
    JsonRpcServerEndpoint.apply(
      endpoint = serverEndpoint.endpoint,
      logic = MEF =>
        input =>
          inject(
            serverEndpoint,
            k.attemptNarrow[E],
            errorToSpanStatus
          ).logic(MEF)(input)
    )
}
