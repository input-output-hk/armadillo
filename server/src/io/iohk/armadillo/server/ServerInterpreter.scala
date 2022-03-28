package io.iohk.armadillo.server

import io.iohk.armadillo.*
import io.iohk.armadillo.server.EndpointHandler.{DecodeFailureContext, DecodeSuccessContext}
import io.iohk.armadillo.server.JsonSupport.Json
import io.iohk.armadillo.server.ServerInterpreter.*
import io.iohk.armadillo.server.Utils.RichEndpointInput
import sttp.monad.MonadError
import sttp.monad.syntax.*
import sttp.tapir.DecodeResult
import sttp.tapir.internal.ParamsAsVector

class ServerInterpreter[F[_], Raw] private (
    jsonRpcEndpoints: List[JsonRpcServerEndpoint[F]],
    jsonSupport: JsonSupport[Raw],
    interceptors: List[Interceptor[F, Raw]]
)(implicit
    monadError: MonadError[F]
) {

  def dispatchRequest(stringRequest: String): F[Option[Raw]] = {
    jsonSupport.parse(stringRequest) match {
      case f: DecodeResult.Failure =>
        monadError.suspend(
          callRequestInterceptors(interceptors, Nil, defaultResponder).onDecodeFailure(
            RequestHandler.DecodeFailureContext(f, stringRequest)
          )
        )
      case DecodeResult.Value(jsonRequest) =>
        monadError.suspend(callRequestInterceptors(interceptors, Nil, defaultResponder).onDecodeSuccess(jsonRequest))
    }
  }

  def callRequestInterceptors(
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
    }
  }

  private def defaultRequestHandler(eis: List[MethodOrEndpointInterceptor[F, Raw]]) = {
    new RequestHandler[F, Raw] {
      override def onDecodeSuccess(request: Json[Raw])(implicit monad: MonadError[F]): F[Option[Raw]] = {
        request match {
          case obj: Json.JsonObject[Raw] => handleObject(jsonRpcEndpoints, obj, eis)
          case _                         => monad.unit(Option.empty[Raw])
        }
      }

      override def onDecodeFailure(ctx: RequestHandler.DecodeFailureContext)(implicit monad: MonadError[F]): F[Option[Raw]] = {
        monad.unit(None)
      }
    }
  }

  private def defaultMethodHandler(eis: List[EndpointInterceptor[F, Raw]]) = {
    new MethodHandler[F, Raw] {
      override def onDecodeSuccess[I](ctx: MethodHandler.DecodeSuccessContext[F, Raw])(implicit monad: MonadError[F]): F[Option[Raw]] = {
        ctx.endpoints.find(_.endpoint.methodName.asString == ctx.request.method) match {
          case None        => monadError.unit(None)
          case Some(value) => handleObjectWithEndpoint(value, ctx.request, eis)
        }
      }

      override def onDecodeFailure(ctx: MethodHandler.DecodeFailureContext[F, Raw])(implicit monad: MonadError[F]): F[Option[Raw]] = {
        monadError.unit(None)
      }
    }
  }

  private def handleObject(
      jsonRpcEndpoints: List[JsonRpcServerEndpoint[F]],
      obj: Json.JsonObject[Raw],
      eis: List[MethodOrEndpointInterceptor[F, Raw]]
  ): F[Option[Raw]] = {
    jsonSupport.decodeJsonRpcRequest(obj) match {
      case failure: DecodeResult.Failure =>
        val ctx = MethodHandler.DecodeFailureContext(jsonRpcEndpoints, obj, failure)
        monadError.suspend(callMethodOrEndpointInterceptors(eis, Nil, defaultResponder).onDecodeFailure(ctx))
      case DecodeResult.Value(v) =>
        val ctx = MethodHandler.DecodeSuccessContext(jsonRpcEndpoints, v)
        monadError.suspend(callMethodOrEndpointInterceptors(eis, Nil, defaultResponder).onDecodeSuccess(ctx))
    }
  }

  def callMethodOrEndpointInterceptors(
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
    }
  }

  private def handleObjectWithEndpoint(
      se: JsonRpcServerEndpoint[F],
      request: JsonRpcRequest[Json[Raw]],
      eis: List[EndpointInterceptor[F, Raw]]
  ): F[Option[Raw]] = {
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
  ) = {
    handler.onDecodeSuccess[serverEndpoint.INPUT](
      DecodeSuccessContext[F, serverEndpoint.INPUT, Raw](serverEndpoint, request, matchedBody)
    )
  }

  private val defaultResponder: Responder[F, Raw] = new Responder[F, Raw] {
    override def apply(response: Option[JsonRpcResponse[Raw]]): F[Option[Raw]] = {
      response.map(jsonSupport.encodeResponse).unit
    }
  }

  private def decodeJsonRpcParamsForEndpoint(
      jsonRpcEndpoint: JsonRpcEndpoint[_, _, _],
      jsonParams: Json[Raw]
  ): DecodeResult[ParamsAsVector] = {
    val vectorCombinator = combineDecodeAsVector(jsonRpcEndpoint.input.asVectorOfBasicInputs)
    val objectCombinator = combineDecodeAsObject(jsonRpcEndpoint.input.asVectorOfBasicInputs)
    val result = jsonParams match {
      case obj: Json.JsonObject[Raw] =>
        jsonRpcEndpoint.paramStructure match {
          case ParamStructure.Either     => objectCombinator(obj)
          case ParamStructure.ByName     => objectCombinator(obj)
          case ParamStructure.ByPosition => DecodeResult.Mismatch("json object", jsonSupport.stringify(jsonSupport.demateralize(obj)))
        }
      case arr: Json.JsonArray[Raw] =>
        jsonRpcEndpoint.paramStructure match {
          case ParamStructure.Either     => vectorCombinator(arr)
          case ParamStructure.ByPosition => vectorCombinator(arr)
          case ParamStructure.ByName     => DecodeResult.Mismatch("json object", jsonSupport.stringify(jsonSupport.demateralize(arr)))
        }
      case Json.Other(raw) => DecodeResult.Mismatch("json array or json object", jsonSupport.stringify(raw))
    }
    result.map(ParamsAsVector)
  }

  private def combineDecodeAsVector(in: Vector[JsonRpcIO.Single[_]]): Json.JsonArray[Raw] => DecodeResult[Vector[_]] = { json =>
    if (json.values.size == in.size) {
      val ss = in.zipWithIndex.toList.map { case (JsonRpcIO.Single(codec, _, _), index) =>
        val rawElement = json.values(index)
        codec.decode(rawElement.asInstanceOf[codec.L])
      }
      DecodeResult.sequence(ss).map(_.toVector)
    } else {
      DecodeResult.Mismatch(s"expected ${in.size} parameters", s"got ${json.values.size}")
    }
  }

  private def combineDecodeAsObject(in: Vector[JsonRpcIO.Single[_]]): Json.JsonObject[Raw] => DecodeResult[Vector[_]] = { json =>
    if (json.fields.size == in.size) {
      val ss = in.toList.map { case JsonRpcIO.Single(codec, _, name) =>
        val rawElement = json.fields.toMap.get(name) match {
          case Some(value) => DecodeResult.Value(value)
          case None        => DecodeResult.Missing
        }
        rawElement.flatMap(r => codec.decode(r.asInstanceOf[codec.L]))
      }
      DecodeResult.sequence(ss).map(_.toVector)
    } else {
      DecodeResult.Mismatch(s"expected ${in.size} parameters", s"got ${json.fields.size}")
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

  sealed trait InterpretationError
  object InterpretationError {
    case class NonUniqueMethod(names: List[MethodName]) extends InterpretationError
  }

  private def defaultEndpointHandler[F[_], Raw](responder: Responder[F, Raw], jsonSupport: JsonSupport[Raw]): EndpointHandler[F, Raw] = {
    new EndpointHandler[F, Raw] {
      override def onDecodeSuccess[I](ctx: DecodeSuccessContext[F, I, Raw])(implicit monad: MonadError[F]): F[Option[Raw]] = {
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
                  case o: JsonRpcIO.Empty[ctx.endpoint.OUTPUT]  => jsonSupport.jsNull
                  case o: JsonRpcIO.Single[ctx.endpoint.OUTPUT] => o.codec.encode(value).asInstanceOf[Raw]
                }
                ctx.request.id.map(JsonRpcSuccessResponse("2.0", encodedOutput, _))
            }
            responder.apply(response)
          }
      }

      override def onDecodeFailure(ctx: DecodeFailureContext[F, Raw])(implicit monad: MonadError[F]): F[Option[Raw]] = {
        val result = if (ctx.request.isNotification) {
          None
        } else {
          Some(createErrorResponse(InvalidParams, ctx.request.id))
        }
        monad.unit(result)
      }

      private def createErrorResponse(error: JsonRpcError[Unit], id: Option[JsonRpcId]): Raw = {
        jsonSupport.encodeResponse(JsonRpcResponse.error_v2(jsonSupport.encodeErrorNoData(error), id))
      }
    }
  }
}
