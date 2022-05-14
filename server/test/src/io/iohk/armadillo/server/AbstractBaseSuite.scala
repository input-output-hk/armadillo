package io.iohk.armadillo.server

import cats.effect.IO
import cats.syntax.all._
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder, Json}
import io.iohk.armadillo.server.ServerInterpreter.InterpretationError
import io.iohk.armadillo.{
  JsonRpcEndpoint,
  JsonRpcError,
  JsonRpcErrorResponse,
  JsonRpcRequest,
  JsonRpcResponse,
  JsonRpcServerEndpoint,
  JsonRpcSuccessResponse
}
import io.iohk.armadillo.json.circe._
import weaver.SimpleIOSuite

trait AbstractBaseSuite[Body, Interpreter] extends SimpleIOSuite {
  implicit val jsonRpcResponseDecoder: Decoder[JsonRpcResponse[Json]] =
    deriveDecoder[JsonRpcSuccessResponse[Json]].widen.or(deriveDecoder[JsonRpcErrorResponse[Json]].widen)

  implicit val jsonRpcRequestEncoder: Encoder[JsonRpcRequest[Json]] = deriveEncoder[JsonRpcRequest[Json]]
  implicit val jsonRpcRequestDecoder: Decoder[JsonRpcRequest[Json]] = deriveDecoder[JsonRpcRequest[Json]]

  def invalidBody: Body

  def testNotification[I, E, O, B: Encoder](
      endpoint: JsonRpcEndpoint[I, E, O],
      suffix: String = ""
  )(
      f: I => IO[Either[JsonRpcError[E], O]]
  )(request: B): Unit

  def testInvalidRequest[I, E, O](
      suffix: String = ""
  )(request: Body, expectedResponse: JsonRpcResponse[Json]): Unit

  def test[I, E, O, B: Encoder](
      endpoint: JsonRpcEndpoint[I, E, O],
      suffix: String = ""
  )(
      f: I => IO[Either[JsonRpcError[E], O]]
  )(request: B, expectedResponse: JsonRpcResponse[Json]): Unit

  def testMultiple[B: Encoder](name: String)(
      se: List[JsonRpcServerEndpoint[IO]]
  )(request: List[B], expectedResponse: List[JsonRpcResponse[Json]]): Unit

  def toInterpreter(se: List[JsonRpcServerEndpoint[IO]]): Either[InterpretationError, Interpreter]
}
