package io.iohk.armadillo.tapir

import io.iohk.armadillo.tapir.TapirInterpreter.{JsonRpcRequest, JsonRpcResponse}
import io.iohk.armadillo.*
import sttp.tapir.Codec.JsonCodec
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.{Codec, EndpointIO, EndpointInput, EndpointOutput, PublicEndpoint, RawBodyType}

import java.nio.charset.StandardCharsets

trait Provider[W[_]] {
  def codec[T](bodyCodec: JsonCodec[T]): JsonCodec[W[T]]
}

class TapirInterpreter[Raw](jsonRpcRequestCodec: Provider[JsonRpcRequest], jsonRpcResponse: Provider[JsonRpcResponse]) {

  def apply[F[_]](jsonRpcEndpoints: List[JsonRpcServerEndpoint[F]]): List[ServerEndpoint[Any, F]] = {
    jsonRpcEndpoints.map(jsonRpcServerEndpointToTapir)
  }

  def jsonRpcServerEndpointToTapir[F[_]](serverEndpoint: JsonRpcServerEndpoint[F]): ServerEndpoint[Any, F] = {
    ServerEndpoint.public(jsonRpcEndpointToTapir(serverEndpoint.endpoint), serverEndpoint.logic)
  }

  def jsonRpcEndpointToTapir[I, E, O](endpoint: JsonRpcEndpoint[I, E, O]): PublicEndpoint[I, E, O, Any] = {
    sttp.tapir.endpoint.post
      .in(EndpointInput.FixedPath(endpoint.methodName.value, Codec.idPlain(), EndpointIO.Info.empty))
      .in(jsonRpcInputToTapir(endpoint.input))
      .errorOut(jsonRpcOutputToTapir(endpoint.error))
      .out(jsonRpcOutputToTapir(endpoint.output))
  }

  def jsonRpcInputToTapir[I](input: JsonRpcInput[I]): EndpointInput[I] = {
    input match {
      case o: JsonRpcIO[_] =>
        o match {
          case JsonRpcIO.Empty(codec, info) =>
            val rawType = RawBodyType.StringBody(StandardCharsets.UTF_8)
            EndpointIO.Body(rawType, jsonRpcRequestCodec.codec(codec), info)
          case JsonRpcIO.Single(codec, info) =>
            val rawType = RawBodyType.StringBody(StandardCharsets.UTF_8)
            EndpointIO.Body(rawType, jsonRpcRequestCodec.codec(codec), info)
        }
      case JsonRpcInput.Pair(left, right, combine, split) =>
        EndpointInput.Pair(jsonRpcInputToTapir(left), jsonRpcInputToTapir(right), combine, split)
    }
  }
  // Json => JsonRequest[Json]
  // Json => T
  def jsonRpcOutputToTapir[O](output: JsonRpcOutput[O]): EndpointOutput[O] = {
    output match {
      case o: JsonRpcIO[_] =>
        o match {
          case JsonRpcIO.Empty(codec, info) =>
            val rawType = RawBodyType.StringBody(StandardCharsets.UTF_8)
            EndpointIO.Body(rawType, jsonRpcResponse.codec(codec), info)
          case JsonRpcIO.Single(codec, info) =>
            val rawType = RawBodyType.StringBody(StandardCharsets.UTF_8)
            EndpointIO.Body(rawType, jsonRpcResponse.codec(codec), info)
        }
      case JsonRpcOutput.Pair(left, right, combine, split) =>
        EndpointOutput.Pair(jsonRpcOutputToTapir(left), jsonRpcOutputToTapir(right), combine, split)
    }
  }
}

object TapirInterpreter {
  case class JsonRpcRequest[Raw](jsonrpc: String, method: String, params: Raw, id: Int)
  case class JsonRpcRequestPartial(jsonrpc: String, method: String, id: Int)

  case class JsonRpcResponse[Raw](jsonrpc: String, result: Raw, id: Int)
}
