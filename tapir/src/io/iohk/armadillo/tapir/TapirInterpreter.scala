package io.iohk.armadillo.tapir

import io.iohk.armadillo.*
import io.iohk.armadillo.Armadillo.*
import io.iohk.armadillo.JsonSupport.Json
import io.iohk.armadillo.tapir.TapirInterpreter.{InterpretationError, Result, RichDecodeResult, RichMonadErrorOps}
import io.iohk.armadillo.tapir.Utils.RichEndpointInput
import sttp.monad.MonadError
import sttp.monad.syntax.*
import sttp.tapir.Codec.JsonCodec
import sttp.tapir.EndpointIO.Info
import sttp.tapir.SchemaType.SCoproduct
import sttp.tapir.internal.ParamsAsVector
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.{CodecFormat, DecodeResult, EndpointIO, RawBodyType, Schema}

import java.nio.charset.StandardCharsets

class TapirInterpreter[F[_], Raw](jsonSupport: JsonSupport[Raw])(implicit
    monadError: MonadError[F]
) {

  def apply(
      jsonRpcEndpoints: List[JsonRpcServerEndpoint[F]]
  ): Either[InterpretationError, ServerEndpoint.Full[Unit, Unit, String, Unit, Raw, Any, F]] = {
    val nonUniqueMethodNames = jsonRpcEndpoints.groupBy(_.endpoint.methodName).values.filter(_.size != 1).map(_.head.endpoint.methodName)
    Either.cond(
      nonUniqueMethodNames.isEmpty,
      toTapirEndpoint(jsonRpcEndpoints),
      InterpretationError.NonUniqueMethod(nonUniqueMethodNames.toList)
    )
  }

  private def toTapirEndpoint(jsonRpcEndpoints: List[JsonRpcServerEndpoint[F]]) = {
    sttp.tapir.endpoint.post
      .in(
        EndpointIO.Body(
          RawBodyType.StringBody(StandardCharsets.UTF_8),
          idJsonCodec,
          Info.empty
        )
      )
      .errorOut(sttp.tapir.statusCode(sttp.model.StatusCode.Ok))
      .out(EndpointIO.Body(RawBodyType.StringBody(StandardCharsets.UTF_8), jsonSupport.outRawCodec, Info.empty))
      .serverLogic[F] { input =>
        requestDispatcher(jsonRpcEndpoints, input)
          .map {
            case Result.RequestResponse(r) => Right(r)
            case Result.Notification()     => Left(())
          }
      }
  }

  private def requestDispatcher(
      jsonRpcEndpoints: List[JsonRpcServerEndpoint[F]],
      stringRequest: String
  ): F[Result[Raw]] = {
    val result = jsonSupport.parse(stringRequest) match {
      case _: DecodeResult.Failure => monadError.unit(Result.RequestResponse(createErrorResponse(ParseError, None)): Result[Raw])
      case DecodeResult.Value(jsonRequest) =>
        jsonRequest match {
          case Json.JsonObject(raw)   => handleObject(jsonRpcEndpoints, raw)
          case Json.JsonArray(values) => handleBatchRequest(jsonRpcEndpoints, values)
          case Json.Other(raw)        => defaultHandler(raw)
        }
    }
    result.handleError[Any] { case _ =>
      monadError.unit(Result.RequestResponse[Raw](createErrorResponse(InternalError, None)): Result[Raw])
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

  private val idJsonCodec: JsonCodec[String] = new JsonCodec[String] {
    override def rawDecode(l: String): DecodeResult[String] = DecodeResult.Value(l)

    override def encode(h: String): String = h

    override def schema: Schema[String] = Schema[String](
      SCoproduct(Nil, None)(_ => None),
      None
    )

    override def format: CodecFormat.Json = CodecFormat.Json()
  }

  private val ParseError = JsonRpcError[Unit](-32700, "Parse error", ())
  private val InvalidRequest = JsonRpcError[Unit](-32600, "Invalid Request", ())
  private val MethodNotFound = JsonRpcError[Unit](-32601, "Method not found", ())
  private val InvalidParams = JsonRpcError[Unit](-32602, "Invalid params", ())
  private val InternalError = JsonRpcError[Unit](-32603, "Internal error", ())
}

object TapirInterpreter {
  sealed trait InterpretationError
  object InterpretationError {
    case class NonUniqueMethod(names: List[MethodName]) extends InterpretationError
  }

  private sealed trait Result[T]
  private object Result {
    case class RequestResponse[T](value: T) extends Result[T]
    case class Notification[T]() extends Result[T]
  }

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

    def fold[R](success: T => R, error: DecodeResult.Failure => R): R = {
      decodeResult match {
        case failure: DecodeResult.Failure => error(failure)
        case DecodeResult.Value(v)         => success(v)
      }
    }
  }

  implicit class RichMonadErrorOps[F[_]: MonadError, A](fa: F[A]) {
    def map2[B, C](fb: F[B])(f: (A, B) => C): F[C] = {
      fa.flatMap { a =>
        fb.map(b => f(a, b))
      }
    }
  }
}
