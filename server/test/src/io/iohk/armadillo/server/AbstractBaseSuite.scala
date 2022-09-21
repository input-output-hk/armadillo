package io.iohk.armadillo.server

import cats.effect.IO
import cats.syntax.all._
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder, Json}
import io.iohk.armadillo.json.circe._
import io.iohk.armadillo.json.json4s.Json4sSupport
import io.iohk.armadillo.server.ServerInterpreter.InterpretationError
import io.iohk.armadillo.{
  JsonRpcEndpoint,
  JsonRpcErrorResponse,
  JsonRpcRequest,
  JsonRpcResponse,
  JsonRpcServerEndpoint,
  JsonRpcSuccessResponse
}
import org.json4s.JsonAST.JValue
import org.json4s.{Formats, NoTypeHints, Serialization}
import weaver.SimpleIOSuite

trait AbstractCirceSuite[Body, Interpreter] extends AbstractBaseSuite[Json, Body, Interpreter] {
  override val jsonSupport: CirceJsonSupport = new CirceJsonSupport
}
trait AbstractJson4sSuite[Body, Interpreter] extends AbstractBaseSuite[JValue, Body, Interpreter] {
  implicit val serialization: Serialization = org.json4s.jackson.Serialization
  implicit val formats: Formats = org.json4s.jackson.Serialization.formats(NoTypeHints)
  override val jsonSupport: Json4sSupport = Json4sSupport(org.json4s.jackson.parseJson(_), org.json4s.jackson.compactJson)
}

trait AbstractBaseSuite[Raw, Body, Interpreter] extends SimpleIOSuite {
  implicit val jsonRpcResponseDecoder: Decoder[JsonRpcResponse[Json]] =
    deriveDecoder[JsonRpcSuccessResponse[Json]].widen.or(deriveDecoder[JsonRpcErrorResponse[Json]].widen)

  implicit val jsonRpcRequestEncoder: Encoder[JsonRpcRequest[Json]] = deriveEncoder[JsonRpcRequest[Json]]
  implicit val jsonRpcRequestDecoder: Decoder[JsonRpcRequest[Json]] = deriveDecoder[JsonRpcRequest[Json]]

  val jsonSupport: JsonSupport[Raw]

  def invalidJson: Body
  def jsonNotAnObject: Body

  def testNotification[I, E, O, B: Encoder](
      endpoint: JsonRpcEndpoint[I, E, O],
      suffix: String = ""
  )(
      f: I => IO[Either[E, O]]
  )(request: B): Unit

  def testInvalidRequest[I, E, O](
      suffix: String = ""
  )(request: Body, expectedResponse: JsonRpcResponse[Json]): Unit

  def test[I, E, O, B: Encoder](
      endpoint: JsonRpcEndpoint[I, E, O],
      suffix: String = ""
  )(
      f: I => IO[Either[E, O]]
  )(request: B, expectedResponse: JsonRpcResponse[Json]): Unit

  def testServerError[I, E, O, B: Encoder](
      endpoint: JsonRpcEndpoint[I, E, O],
      suffix: String = ""
  )(
      f: I => IO[Either[E, O]]
  )(request: B, expectedResponse: JsonRpcResponse[Json]): Unit

  def testMultiple[B: Encoder](name: String)(
      se: List[JsonRpcServerEndpoint[IO]]
  )(request: List[B], expectedResponse: List[JsonRpcResponse[Json]]): Unit

  def toInterpreter(se: List[JsonRpcServerEndpoint[IO]]): Either[InterpretationError, Interpreter]
}
