package io.iohk.armadillo.tapir

import io.iohk.armadillo.*
import io.iohk.armadillo.Armadillo.{JsonRpcError, JsonRpcErrorResponse, JsonRpcRequest, JsonRpcResponse}
import io.iohk.armadillo.tapir.TapirInterpreter.RichDecodeResult
import io.iohk.armadillo.tapir.Utils.RichEndpointInput
import sttp.monad.MonadError
import sttp.monad.syntax.*
import sttp.tapir.Codec.JsonCodec
import sttp.tapir.EndpointIO.Info
import sttp.tapir.internal.ParamsAsVector
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.{DecodeResult, EndpointIO, RawBodyType}

import java.nio.charset.StandardCharsets

trait Provider[W[_]] {
  def codec[T](bodyCodec: JsonCodec[T]): JsonCodec[W[T]]
}

class TapirInterpreter[F[_], Json](jsonSupport: JsonSupport[Json])(implicit
    monadError: MonadError[F]
) {

  def apply(jsonRpcEndpoints: List[JsonRpcServerEndpoint[F]]): ServerEndpoint[Any, F] = {
    sttp.tapir.endpoint.post
      .in(
        EndpointIO.Body(
          RawBodyType.StringBody(StandardCharsets.UTF_8),
          jsonSupport.inCodec.mapDecode { envelop =>
            decodeJsonRpcParams(jsonRpcEndpoints.map(_.endpoint), envelop)
          } { _ => throw new RuntimeException("should not be called") },
          Info.empty
        )
      )
      .out(EndpointIO.Body(RawBodyType.StringBody(StandardCharsets.UTF_8), jsonSupport.outCodec, Info.empty))
      .errorOut(EndpointIO.Body(RawBodyType.StringBody(StandardCharsets.UTF_8), jsonSupport.errorOutCodec, Info.empty))
      .serverLogic[F](serverLogic(jsonRpcEndpoints, _))
  }

  private def serverLogic(jsonRpcEndpoints: List[JsonRpcServerEndpoint[F]], envelop: JsonRpcRequest[ParamsAsVector]) = {
    val matchedEndpoint = jsonRpcEndpoints.find(_.endpoint.methodName.value == envelop.method) match {
      case Some(value) => value
      case None        => throw new IllegalStateException("Cannot happen because filtering codec passes through only matching methods")
    }
    val matchedBody = envelop.params.asAny.asInstanceOf[matchedEndpoint.INPUT]
    matchedEndpoint.logic(monadError)(matchedBody).map {
      case Left(value) =>
        val encodedError = matchedEndpoint.endpoint.error match {
          case JsonRpcErrorOutput.EmptyError(codec, _) => jsonSupport.empty.asInstanceOf[codec.L]
          case JsonRpcErrorOutput.Single(codec, _, _)  => codec.encode(value)
        }
        Left(JsonRpcErrorResponse("2.0", encodedError.asInstanceOf[Json], 1))
      case Right(value) =>
        val encodedOutput = matchedEndpoint.endpoint.output match {
          case o: JsonRpcIO.Empty[matchedEndpoint.OUTPUT]  => jsonSupport.empty.asInstanceOf[o.codec.L]
          case o: JsonRpcIO.Single[matchedEndpoint.OUTPUT] => o.codec.encode(value)
        }
        Right(JsonRpcResponse("2.0", encodedOutput.asInstanceOf[Json], 1))
    }
  }

  private def decodeJsonRpcParams(endpoints: List[JsonRpcEndpoint[_, _, _]], envelop: JsonRpcRequest[Json]) = {
    endpoints.find(_.methodName.value == envelop.method) match {
      case Some(jsonRpcEndpoint) =>
        val vectorCombinator = combineDecodeAsVector(jsonRpcEndpoint.input.asVectorOfBasicInputs)
        val objectCombinator = combineDecodeAsObject(jsonRpcEndpoint.input.asVectorOfBasicInputs)
        val combinedDecodeResult = vectorCombinator
          .apply(envelop.params)
          .orElse(objectCombinator.apply(envelop.params))
        combinedDecodeResult.map { paramsVector =>
          val params = ParamsAsVector(paramsVector)
          JsonRpcRequest(
            jsonrpc = envelop.jsonrpc,
            method = envelop.method,
            params = params,
            id = envelop.id
          )
        }
      case None =>
        DecodeResult.Mismatch(endpoints.map(_.methodName).mkString(","), envelop.method)
    }
  }

  private def combineDecodeAsVector(in: Vector[JsonRpcIO.Single[_]]): Json => DecodeResult[Vector[_]] = { json =>
    val ss = in.zipWithIndex.toList.map { case (JsonRpcIO.Single(codec, _, _), index) =>
      val rawElement = jsonSupport.getByIndex(json, index)
      rawElement.flatMap(r => codec.decode(r.asInstanceOf[codec.L]))
    }
    DecodeResult.sequence(ss).map(_.toVector)
  }

  private def combineDecodeAsObject(in: Vector[JsonRpcIO.Single[_]]): Json => DecodeResult[Vector[_]] = { json =>
    val ss = in.toList.map { case JsonRpcIO.Single(codec, _, name) =>
      val rawElement = jsonSupport.getByField(json, name)
      rawElement.flatMap(r => codec.decode(r.asInstanceOf[codec.L]))
    }
    DecodeResult.sequence(ss).map(_.toVector)
  }
}

object TapirInterpreter {
  implicit class RichDecodeResult[T](decodeResult: DecodeResult[T]) {
    def orElse(other: => DecodeResult[T]): DecodeResult[T] = {
      decodeResult match {
        case firstFailure: DecodeResult.Failure =>
          other match {
            case secondFailure: DecodeResult.Failure => DecodeResult.Multiple(Seq(firstFailure, secondFailure))
            case success: DecodeResult.Value[T]      => success
          }
        case success: DecodeResult.Value[T] => success
      }
    }
  }
}
