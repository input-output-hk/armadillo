package io.iohk.armadillo.server

import io.iohk.armadillo._
import io.iohk.armadillo.server.EndpointHandler.{DecodeFailureContext, DecodeSuccessContext}
import io.iohk.armadillo.server.JsonSupport.Json
import io.iohk.armadillo.server.ServerInterpreter._
import io.iohk.armadillo.server.Utils.RichEndpointInput
import sttp.monad.MonadError
import sttp.monad.syntax._
import sttp.tapir.DecodeResult
import sttp.tapir.internal.ParamsAsVector

class ServerInterpreter[F[_], Raw] private (
    jsonRpcEndpoints: List[JsonRpcServerEndpoint[F]],
    jsonSupport: JsonSupport[Raw],
    interceptors: List[Interceptor[F, Raw]]
)(implicit
    monadError: MonadError[F]
) {

  def dispatchRequest(stringRequest: String): F[ServerInterpreterResponse[Raw]] = {
    jsonSupport.parse(stringRequest) match {
      case f: DecodeResult.Failure =>
        monadError.suspend(
          callRequestInterceptors(interceptors, Nil, defaultResponder)
            .onDecodeFailure(RequestHandler.DecodeFailureContext(f, stringRequest))
            .map {
              case DecodeAction.ActionTaken(response) => response
              case DecodeAction.None()                => ServerInterpreterResponse.None() // TODO: What is the expected behavior
            }
        )
      case DecodeResult.Value(jsonRequest) =>
        monadError.suspend(
          callRequestInterceptors(interceptors, Nil, defaultResponder)
            .onDecodeSuccess(jsonRequest)
            .map {
              case DecodeAction.ActionTaken(response) => response
              case DecodeAction.None()                => ServerInterpreterResponse.None() // TODO: What is the expected behavior
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
      override def onDecodeSuccess(request: Json[Raw])(implicit monad: MonadError[F]): F[DecodeAction[Raw]] = {
        request match {
          case obj: Json.JsonObject[Raw] => handleObject(jsonRpcEndpoints, obj, eis)
          case _                         => monad.unit(DecodeAction.None())
        }
      }

      override def onDecodeFailure(
          ctx: RequestHandler.DecodeFailureContext
      )(implicit monad: MonadError[F]): F[DecodeAction[Raw]] = {
        monad.unit(DecodeAction.None())
      }
    }
  }

  private def defaultMethodHandler(eis: List[EndpointInterceptor[F, Raw]]): MethodHandler[F, Raw] = {
    new MethodHandler[F, Raw] {
      override def onDecodeSuccess[I](
          ctx: MethodHandler.DecodeSuccessContext[F, Raw]
      )(implicit monad: MonadError[F]): F[DecodeAction[Raw]] = {
        ctx.endpoints.find(_.endpoint.methodName.asString == ctx.request.method) match {
          case None        => monadError.unit(DecodeAction.None())
          case Some(value) => handleObjectWithEndpoint(value, ctx.request, eis)
        }
      }

      override def onDecodeFailure(ctx: MethodHandler.DecodeFailureContext[F, Raw])(implicit monad: MonadError[F]): F[DecodeAction[Raw]] = {
        monadError.unit(DecodeAction.None())
      }
    }
  }

  private def handleObject(
      jsonRpcEndpoints: List[JsonRpcServerEndpoint[F]],
      obj: Json.JsonObject[Raw],
      eis: List[MethodOrEndpointInterceptor[F, Raw]]
  ): F[DecodeAction[Raw]] = {
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
  ): F[DecodeAction[Raw]] = {
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
  ): F[DecodeAction[Raw]] = {
    handler.onDecodeSuccess[serverEndpoint.INPUT](
      DecodeSuccessContext[F, serverEndpoint.INPUT, Raw](serverEndpoint, request, matchedBody)
    )
  }

  private val defaultResponder: Responder[F, Raw] = new Responder[F, Raw] {
    override def apply(response: Option[JsonRpcResponse[Raw]]): F[DecodeAction[Raw]] = {
      response match {
        case Some(value) =>
          value match {
            case successResponse @ JsonRpcSuccessResponse(_, _, _) =>
              monadError.unit(DecodeAction.ActionTaken(ServerInterpreterResponse.Result(jsonSupport.encodeResponse(successResponse))))
            case errorResponse @ JsonRpcErrorResponse(_, _, _) =>
              monadError.unit(DecodeAction.ActionTaken(ServerInterpreterResponse.Error(jsonSupport.encodeResponse(errorResponse))))
          }
        case None => monadError.unit(DecodeAction.None())
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
          val codec = input.codec
          codec.decode(currentParam.asInstanceOf[codec.L]) match {
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
          case Some(r) => codec.decode(r.asInstanceOf[codec.L])
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
  val ParseError: JsonRpcError[Unit] = JsonRpcError[Unit](-32700, "Parse error", ())
  val InvalidRequest: JsonRpcError[Unit] = JsonRpcError[Unit](-32600, "Invalid Request", ())
  val MethodNotFound: JsonRpcError[Unit] = JsonRpcError[Unit](-32601, "Method not found", ())
  val InvalidParams: JsonRpcError[Unit] = JsonRpcError[Unit](-32602, "Invalid params", ())
  val InternalError: JsonRpcError[Unit] = JsonRpcError[Unit](-32603, "Internal error", ())

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

  sealed trait ServerInterpreterResponse[Raw]

  object ServerInterpreterResponse {
    final case class Result[Raw](value: Raw) extends ServerInterpreterResponse[Raw]
    final case class Error[Raw](value: Raw) extends ServerInterpreterResponse[Raw]
    final case class None[Raw]() extends ServerInterpreterResponse[Raw] // In case of notification requests
  }

  sealed trait DecodeAction[Raw]
  object DecodeAction {
    final case class ActionTaken[Raw](response: ServerInterpreterResponse[Raw]) extends DecodeAction[Raw]
    final case class None[Raw]() extends DecodeAction[Raw]
  }

  sealed trait InterpretationError
  object InterpretationError {
    case class NonUniqueMethod(names: List[MethodName]) extends InterpretationError
  }

  private def defaultEndpointHandler[F[_], Raw](responder: Responder[F, Raw], jsonSupport: JsonSupport[Raw]): EndpointHandler[F, Raw] = {
    new EndpointHandler[F, Raw] {
      override def onDecodeSuccess[I](
          ctx: DecodeSuccessContext[F, I, Raw]
      )(implicit monad: MonadError[F]): F[DecodeAction[Raw]] = {
        ctx.endpoint
          .logic(monad)(ctx.input)
          .flatMap { x =>
            val response = x match {
              case Left(value) =>
                val encodedError = ctx.endpoint.endpoint.error match {
                  case single @ JsonRpcErrorOutput.Single(_) =>
                    val error = single.error // TODO should JsonRpcErrorResponse contain JsonRpcError[T] instead of Json?
                    error.codec.encode(value.asInstanceOf[error.DATA]).asInstanceOf[Raw]
                }
                ctx.request.id.map(id => JsonRpcErrorResponse("2.0", encodedError, Some(id)))
              case Right(value) =>
                val encodedOutput = ctx.endpoint.endpoint.output match {
                  case _: JsonRpcIO.Empty[ctx.endpoint.OUTPUT]  => jsonSupport.jsNull
                  case o: JsonRpcIO.Single[ctx.endpoint.OUTPUT] => o.codec.encode(value).asInstanceOf[Raw]
                }
                ctx.request.id.map(JsonRpcSuccessResponse("2.0", encodedOutput, _))
            }
            responder.apply(response)
          }
      }

      override def onDecodeFailure(ctx: DecodeFailureContext[F, Raw])(implicit monad: MonadError[F]): F[DecodeAction[Raw]] = {
        val result: DecodeAction[Raw] = if (ctx.request.isNotification) {
          DecodeAction.ActionTaken(ServerInterpreterResponse.None())
        } else {
          DecodeAction.ActionTaken(ServerInterpreterResponse.Error(createErrorResponse(InvalidParams, ctx.request.id)))
        }
        monad.unit(result)
      }

      private def createErrorResponse(error: JsonRpcError[Unit], id: Option[JsonRpcId]): Raw = {
        jsonSupport.encodeResponse(JsonRpcResponse.error_v2(jsonSupport.encodeErrorNoData(error), id))
      }
    }
  }
}
