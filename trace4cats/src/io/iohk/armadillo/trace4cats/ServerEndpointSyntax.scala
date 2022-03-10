package io.iohk.armadillo.trace4cats

import cats.Monad
import cats.effect.kernel.MonadCancelThrow
import cats.syntax.either.*
import io.iohk.armadillo.JsonRpcServerEndpoint
import io.janstenpickle.trace4cats.Span
import io.janstenpickle.trace4cats.base.context.Provide
import io.janstenpickle.trace4cats.inject.{EntryPoint, ResourceKleisli, Trace}

import scala.reflect.ClassTag

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

  implicit class TracedServerEndpointRecoverErrors[I, E <: Throwable, O, F[_], G[_]](
      serverEndpoint: JsonRpcServerEndpoint.Full[I, E, O, G]
  ) {
    def injectContextRecoverErrors[Ctx](
        entryPoint: EntryPoint[F],
        makeContext: (I, Span[F]) => F[Ctx],
        spanNamer: ArmadilloSpanNamer[I] = ArmadilloSpanNamer.methodName,
        errorToSpanStatus: ArmadilloStatusMapping[E] = ArmadilloStatusMapping.errorMessageToInternal
    )(implicit
        P: Provide[F, G, Ctx],
        F: MonadCancelThrow[F],
        G: Monad[G],
        T: Trace[G],
        eClassTag: ClassTag[E]
    ): JsonRpcServerEndpoint.Full[I, E, O, F] = {
      val inputSpanNamer = spanNamer(serverEndpoint.endpoint, _)
      val context = ArmadilloResourceKleislis.fromInputContextRecoverErrors[F, I, E, Ctx](
        makeContext,
        inputSpanNamer,
        errorToSpanStatus
      )(entryPoint.toKleisli)

      ServerEndpointTracer.injectRecoverErrors(
        serverEndpoint,
        context,
        errorToSpanStatus
      )
    }

    def tracedContextRecoverErrors[Ctx](
        k: ResourceKleisli[F, I, Ctx],
        errorToSpanStatus: ArmadilloStatusMapping[E] = ArmadilloStatusMapping.errorStringToInternal
    )(implicit
        P: Provide[F, G, Ctx],
        F: MonadCancelThrow[F],
        G: Monad[G],
        T: Trace[G],
        eClassTag: ClassTag[E]
    ): JsonRpcServerEndpoint.Full[I, E, O, F] =
      ServerEndpointTracer.injectRecoverErrors(
        serverEndpoint,
        k,
        errorToSpanStatus
      )
  }
}