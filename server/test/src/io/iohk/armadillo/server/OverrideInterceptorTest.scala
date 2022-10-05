package io.iohk.armadillo.server

import cats.effect.{IO, Ref}
import cats.syntax.all._
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.literal._
import io.circe.{Decoder, Encoder, Json}
import io.iohk.armadillo.json.circe.{CirceJsonSupport, _}
import io.iohk.armadillo.server.ServerInterpreter.ServerResponse
import io.iohk.armadillo.server._
import io.iohk.armadillo.{JsonRpcErrorResponse, JsonRpcRequest, JsonRpcResponse, JsonRpcSuccessResponse}
import sttp.tapir.integ.cats.CatsMonadError
import weaver.SimpleIOSuite

object OverrideInterceptorTest extends SimpleIOSuite with CirceEndpoints {
  implicit lazy val jsonRpcResponseDecoder: Decoder[JsonRpcResponse[Json]] =
    deriveDecoder[JsonRpcSuccessResponse[Json]].widen.or(deriveDecoder[JsonRpcErrorResponse[Json]].widen)

  implicit lazy val jsonRpcRequestEncoder: Encoder[JsonRpcRequest[Json]] = deriveEncoder[JsonRpcRequest[Json]]
  implicit lazy val jsonRpcRequestDecoder: Decoder[JsonRpcRequest[Json]] = deriveDecoder[JsonRpcRequest[Json]]
  implicit val monadError: CatsMonadError[IO] = new CatsMonadError[IO]
  private val jsonSupport = new CirceJsonSupport

  test("should return method not found when calling non-existing overrided endpoint") {
    val interpreter = ServerInterpreter.applyUnsafe(List.empty, jsonSupport, CustomInterceptors[IO, Json]().interceptors)

    interpreter
      .dispatchRequest(
        jsonSupport.stringify(Encoder[JsonRpcRequest[Json]].apply(JsonRpcRequest.v2[Json]("hello", json"[42]", 1)))
      )
      .map {
        case Some(ServerResponse.Failure(json)) =>
          expect(json == json"""{
                               "jsonrpc" : "2.0",
                               "error" : ${ServerInterpreter.MethodNotFound},
                               "id" : 1
                             }""")
        case _ => failure("Expected server fail response")
      }
  }

  test("should not call the original endpoint and return overrided response") {
    val ref = Ref.unsafe(0)
    val originalEndpoint = hello_in_int_out_string.serverLogic { i =>
      ref.update(_ + 1) >> IO.pure(s"greetings $i".asRight[Unit])
    }
    val overridingEndpoint = originalEndpoint.`override`.thenReturn(IO.pure("stubbed response".asRight[Unit]))
    val interpreter = ServerInterpreter.applyUnsafe(
      List(originalEndpoint),
      jsonSupport,
      CustomInterceptors[IO, Json](overridedEndpoints = List(overridingEndpoint)).interceptors
    )

    interpreter
      .dispatchRequest(
        jsonSupport.stringify(Encoder[JsonRpcRequest[Json]].apply(JsonRpcRequest.v2[Json]("hello", json"[42]", 1)))
      )
      .product(ref.get)
      .map {
        case (Some(ServerResponse.Success(json)), counter) =>
          expect(json == json"""{
                          "jsonrpc" : "2.0",
                          "result" : "stubbed response",
                          "id" : 1
                        }
                        """).and(expect(counter == 0))
        case _ => failure("Expected server success response")
      }
  }

  test("should call override before the original logic") {
    val ref = Ref.unsafe(0)
    val originalEndpoint = hello_in_int_out_string.serverLogic { i =>
      ref.update(_ + 2) >> IO.pure(s"greetings $i".asRight[Unit])
    }
    val overridingEndpoint = originalEndpoint.`override`.runBeforeLogic(ref.update(_ * 2))
    val interpreter = ServerInterpreter.applyUnsafe(
      List(originalEndpoint),
      jsonSupport,
      CustomInterceptors[IO, Json](overridedEndpoints = List(overridingEndpoint)).interceptors
    )

    interpreter
      .dispatchRequest(
        jsonSupport.stringify(Encoder[JsonRpcRequest[Json]].apply(JsonRpcRequest.v2[Json]("hello", json"[42]", 1)))
      )
      .product(ref.get)
      .map {
        case (Some(ServerResponse.Success(json)), counter) =>
          expect(json == json"""{
                          "jsonrpc" : "2.0",
                          "result" : "greetings 42",
                          "id" : 1
                        }
                        """).and(expect(counter == 2))
        case _ => failure("Expected server success response")
      }
  }

  test("should call override after the original logic") {
    val ref = Ref.unsafe(0)
    val originalEndpoint = hello_in_int_out_string.serverLogic { i =>
      ref.update(_ + 2) >> IO.pure(s"greetings $i".asRight[Unit])
    }
    val overridingEndpoint = originalEndpoint.`override`.runAfterLogic(ref.update(_ * 2))
    val interpreter = ServerInterpreter.applyUnsafe(
      List(originalEndpoint),
      jsonSupport,
      CustomInterceptors[IO, Json](overridedEndpoints = List(overridingEndpoint)).interceptors
    )

    interpreter
      .dispatchRequest(
        jsonSupport.stringify(Encoder[JsonRpcRequest[Json]].apply(JsonRpcRequest.v2[Json]("hello", json"[42]", 1)))
      )
      .product(ref.get)
      .map {
        case (Some(ServerResponse.Success(json)), counter) =>
          expect(json == json"""{
                          "jsonrpc" : "2.0",
                          "result" : "greetings 42",
                          "id" : 1
                        }
                        """).and(expect(counter == 4))
        case _ => failure("Expected server success response")
      }
  }
}
