package io.iohk.armadillo.server

import cats.syntax.all._
import io.iohk.armadillo.JsonRpcError.NoData
import io.iohk.armadillo.server.EndpointHandler.{DecodeFailureContext, DecodeSuccessContext}
import io.iohk.armadillo.server.JsonSupport.Json
import io.iohk.armadillo.server.ServerInterpreter._
import io.iohk.armadillo.server.Utils.RichEndpointInput
import io.iohk.armadillo.{JsonRpcErrorOutput, _}
import sttp.monad.MonadError
import sttp.monad.syntax._
import sttp.tapir.DecodeResult
import sttp.tapir.internal.ParamsAsVector

import scala.annotation.tailrec

class ServerInterpreter[F[_], Raw] private (
    jsonRpcEndpoints: List[JsonRpcServerEndpoint[F]],
    jsonSupport: JsonSupport[Raw],
    interceptors: List[Interceptor[F, Raw]]
)(implicit
    monadError: MonadError[F]
) {

  def dispatchRequest(stringRequest: String): F[Option[ServerResponse[Raw]]] = {
    jsonSupport.parse(stringRequest) match {
      case f: DecodeResult.Failure =>
        monadError.suspend(
          callRequestInterceptors(interceptors, Nil, defaultResponder)
            .onDecodeFailure(RequestHandler.DecodeFailureContext(f, stringRequest))
            .map {
              case ResponseHandlingStatus.Handled(response) => response
              case ResponseHandlingStatus.Unhandled         => throw new RuntimeException(s"Unhandled request: $stringRequest")
            }
        )
      case DecodeResult.Value(jsonRequest) =>
        monadError.suspend(
          callRequestInterceptors(interceptors, Nil, defaultResponder)
            .onDecodeSuccess(jsonRequest)
            .map {
              case ResponseHandlingStatus.Handled(response) => response
              case ResponseHandlingStatus.Unhandled         => throw new RuntimeException(s"Unhandled request: $stringRequest")
            }
        )
    }
  }

  private def callRequestInterceptors(
      is: List[Interceptor[F, Raw]],
      eisAcc: List[MethodOrEndpointInterceptor[F, Raw]],
      responder: Responder[F, Raw]
  ): RequestHandler[F, Raw] = {
    is match {
      case Nil => defaultRequestHandler(eisAcc.reverse)
      case (ri: RequestInterceptor[F, Raw]) :: tail =>
        ri.apply(
          responder,
          jsonSupport,
          { ei =>
            RequestHandler(
              onSuccess = callRequestInterceptors(tail, ei :: eisAcc, responder).onDecodeSuccess,
              callRequestInterceptors(tail, ei :: eisAcc, responder).onDecodeFailure
            )
          }
        )
      case (ei: MethodInterceptor[F, Raw]) :: tail   => callRequestInterceptors(tail, ei :: eisAcc, responder)
      case (ei: EndpointInterceptor[F, Raw]) :: tail => callRequestInterceptors(tail, ei :: eisAcc, responder)
      case other =>
        throw new IllegalArgumentException(s"Unsupported interceptor! $other")
    }
  }

  private def defaultRequestHandler(eis: List[MethodOrEndpointInterceptor[F, Raw]]): RequestHandler[F, Raw] = {
    new RequestHandler[F, Raw] {
      override def onDecodeSuccess(request: Json[Raw])(implicit monad: MonadError[F]): F[ResponseHandlingStatus[Raw]] = {
        request match {
          case obj: Json.JsonObject[Raw] => handleObject(jsonRpcEndpoints, obj, eis)
          case _                         => monad.unit(ResponseHandlingStatus.Unhandled)
        }
      }

      override def onDecodeFailure(
          ctx: RequestHandler.DecodeFailureContext
      )(implicit monad: MonadError[F]): F[ResponseHandlingStatus[Raw]] = {
        monad.unit(ResponseHandlingStatus.Unhandled)
      }
    }
  }

  private def defaultMethodHandler(eis: List[EndpointInterceptor[F, Raw]]): MethodHandler[F, Raw] = {
    new MethodHandler[F, Raw] {
      override def onDecodeSuccess[I](
          ctx: MethodHandler.DecodeSuccessContext[F, Raw]
      )(implicit monad: MonadError[F]): F[ResponseHandlingStatus[Raw]] = {
        ctx.endpoints.find(_.endpoint.methodName.asString == ctx.request.method) match {
          case None        => monadError.unit(ResponseHandlingStatus.Unhandled)
          case Some(value) => handleObjectWithEndpoint(value, ctx.request, eis)
        }
      }

      override def onDecodeFailure(
          ctx: MethodHandler.DecodeFailureContext[F, Raw]
      )(implicit monad: MonadError[F]): F[ResponseHandlingStatus[Raw]] = {
        monadError.unit(ResponseHandlingStatus.Unhandled)
      }
    }
  }

  private def handleObject(
      jsonRpcEndpoints: List[JsonRpcServerEndpoint[F]],
      obj: Json.JsonObject[Raw],
      eis: List[MethodOrEndpointInterceptor[F, Raw]]
  ): F[ResponseHandlingStatus[Raw]] = {
    jsonSupport.decodeJsonRpcRequest(obj) match {
      case failure: DecodeResult.Failure =>
        val ctx = MethodHandler.DecodeFailureContext(jsonRpcEndpoints, obj, failure)
        monadError.suspend(callMethodOrEndpointInterceptors(eis, Nil, defaultResponder).onDecodeFailure(ctx))
      case DecodeResult.Value(v) =>
        val ctx = MethodHandler.DecodeSuccessContext(jsonRpcEndpoints, v)
        monadError.suspend(callMethodOrEndpointInterceptors(eis, Nil, defaultResponder).onDecodeSuccess(ctx))
    }
  }

  private def callMethodOrEndpointInterceptors(
      is: List[MethodOrEndpointInterceptor[F, Raw]],
      eisAcc: List[EndpointInterceptor[F, Raw]],
      responder: Responder[F, Raw]
  ): MethodHandler[F, Raw] = {
    is match {
      case Nil => defaultMethodHandler(eisAcc.reverse)
      case (ri: MethodInterceptor[F, Raw]) :: tail =>
        ri.apply(
          responder,
          jsonSupport,
          { ei =>
            MethodHandler(
              onSuccess = callMethodOrEndpointInterceptors(tail, ei :: eisAcc, responder).onDecodeSuccess,
              callMethodOrEndpointInterceptors(tail, ei :: eisAcc, responder).onDecodeFailure
            )
          }
        )
      case (ei: EndpointInterceptor[F, Raw]) :: tail => callMethodOrEndpointInterceptors(tail, ei :: eisAcc, responder)
      case other =>
        throw new IllegalArgumentException(s"Unsupported interceptor! $other")
    }
  }

  private def handleObjectWithEndpoint(
      se: JsonRpcServerEndpoint[F],
      request: JsonRpcRequest[Json[Raw]],
      eis: List[EndpointInterceptor[F, Raw]]
  ): F[ResponseHandlingStatus[Raw]] = {
    val handler = eis.foldRight(defaultEndpointHandler(defaultResponder, jsonSupport)) { case (interceptor, handler) =>
      interceptor.apply(defaultResponder, jsonSupport, handler)
    }
    decodeJsonRpcParamsForEndpoint(se.endpoint, request.params) match {
      case e: DecodeResult.Failure => handler.onDecodeFailure(DecodeFailureContext(se, request, e))
      case DecodeResult.Value(params) =>
        val matchedBody = params.asAny.asInstanceOf[se.INPUT]
        onDecodeSuccess[se.INPUT, se.ERROR_OUTPUT, se.OUTPUT](se, request, handler, matchedBody)
    }
  }

  private def onDecodeSuccess[I, E, O](
      serverEndpoint: JsonRpcServerEndpoint.Full[I, E, O, F],
      request: JsonRpcRequest[Json[Raw]],
      handler: EndpointHandler[F, Raw],
      matchedBody: I
  ): F[ResponseHandlingStatus[Raw]] = {
    handler.onDecodeSuccess[serverEndpoint.INPUT](
      DecodeSuccessContext[F, serverEndpoint.INPUT, Raw](serverEndpoint, request, matchedBody)
    )
  }

  private val defaultResponder: Responder[F, Raw] = new Responder[F, Raw] {
    override def apply(response: Option[JsonRpcResponse[Raw]]): F[Option[ServerResponse[Raw]]] = {
      response match {
        case Some(value) =>
          value match {
            case successResponse @ JsonRpcSuccessResponse(_, _, _) =>
              monadError.unit(ServerResponse.Success(jsonSupport.encodeResponse(successResponse)).some)
            case errorResponse @ JsonRpcErrorResponse(_, _, _) =>
              monadError.unit(ServerResponse.Failure(jsonSupport.encodeResponse(errorResponse)).some)
          }
        case None => monadError.unit(None)
      }
    }
  }

  private def decodeJsonRpcParamsForEndpoint(
      jsonRpcEndpoint: JsonRpcEndpoint[_, _, _],
      maybeJsonParams: Option[Json[Raw]]
  ): DecodeResult[ParamsAsVector] = {
    maybeJsonParams match {
      case Some(jsonParams) =>
        val result = jsonParams match {
          case obj: Json.JsonObject[Raw] =>
            val objectCombinator = combineDecodeAsObject(jsonRpcEndpoint.input.asVectorOfBasicInputs)
            jsonRpcEndpoint.paramStructure match {
              case ParamStructure.Either     => objectCombinator(obj)
              case ParamStructure.ByName     => objectCombinator(obj)
              case ParamStructure.ByPosition => DecodeResult.Mismatch("json object", jsonSupport.stringify(jsonSupport.demateralize(obj)))
            }
          case arr: Json.JsonArray[Raw] =>
            val vectorCombinator = combineDecodeAsVector(jsonRpcEndpoint.input.asVectorOfBasicInputs)
            jsonRpcEndpoint.paramStructure match {
              case ParamStructure.Either     => vectorCombinator(arr)
              case ParamStructure.ByPosition => vectorCombinator(arr)
              case ParamStructure.ByName     => DecodeResult.Mismatch("json object", jsonSupport.stringify(jsonSupport.demateralize(arr)))
            }
          case Json.Other(raw) => DecodeResult.Mismatch("json array or json object", jsonSupport.stringify(raw))
        }
        result.map(ParamsAsVector)
      case None => DecodeResult.Value(ParamsAsVector(Vector.empty))
    }
  }

  private def combineDecodeAsVector(in: Vector[JsonRpcIO.Single[_]]): Json.JsonArray[Raw] => DecodeResult[Vector[_]] = { json =>
    case class State(results: List[DecodeResult[_]], paramsToProcess: List[Raw])
    val ss = in.foldLeft(State(List.empty, json.values.toList)) { (acc, input) =>
      acc.paramsToProcess match {
        case currentParam :: restOfParams =>
          val codec = input.codec.asInstanceOf[JsonRpcCodec[Any]]
          val decoded = codec.decode(currentParam.asInstanceOf[codec.L])
          val validated = decoded.flatMap(Validation.from(codec.schema.validator))
          validated match {
            case _: DecodeResult.Failure if codec.schema.isOptional =>
              acc.copy(results = acc.results :+ DecodeResult.Value(None))
            case other => State(acc.results :+ other, restOfParams)
          }
        case Nil => acc.copy(results = acc.results :+ DecodeResult.Missing)
      }
    }
    if (ss.paramsToProcess.isEmpty) {
      DecodeResult.sequence(ss.results).map(_.toVector)
    } else {
      val msg = "Too many inputs provided"
      DecodeResult.Error(msg, new RuntimeException(msg))
    }
  }

  private def combineDecodeAsObject(in: Vector[JsonRpcIO.Single[_]]): Json.JsonObject[Raw] => DecodeResult[Vector[_]] = { json =>
    val jsonAsMap = json.fields.toMap
    if (jsonAsMap.size >= in.count(_.codec.schema.isOptional) && jsonAsMap.size <= in.size) {
      val ss = in.toList.map { case JsonRpcIO.Single(codec, _, name) =>
        jsonAsMap.get(name) match {
          case Some(r) =>
            val decoded = codec.decode(r.asInstanceOf[codec.L])
            decoded.flatMap(Validation.from(codec.schema.validator))
          case None =>
            if (codec.schema.isOptional) {
              DecodeResult.Value(None)
            } else {
              DecodeResult.Missing
            }
        }
      }
      DecodeResult.sequence(ss).map(_.toVector)
    } else {
      val msg = "Too many inputs provided"
      DecodeResult.Error(msg, new RuntimeException(msg))
    }
  }
}

object ServerInterpreter {
  val ParseError: JsonRpcError.NoData = JsonRpcError.noData(-32700, "Parse error")
  val InvalidRequest: JsonRpcError.NoData = JsonRpcError.noData(-32600, "Invalid Request")
  val MethodNotFound: JsonRpcError.NoData = JsonRpcError.noData(-32601, "Method not found")
  val InvalidParams: JsonRpcError.NoData = JsonRpcError.noData(-32602, "Invalid params")
  val InternalError: JsonRpcError.NoData = JsonRpcError.noData(-32603, "Internal error")

  def apply[F[_]: MonadError, Raw](
      jsonRpcEndpoints: List[JsonRpcServerEndpoint[F]],
      jsonSupport: JsonSupport[Raw],
      interceptors: List[Interceptor[F, Raw]]
  ): Either[InterpretationError, ServerInterpreter[F, Raw]] = {
    val nonUniqueMethodNames = jsonRpcEndpoints.groupBy(_.endpoint.methodName).values.filter(_.size != 1).map(_.head.endpoint.methodName)
    Either.cond(
      nonUniqueMethodNames.isEmpty,
      new ServerInterpreter(jsonRpcEndpoints, jsonSupport, interceptors),
      InterpretationError.NonUniqueMethod(nonUniqueMethodNames.toList)
    )
  }

  sealed trait ServerResponse[+Raw] {
    def body: Raw
  }

  object ServerResponse {
    final case class Success[+Raw](body: Raw) extends ServerResponse[Raw]
    final case class Failure[+Raw](body: Raw) extends ServerResponse[Raw]
    final case class ServerFailure[+Raw](body: Raw) extends ServerResponse[Raw]
  }

  sealed trait ResponseHandlingStatus[+Raw]
  object ResponseHandlingStatus {
    final case class Handled[+Raw](response: Option[ServerResponse[Raw]]) extends ResponseHandlingStatus[Raw]
    case object Unhandled extends ResponseHandlingStatus[Nothing]
  }

  sealed trait InterpretationError
  object InterpretationError {
    case class NonUniqueMethod(names: List[MethodName]) extends InterpretationError
  }

  private def defaultEndpointHandler[F[_], Raw](responder: Responder[F, Raw], jsonSupport: JsonSupport[Raw]): EndpointHandler[F, Raw] = {
    new EndpointHandler[F, Raw] {
      override def onDecodeSuccess[I](
          ctx: DecodeSuccessContext[F, I, Raw]
      )(implicit monad: MonadError[F]): F[ResponseHandlingStatus[Raw]] = {
        ctx.endpoint
          .logic(monad)(ctx.input)
          .map {
            case Left(value) =>
              val encodedError = handleErrorReturnType(jsonSupport, ctx)(value, ctx.endpoint.endpoint.error)
              ctx.request.id.map(id => JsonRpcErrorResponse("2.0", encodedError, Some(id)))
            case Right(value) =>
              val encodedOutput = ctx.endpoint.endpoint.output match {
                case _: JsonRpcIO.Empty[ctx.endpoint.OUTPUT]  => jsonSupport.jsNull
                case o: JsonRpcIO.Single[ctx.endpoint.OUTPUT] => o.codec.encode(value).asInstanceOf[Raw]
              }
              ctx.request.id.map(JsonRpcSuccessResponse("2.0", encodedOutput, _))
          }
          .flatMap(responder.apply)
          .map(ResponseHandlingStatus.Handled.apply)
      }

      override def onDecodeFailure(ctx: DecodeFailureContext[F, Raw])(implicit monad: MonadError[F]): F[ResponseHandlingStatus[Raw]] = {
        val result: ResponseHandlingStatus[Raw] = if (ctx.request.isNotification) {
          ResponseHandlingStatus.Handled(none)
        } else {
          ResponseHandlingStatus.Handled(ServerResponse.Failure(createErrorResponse(InvalidParams, ctx.request.id)).some)
        }
        monad.unit(result)
      }

      private def createErrorResponse(error: JsonRpcError.NoData, id: Option[JsonRpcId]): Raw = {
        jsonSupport.encodeResponse(JsonRpcResponse.error_v2(jsonSupport.encodeErrorNoData(error), id))
      }
    }
  }

  @tailrec
  private def handleErrorReturnType[Raw, F[_], I](jsonSupport: JsonSupport[Raw], ctx: DecodeSuccessContext[F, I, Raw])(
      value: ctx.endpoint.ERROR_OUTPUT,
      errorOutput: JsonRpcErrorOutput[ctx.endpoint.ERROR_OUTPUT]
  ): Raw = {
    errorOutput match {
      case _: JsonRpcErrorOutput.SingleNoData =>
        jsonSupport.encodeErrorNoData(value.asInstanceOf[NoData])
      case single: JsonRpcErrorOutput.SingleWithData[ctx.endpoint.ERROR_OUTPUT] =>
        single.codec.encode(value.asInstanceOf[single.DATA]).asInstanceOf[Raw]
      case JsonRpcErrorOutput.Fixed(code, message) =>
        jsonSupport.encodeErrorNoData(JsonRpcError.noData(code, message))
      case _: JsonRpcErrorOutput.Empty =>
        jsonSupport.jsNull
      case JsonRpcErrorOutput.FixedWithData(code, message, codec) =>
        val encodedData = codec.encode(value).asInstanceOf[Raw]
        jsonSupport.encodeErrorWithData(JsonRpcError.withData(code, message, encodedData))
      case JsonRpcErrorOutput.OneOf(variants, _) =>
        variants.find(v => v.appliesTo(value)) match {
          case Some(matchedVariant) =>
            handleErrorReturnType(jsonSupport, ctx)(
              value,
              matchedVariant.output.asInstanceOf[JsonRpcErrorOutput[ctx.endpoint.ERROR_OUTPUT]]
            )
          case None => throw new IllegalArgumentException(s"OneOf variant not matched. Variants: $variants, value: $value")
        }
    }
  }
}
