package io.iohk.armadillo.server

import io.iohk.armadillo.Armadillo.{JsonRpcError, JsonRpcErrorResponse, JsonRpcId, JsonRpcRequest, JsonRpcResponse, JsonRpcSuccessResponse}
import io.iohk.armadillo.server.JsonSupport.Json
import io.iohk.armadillo.server.ServerInterpreter.*
import io.iohk.armadillo.server.Utils.{RichDecodeResult, RichEndpointInput, RichMonadErrorOps}
import io.iohk.armadillo.{JsonRpcEndpoint, JsonRpcErrorOutput, JsonRpcIO, JsonRpcServerEndpoint, MethodName}
import sttp.monad.MonadError
import sttp.tapir.DecodeResult
import sttp.tapir.internal.ParamsAsVector
import sttp.monad.syntax.*

class ServerInterpreter[F[_], Raw] private (jsonRpcEndpoints: List[JsonRpcServerEndpoint[F]], jsonSupport: JsonSupport[Raw])(implicit
    monadError: MonadError[F]
) {

  def dispatchRequest(stringRequest: String): F[Result[Raw]] = {
    val result = jsonSupport.parse(stringRequest) match {
      case _: DecodeResult.Failure => monadError.unit(Result.RequestResponse(createErrorResponse(ParseError, None)): Result[Raw])
      case DecodeResult.Value(jsonRequest) =>
        jsonRequest match {
          case Json.JsonObject(raw)   => handleObject(jsonRpcEndpoints, raw)
          case Json.JsonArray(values) => handleBatchRequest(jsonRpcEndpoints, values)
          case Json.Other(raw)        => defaultHandler(raw)
        }
    }
    result.handleError { case _ =>
      monadError.unit(Result.RequestResponse(createErrorResponse(InternalError, None)))
    }
  }

  private def createErrorResponse(error: JsonRpcError[Unit], id: Option[JsonRpcId]): Raw = {
    jsonSupport.encodeError(JsonRpcResponse.error_v2(jsonSupport.encodeErrorNoData(error), id))
  }

  private def handleBatchRequest(
      jsonRpcEndpoints: List[JsonRpcServerEndpoint[F]],
      requests: Vector[Raw]
  ): F[Result[Raw]] = {
    requests
      .foldRight(monadError.unit(List.empty[Result[Raw]])) { case (req, accF) =>
        val fb = handleObject(jsonRpcEndpoints, req)
        fb.map2(accF)(_ :: _)
      }
      .map { responses =>
        val withoutNotifications = responses.collect { case Result.RequestResponse(response) => response }
        if (withoutNotifications.isEmpty) {
          Result.Notification()
        } else {
          Result.RequestResponse(jsonSupport.asArray(withoutNotifications.toVector))
        }
      }
  }

  private def handleObject(
      jsonRpcEndpoints: List[JsonRpcServerEndpoint[F]],
      obj: Raw
  ): F[Result[Raw]] = {
    jsonSupport.decodeJsonRpcRequest(obj) match {
      case _: DecodeResult.Failure =>
        monadError.unit(Result.RequestResponse(createErrorResponse(InvalidRequest, None)))
      case DecodeResult.Value(request) =>
        jsonRpcEndpoints.find(_.endpoint.methodName.value == request.method) match {
          case None        => monadError.unit(Result.RequestResponse(createErrorResponse(MethodNotFound, request.id)))
          case Some(value) => handleObjectWithEndpoint(value, request)
        }
    }
  }

  private def handleObjectWithEndpoint(
      serverEndpoint: JsonRpcServerEndpoint[F],
      request: JsonRpcRequest[Raw]
  ): F[Result[Raw]] = {
    decodeJsonRpcParamsForEndpoint(serverEndpoint.endpoint, request.params) match {
      case _: DecodeResult.Failure =>
        val result = if (request.isNotification) {
          Result.Notification[Raw]()
        } else {
          Result.RequestResponse(createErrorResponse(InvalidParams, request.id))
        }
        monadError.unit(result)
      case DecodeResult.Value(params) =>
        serverLogicForEndpoint(params, serverEndpoint, request.id)
          .handleError { case _ =>
            val result = if (request.isNotification) {
              Result.Notification[Raw]()
            } else {
              Result.RequestResponse[Raw](createErrorResponse(InternalError, request.id))
            }
            monadError.unit(result)
          }
    }
  }

  private def defaultHandler(json: Raw): F[Result[Raw]] =
    monadError.unit(Result.RequestResponse(createErrorResponse(InvalidRequest, None)))

  private def serverLogicForEndpoint(
      params: ParamsAsVector,
      matchedEndpoint: JsonRpcServerEndpoint[F],
      maybeRequestId: Option[JsonRpcId]
  ): F[Result[Raw]] = {
    val matchedBody = params.asAny.asInstanceOf[matchedEndpoint.INPUT]
    matchedEndpoint
      .logic(monadError)(matchedBody)
      .map {
        case Left(value) =>
          maybeRequestId match {
            case Some(requestId) =>
              val encodedError = matchedEndpoint.endpoint.error match {
                case single @ JsonRpcErrorOutput.Single(_) =>
                  val error = single.error // TODO should JsonRpcErrorResponse contain JsonRpcError[T] instead of Json?
                  error.codec.encode(value.asInstanceOf[error.DATA]).asInstanceOf[Raw]
              }
              Result.RequestResponse(jsonSupport.encodeError(JsonRpcErrorResponse("2.0", encodedError, Some(requestId))))
            case None => Result.Notification()
          }
        case Right(value) =>
          maybeRequestId match {
            case Some(requestId) =>
              val encodedOutput = matchedEndpoint.endpoint.output match {
                case o: JsonRpcIO.Empty[matchedEndpoint.OUTPUT]  => jsonSupport.jsNull.asInstanceOf[o.codec.L]
                case o: JsonRpcIO.Single[matchedEndpoint.OUTPUT] => o.codec.encode(value)
              }
              Result.RequestResponse(jsonSupport.encodeSuccess(JsonRpcSuccessResponse("2.0", encodedOutput.asInstanceOf[Raw], requestId)))
            case None => Result.Notification()
          }
      }
  }

  private def decodeJsonRpcParamsForEndpoint(jsonRpcEndpoint: JsonRpcEndpoint[_, _, _], jsonParams: Raw) = {
    val vectorCombinator = combineDecodeAsVector(jsonRpcEndpoint.input.asVectorOfBasicInputs)
    val objectCombinator = combineDecodeAsObject(jsonRpcEndpoint.input.asVectorOfBasicInputs)
    vectorCombinator
      .apply(jsonParams)
      .orElse(objectCombinator(jsonParams))
      .map(ParamsAsVector)
  }

  private def combineDecodeAsVector(in: Vector[JsonRpcIO.Single[_]]): Raw => DecodeResult[Vector[_]] = { json =>
    val ss = in.zipWithIndex.toList.map { case (JsonRpcIO.Single(codec, _, _), index) =>
      val rawElement = jsonSupport.getByIndex(json, index)
      rawElement.flatMap(r => codec.decode(r.asInstanceOf[codec.L]))
    }
    DecodeResult.sequence(ss).map(_.toVector)
  }

  private def combineDecodeAsObject(in: Vector[JsonRpcIO.Single[_]]): Raw => DecodeResult[Vector[_]] = { json =>
    val ss = in.toList.map { case JsonRpcIO.Single(codec, _, name) =>
      val rawElement = jsonSupport.getByField(json, name)
      rawElement.flatMap(r => codec.decode(r.asInstanceOf[codec.L]))
    }
    DecodeResult.sequence(ss).map(_.toVector)
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
      jsonSupport: JsonSupport[Raw]
  ): Either[InterpretationError, ServerInterpreter[F, Raw]] = {
    val nonUniqueMethodNames = jsonRpcEndpoints.groupBy(_.endpoint.methodName).values.filter(_.size != 1).map(_.head.endpoint.methodName)
    Either.cond(
      nonUniqueMethodNames.isEmpty,
      new ServerInterpreter(jsonRpcEndpoints, jsonSupport),
      InterpretationError.NonUniqueMethod(nonUniqueMethodNames.toList)
    )
  }

  sealed trait InterpretationError
  object InterpretationError {
    case class NonUniqueMethod(names: List[MethodName]) extends InterpretationError
  }

  sealed trait Result[T]
  object Result {
    case class RequestResponse[T](value: T) extends Result[T]
    case class Notification[T]() extends Result[T]
  }

}
