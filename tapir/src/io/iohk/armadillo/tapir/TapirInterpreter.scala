package io.iohk.armadillo.tapir

import io.iohk.armadillo.*
import io.iohk.armadillo.Armadillo.{JsonRpcErrorResponse, JsonRpcRequest, JsonRpcSuccessResponse, jsonRpcEndpoint}
import io.iohk.armadillo.tapir.TapirInterpreter.RichDecodeResult
import io.iohk.armadillo.tapir.Utils.{RichEndpointErrorPart, RichEndpointInput}
import sttp.model.StatusCode
import sttp.monad.MonadError
import sttp.monad.syntax.*
import sttp.tapir.Codec.{JsonCodec, json}
import sttp.tapir.EndpointIO.Info
import sttp.tapir.SchemaType.SCoproduct
import sttp.tapir.internal.{ParamsAsAny, ParamsAsVector}
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
      .serverLogic[F](serverLogic2(jsonRpcEndpoints, _))
  }

  private def serverLogic2(
      jsonRpcEndpoints: List[JsonRpcServerEndpoint[F]],
      stringRequest: String
  ): F[Either[JsonRpcErrorResponse[Json], JsonRpcSuccessResponse[Json]]] = {
    jsonSupport.parse(stringRequest) match {
      case failure: DecodeResult.Failure => ??? // TODO parseError
      case DecodeResult.Value(jsonRequest) =>
        jsonSupport.fold(jsonRequest)(
          handleArray(jsonRpcEndpoints, _),
          handleObject(jsonRpcEndpoints, _),
          defaultHandler
        ) // TODO combine parse and fold?
    }

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
      case _: DecodeResult.Failure => monadError.unit(Left(JsonRpcErrorResponse("2.0", ???, 1))) // TODO invalidRequest
      case DecodeResult.Value(request) =>
        jsonRpcEndpoints.find(_.endpoint.methodName.value == request.method) match {
          case None =>
            throw new IllegalStateException(
              "Cannot happen because filtering codec passes through only matching methods"
            ) // TODO return notFoundError
          case Some(value) => handleObjectWithEndpoint(value, request.params)
        }
    }
  }

  private def handleObjectWithEndpoint(
      serverEndpoint: JsonRpcServerEndpoint[F],
      jsonParams: Json
  ): F[Either[JsonRpcErrorResponse[Json], JsonRpcSuccessResponse[Json]]] = {
    decodeJsonRpcParamsForEndpoint(serverEndpoint.endpoint, jsonParams) match {
      case failure: DecodeResult.Failure => ??? // TODO invalidParams
      case DecodeResult.Value(params)    => serverLogicForEndpoint(params, serverEndpoint)
    }
  }

  private def defaultHandler(json: Json): F[Either[JsonRpcErrorResponse[Json], JsonRpcSuccessResponse[Json]]] = {
    ??? // TODO invalidRequest
  }

  private def serverLogicForEndpoint(params: ParamsAsVector, matchedEndpoint: JsonRpcServerEndpoint[F]) = {
    val matchedBody = params.asAny.asInstanceOf[matchedEndpoint.INPUT]
    matchedEndpoint.logic(monadError)(matchedBody).map {
      case Left(value) =>
        val encodedError = matchedEndpoint.endpoint.error match {
          case JsonRpcErrorOutput.EmptyError(_, _) => Vector.empty[Json]
          case JsonRpcErrorOutput.Single(error) =>
            val valueAsVector = ParamsAsAny(value).asVector
            val partsAsVector = error.asVectorOfBasicErrorParts
            val jsonVector = valueAsVector.zip(partsAsVector).map { case (value, part) =>
              part.codec.encode(value.asInstanceOf[part.DATA]).asInstanceOf[Json]
            }
            jsonVector
        }
        Left(JsonRpcErrorResponse("2.0", jsonSupport.asArray(encodedError), 1))
      case Right(value) =>
        val encodedOutput = matchedEndpoint.endpoint.output match {
          case o: JsonRpcIO.Empty[matchedEndpoint.OUTPUT]  => jsonSupport.emptyObject.asInstanceOf[o.codec.L]
          case o: JsonRpcIO.Single[matchedEndpoint.OUTPUT] => o.codec.encode(value)
        }
        Right(JsonRpcSuccessResponse("2.0", encodedOutput.asInstanceOf[Json], 1))
    }
  }

  private def encodeJsonRpcParams(endpoints: List[JsonRpcEndpoint[_, _, _]], envelop: JsonRpcRequest[ParamsAsVector]) = {
    endpoints.find(_.methodName.value == envelop.method) match {
      case Some(jsonRpcEndpoint) =>
        encodeJsonRpcParamsForEndpoint(jsonRpcEndpoint, envelop)
      case None => ???
    }
  }

  private def encodeJsonRpcParamsForEndpoint(jsonRpcEndpoint: JsonRpcEndpoint[_, _, _], envelop: JsonRpcRequest[ParamsAsVector]) = {
    val encoder = combineEncodeAsVector(jsonRpcEndpoint.input.asVectorOfBasicInputs)
    envelop.copy(params = encoder(envelop.params))
  }

  private def decodeJsonRpcParams(endpoints: List[JsonRpcEndpoint[_, _, _]], envelop: JsonRpcRequest[Json]) = {
    endpoints.find(_.methodName.value == envelop.method) match {
      case Some(jsonRpcEndpoint) =>
        decodeJsonRpcParamsForEndpoint(jsonRpcEndpoint, envelop.params)
      case None =>
        DecodeResult.Mismatch(endpoints.map(_.methodName).mkString(","), envelop.method)
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
