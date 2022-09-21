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
  JsonRpcId,
  JsonRpcRequest,
  JsonRpcResponse,
  JsonRpcServerEndpoint,
  JsonRpcSuccessResponse
}
import org.json4s.JsonAST.JValue
import org.json4s.{CustomSerializer, Formats, JInt, JString, NoTypeHints, Serialization}
import weaver.SimpleIOSuite

trait AbstractCirceSuite[Body, Interpreter] extends AbstractBaseSuite[Json, Body, Interpreter] {
  type Enc[T] = Encoder[T]
  override lazy val jsonSupport: CirceJsonSupport = new CirceJsonSupport
  implicit lazy val jsonRpcResponseDecoder: Decoder[JsonRpcResponse[Json]] =
    deriveDecoder[JsonRpcSuccessResponse[Json]].widen.or(deriveDecoder[JsonRpcErrorResponse[Json]].widen)

  implicit lazy val jsonRpcRequestEncoder: Encoder[JsonRpcRequest[Json]] = deriveEncoder[JsonRpcRequest[Json]]
  implicit lazy val jsonRpcRequestDecoder: Decoder[JsonRpcRequest[Json]] = deriveDecoder[JsonRpcRequest[Json]]
}
trait AbstractJson4sSuite[Body, Interpreter] extends AbstractBaseSuite[JValue, Body, Interpreter] {
  type Enc[T] = Unit
  object JsonRpcIdSerializer
      extends CustomSerializer[JsonRpcId](_ =>
        (
          {
            case JString(str) => JsonRpcId.StringId(str)
            case JInt(int)    => JsonRpcId.IntId(int.toInt)
          },
          {
            case JsonRpcId.IntId(int)    => JInt(int)
            case JsonRpcId.StringId(str) => JString(str)
          }
        )
      )
  implicit lazy val serialization: Serialization = org.json4s.jackson.Serialization
  implicit lazy val formats: Formats = org.json4s.jackson.Serialization.formats(NoTypeHints) + JsonRpcIdSerializer
  override lazy val jsonSupport: Json4sSupport = Json4sSupport(org.json4s.jackson.parseJson(_), org.json4s.jackson.compactJson)
}

trait AbstractBaseSuite[Raw, Body, Interpreter] extends SimpleIOSuite {
  type Enc[T]

  def jsonSupport: JsonSupport[Raw]

  def invalidJson: Body
  def jsonNotAnObject: Body

  def testNotification[I, E, O](
      endpoint: JsonRpcEndpoint[I, E, O],
      suffix: String = ""
  )(
      f: I => IO[Either[E, O]]
  )(request: JsonRpcRequest[Raw]): Unit

  def testInvalidRequest[I, E, O](
      suffix: String = ""
  )(request: Body, expectedResponse: JsonRpcResponse[Raw]): Unit

  def test[I, E, O, B: Enc](
      endpoint: JsonRpcEndpoint[I, E, O],
      suffix: String = ""
  )(
      f: I => IO[Either[E, O]]
  )(request: B, expectedResponse: JsonRpcResponse[Raw]): Unit

  def testServerError[I, E, O](
      endpoint: JsonRpcEndpoint[I, E, O],
      suffix: String = ""
  )(
      f: I => IO[Either[E, O]]
  )(request: JsonRpcRequest[Raw], expectedResponse: JsonRpcResponse[Raw]): Unit

  def testMultiple[B: Enc](name: String)(
      se: List[JsonRpcServerEndpoint[IO]]
  )(request: List[B], expectedResponse: List[JsonRpcResponse[Raw]]): Unit

  def toInterpreter(se: List[JsonRpcServerEndpoint[IO]]): Either[InterpretationError, Interpreter]
}
