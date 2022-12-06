package io.iohk.armadillo.trace4cats

import cats.Monad
import cats.data.{EitherT, Kleisli}
import cats.effect.kernel.Resource
import trace4cats.kernel.ErrorHandler
import trace4cats.model.{SpanKind, TraceHeaders}
import trace4cats.{ResourceKleisli, Span, SpanParams}

object ArmadilloResourceKleislis {
  def fromInput[F[_], I](
      inSpanNamer: ArmadilloInputSpanNamer[I]
  )(k: ResourceKleisli[F, SpanParams, Span[F]]): ResourceKleisli[F, I, Span[F]] =
    Kleisli { input =>
      k.run((inSpanNamer(input), SpanKind.Server, TraceHeaders.empty, ErrorHandler.empty))
    }

  def fromInputContext[F[_]: Monad, I, E, Ctx](
      makeContext: (I, Span[F]) => F[Either[E, Ctx]],
      inSpanNamer: ArmadilloInputSpanNamer[I],
      errorToSpanStatus: ArmadilloStatusMapping[E]
  )(k: ResourceKleisli[F, SpanParams, Span[F]]): ResourceKleisli[F, I, Either[E, Ctx]] =
    fromInput(inSpanNamer)(k).tapWithF { (req, span) =>
      val fa = EitherT(makeContext(req, span))
        .leftSemiflatTap(e => span.setStatus(errorToSpanStatus(e)))
        .value
      Resource.eval(fa)
    }
}
