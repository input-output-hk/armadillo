package io.iohk.armadillo.trace4cats

import cats.Monad
import cats.data.{EitherT, Kleisli}
import cats.effect.kernel.Resource
import io.iohk.armadillo.JsonRpcError
import io.janstenpickle.trace4cats.inject.{ResourceKleisli, SpanParams}
import io.janstenpickle.trace4cats.model.{SpanKind, TraceHeaders}
import io.janstenpickle.trace4cats.{ErrorHandler, Span}

object ArmadilloResourceKleislis {
  def fromInput[F[_], I](
      inSpanNamer: ArmadilloInputSpanNamer[I]
  )(k: ResourceKleisli[F, SpanParams, Span[F]]): ResourceKleisli[F, I, Span[F]] =
    Kleisli { input =>
      k.run((inSpanNamer(input), SpanKind.Server, TraceHeaders.empty, ErrorHandler.empty))
    }

  def fromInputContext[F[_]: Monad, I, E, Ctx](
      makeContext: (I, Span[F]) => F[Either[JsonRpcError[E], Ctx]],
      inSpanNamer: ArmadilloInputSpanNamer[I],
      errorToSpanStatus: ArmadilloStatusMapping[E]
  )(k: ResourceKleisli[F, SpanParams, Span[F]]): ResourceKleisli[F, I, Either[JsonRpcError[E], Ctx]] =
    fromInput(inSpanNamer)(k).tapWithF { (req, span) =>
      val fa = EitherT(makeContext(req, span))
        .leftSemiflatTap(e => span.setStatus(errorToSpanStatus(e)))
        .value
      Resource.eval(fa)
    }
}
