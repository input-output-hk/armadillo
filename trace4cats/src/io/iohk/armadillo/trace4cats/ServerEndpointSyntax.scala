package io.iohk.armadillo.trace4cats

import cats.Monad
import cats.effect.kernel.MonadCancelThrow
import cats.syntax.either._
import io.iohk.armadillo.JsonRpcServerEndpoint
import trace4cats.context.Provide
import trace4cats.{EntryPoint, ResourceKleisli, Span, Trace}

trait ServerEndpointSyntax {
  implicit class TracedServerEndpoint[I, E, O, F[_], G[_]](
      serverEndpoint: JsonRpcServerEndpoint.Full[I, E, O, G]
  ) {
    def inject(
        entryPoint: EntryPoint[F],
        spanNamer: ArmadilloSpanNamer[I] = ArmadilloSpanNamer.methodName[I],
        errorToSpanStatus: ArmadilloStatusMapping[E] = ArmadilloStatusMapping.errorStringToInternal
    )(implicit
        P: Provide[F, G, Span[F]],
        F: MonadCancelThrow[F],
        G: Monad[G],
        T: Trace[G]
    ): JsonRpcServerEndpoint.Full[I, E, O, F] = {
      val inputSpanNamer = spanNamer(serverEndpoint.endpoint, _)
      val context = ArmadilloResourceKleislis
        .fromInput[F, I](inputSpanNamer)(entryPoint.toKleisli)
        .map(_.asRight[E])
      ServerEndpointTracer.inject(
        serverEndpoint,
        context,
        errorToSpanStatus
      )
    }

    def traced(
        k: ResourceKleisli[F, I, Span[F]],
        errorToSpanStatus: ArmadilloStatusMapping[E] = ArmadilloStatusMapping.errorStringToInternal
    )(implicit
        P: Provide[F, G, Span[F]],
        F: MonadCancelThrow[F],
        G: Monad[G],
        T: Trace[G]
    ): JsonRpcServerEndpoint.Full[I, E, O, F] =
      ServerEndpointTracer.inject(
        serverEndpoint,
        k.map(_.asRight[E]),
        errorToSpanStatus
      )

    def injectContext[Ctx](
        entryPoint: EntryPoint[F],
        makeContext: (I, Span[F]) => F[Either[E, Ctx]],
        spanNamer: ArmadilloSpanNamer[I] = ArmadilloSpanNamer.methodName,
        errorToSpanStatus: ArmadilloStatusMapping[E] = ArmadilloStatusMapping.errorStringToInternal
    )(implicit
        P: Provide[F, G, Ctx],
        F: MonadCancelThrow[F],
        G: Monad[G],
        T: Trace[G]
    ): JsonRpcServerEndpoint.Full[I, E, O, F] = {
      val inputSpanNamer = spanNamer(serverEndpoint.endpoint, _)
      val context = ArmadilloResourceKleislis.fromInputContext[F, I, E, Ctx](
        makeContext,
        inputSpanNamer,
        errorToSpanStatus
      )(entryPoint.toKleisli)
      ServerEndpointTracer.inject(
        serverEndpoint,
        context,
        errorToSpanStatus
      )
    }

    def tracedContext[Ctx](
        k: ResourceKleisli[F, I, Either[E, Ctx]],
        errorToSpanStatus: ArmadilloStatusMapping[E] = ArmadilloStatusMapping.errorStringToInternal
    )(implicit
        P: Provide[F, G, Ctx],
        F: MonadCancelThrow[F],
        G: Monad[G],
        T: Trace[G]
    ): JsonRpcServerEndpoint.Full[I, E, O, F] =
      ServerEndpointTracer.inject(
        serverEndpoint,
        k,
        errorToSpanStatus
      )
  }
}
