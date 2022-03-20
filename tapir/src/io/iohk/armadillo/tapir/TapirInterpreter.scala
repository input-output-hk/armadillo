package io.iohk.armadillo.tapir

import io.iohk.armadillo.*
import io.iohk.armadillo.Armadillo.{JsonRpcErrorNoData, JsonRpcErrorResponse, JsonRpcSuccessResponse}
import io.iohk.armadillo.tapir.TapirInterpreter.RichDecodeResult
import io.iohk.armadillo.tapir.Utils.RichEndpointInput
import sttp.model.StatusCode
import sttp.monad.MonadError
import sttp.monad.syntax.*
import sttp.tapir.Codec.JsonCodec
import sttp.tapir.EndpointIO.Info
import sttp.tapir.SchemaType.SCoproduct
import sttp.tapir.internal.ParamsAsVector
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.{CodecFormat, DecodeResult, EndpointIO, RawBodyType, Schema, statusCode}

import java.nio.charset.StandardCharsets

trait Provider[W[_]] {
  def codec[T](bodyCodec: JsonCodec[T]): JsonCodec[W[T]]
}

class TapirInterpreter[F[_], Json](jsonSupport: JsonSupport[Json])(implicit
    monadError: MonadError[F]
) {

  def apply(
      jsonRpcEndpoints: List[JsonRpcServerEndpoint[F]]
  ): ServerEndpoint.Full[Unit, Unit, String, JsonRpcErrorResponse[Json], JsonRpcSuccessResponse[Json], Any, F] = {
    sttp.tapir.endpoint.post
      .in(
        EndpointIO.Body(
          RawBodyType.StringBody(StandardCharsets.UTF_8),
          idJsonCodec,
          Info.empty
        )
      )
      .out(EndpointIO.Body(RawBodyType.StringBody(StandardCharsets.UTF_8), jsonSupport.outCodec, Info.empty))
      .errorOut(
        statusCode(StatusCode.Ok)
          .and(EndpointIO.Body(RawBodyType.StringBody(StandardCharsets.UTF_8), jsonSupport.errorOutCodec, Info.empty))
      )
      .serverLogic[F](serverLogic2(jsonRpcEndpoints, _).handleError { case _ =>
        monadError.unit(createErrorResponse(InternalError))
      })
  }

  private def serverLogic2(
      jsonRpcEndpoints: List[JsonRpcServerEndpoint[F]],
      stringRequest: String
  ): F[Either[JsonRpcErrorResponse[Json], JsonRpcSuccessResponse[Json]]] = {
    jsonSupport.parse(stringRequest) match {
      case _: DecodeResult.Failure => monadError.unit(createErrorResponse(ParseError))
      case DecodeResult.Value(jsonRequest) =>
        jsonSupport.fold(jsonRequest)(
          handleArray(jsonRpcEndpoints, _),
          handleObject(jsonRpcEndpoints, _),
          defaultHandler
        ) // TODO combine parse and fold?
    }

  }

  private def createErrorResponse(error: JsonRpcErrorNoData): Either[JsonRpcErrorResponse[Json], JsonRpcSuccessResponse[Json]] = {
    Left(JsonRpcErrorResponse("2.0", jsonSupport.encodeErrorNoData(error), 1))
  }

  private def handleArray(
      jsonRpcEndpoints: List[JsonRpcServerEndpoint[F]],
      array: Vector[Json]
  ): F[Either[JsonRpcErrorResponse[Json], JsonRpcSuccessResponse[Json]]] = {
    ???
  }

  private def handleObject(
      jsonRpcEndpoints: List[JsonRpcServerEndpoint[F]],
      obj: Json
  ): F[Either[JsonRpcErrorResponse[Json], JsonRpcSuccessResponse[Json]]] = {
    val codec = jsonSupport.inRpcCodec
    codec.decode(obj.asInstanceOf[codec.L]) match {
      case _: DecodeResult.Failure => monadError.unit(createErrorResponse(InvalidRequest))
      case DecodeResult.Value(request) =>
        jsonRpcEndpoints.find(_.endpoint.methodName.value == request.method) match {
          case None        => monadError.unit(createErrorResponse(MethodNotFound))
          case Some(value) => handleObjectWithEndpoint(value, request.params)
        }
    }
  }

  private def handleObjectWithEndpoint(
      serverEndpoint: JsonRpcServerEndpoint[F],
      jsonParams: Json
  ): F[Either[JsonRpcErrorResponse[Json], JsonRpcSuccessResponse[Json]]] = {
    decodeJsonRpcParamsForEndpoint(serverEndpoint.endpoint, jsonParams) match {
      case _: DecodeResult.Failure    => monadError.unit(createErrorResponse(InvalidParams))
      case DecodeResult.Value(params) => serverLogicForEndpoint(params, serverEndpoint)
    }
  }

  private def defaultHandler(json: Json): F[Either[JsonRpcErrorResponse[Json], JsonRpcSuccessResponse[Json]]] = {
    monadError.unit(createErrorResponse(InvalidRequest))
  }

  private def serverLogicForEndpoint(params: ParamsAsVector, matchedEndpoint: JsonRpcServerEndpoint[F]) = {
    val matchedBody = params.asAny.asInstanceOf[matchedEndpoint.INPUT]
    matchedEndpoint.logic(monadError)(matchedBody).map {
      case Left(value) =>
        val encodedError = matchedEndpoint.endpoint.error match {
          case single @ JsonRpcErrorOutput.Single(_) =>
            val error = single.error
            error.codec.encode(value.asInstanceOf[error.DATA]).asInstanceOf[Json]
        }
        Left(JsonRpcErrorResponse("2.0", encodedError, 1))
      case Right(value) =>
        val encodedOutput = matchedEndpoint.endpoint.output match {
          case o: JsonRpcIO.Empty[matchedEndpoint.OUTPUT]  => jsonSupport.emptyObject.asInstanceOf[o.codec.L]
          case o: JsonRpcIO.Single[matchedEndpoint.OUTPUT] => o.codec.encode(value)
        }
        Right(JsonRpcSuccessResponse("2.0", encodedOutput.asInstanceOf[Json], 1))
    }
  }

  private def decodeJsonRpcParamsForEndpoint(jsonRpcEndpoint: JsonRpcEndpoint[_, _, _], jsonParams: Json) = {
    val vectorCombinator = combineDecodeAsVector(jsonRpcEndpoint.input.asVectorOfBasicInputs)
    val objectCombinator = combineDecodeAsObject(jsonRpcEndpoint.input.asVectorOfBasicInputs)
    val combinedDecodeResult = vectorCombinator
      .apply(jsonParams)
      .orElse(objectCombinator.apply(jsonParams))
    combinedDecodeResult.map { paramsVector =>
      ParamsAsVector(paramsVector)
    }
  }

  private def combineEncodeAsVector(in: Vector[JsonRpcIO.Single[_]]): ParamsAsVector => Json = { params =>
    val ss = in.zipWithIndex.map { case (JsonRpcIO.Single(codec, _, _), index) =>
      codec.encode(params.asVector(index)).asInstanceOf[Json]
    }
    jsonSupport.asArray(ss)
  }

  // TODO This is left to be used when deriving sttp.client
  private def combineEncodeAsObject(in: Vector[JsonRpcIO.Single[_]]): ParamsAsVector => Json = { params =>
    val ss = in.zipWithIndex.map { case (JsonRpcIO.Single(codec, _, name), index) =>
      name -> codec.encode(params.asVector(index)).asInstanceOf[Json]
    }.toMap
    jsonSupport.asObject(ss)
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

  private val idJsonCodec: JsonCodec[String] = new JsonCodec[String] {
    override def rawDecode(l: String): DecodeResult[String] = DecodeResult.Value(l)

    override def encode(h: String): String = h

    override def schema: Schema[String] = anythingSchema[String]

    override def format: CodecFormat.Json = CodecFormat.Json()
  }

  private def anythingSchema[T]: Schema[T] = Schema[T](
    SCoproduct(Nil, None)(_ => None),
    None
  )

  private val ParseError = JsonRpcErrorNoData(-32700, "Parse error")
  private val InvalidRequest = JsonRpcErrorNoData(-32600, "Invalid Request")
  private val MethodNotFound = JsonRpcErrorNoData(-32601, "Method not found")
  private val InvalidParams = JsonRpcErrorNoData(-32602, "Invalid params")
  private val InternalError = JsonRpcErrorNoData(-32603, "Internal error")
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

    def fold[R](success: T => R, error: DecodeResult.Failure => R): R = {
      decodeResult match {
        case failure: DecodeResult.Failure => error(failure)
        case DecodeResult.Value(v)         => success(v)
      }
    }
  }
}
