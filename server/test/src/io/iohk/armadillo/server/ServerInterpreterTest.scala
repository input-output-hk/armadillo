package io.iohk.armadillo.server

import cats.effect.IO
import io.circe.{Encoder, Json}
import io.iohk.armadillo.*
import io.iohk.armadillo.json.circe.CirceJsonSupport
import io.iohk.armadillo.server.Endpoints.hello_in_int_out_string
import sttp.tapir.integ.cats.CatsMonadError

object ServerInterpreterTest
    extends AbstractServerSuite[String, ServerInterpreter[IO, Json]]
    with AbstractBaseSuite[String, ServerInterpreter[IO, Json]] {
  override def invalidBody: String = """{"jsonrpc": "2.0", "method": "foobar, "params": "bar", "baz]"""

  override def testNotification[I, E, O, B: Encoder](endpoint: JsonRpcEndpoint[I, E, O], suffix: String)(
      f: I => IO[Either[JsonRpcError[E], O]]
  )(request: B): Unit = {
    test(endpoint.showDetail + " as notification " + suffix) {
      val interpreter = createInterpreter(List(endpoint.serverLogic(f)))
      val strRequest = Encoder[B].apply(request).noSpaces
      interpreter.dispatchRequest(strRequest).map { response =>
        expect.same(None, response.map(_.as[JsonRpcResponse[Json]]))
      }
    }
  }

  override def testInvalidRequest[I, E, O](name: String)(request: String, expectedResponse: JsonRpcResponse[Json]): Unit = {
    test(name) {
      val interpreter = createInterpreter(List(hello_in_int_out_string.serverLogic[IO](int => IO.pure(Right(int.toString)))))
      interpreter.dispatchRequest(request).map { response =>
        expect.same(Some(Right(expectedResponse)), response.map(_.as[JsonRpcResponse[Json]]))
      }
    }
  }

  override def test[I, E, O, B: Encoder](endpoint: JsonRpcEndpoint[I, E, O], suffix: String)(
      f: I => IO[Either[JsonRpcError[E], O]]
  )(request: B, expectedResponse: JsonRpcResponse[Json]): Unit = {
    test(endpoint.showDetail + " " + suffix) {
      val interpreter = createInterpreter(List(endpoint.serverLogic(f)))
      val strRequest = Encoder[B].apply(request).noSpaces
      interpreter.dispatchRequest(strRequest).map { response =>
        expect.same(Some(Right(expectedResponse)), response.map(_.as[JsonRpcResponse[Json]]))
      }
    }
  }

  override def testMultiple[B: Encoder](name: String)(
      se: List[JsonRpcServerEndpoint[IO]]
  )(request: List[B], expectedResponse: List[JsonRpcResponse[Json]]): Unit = {
    test(name) {
      val interpreter = createInterpreter(se)
      val strRequest = Json.arr(request.map(b => Encoder[B].apply(b)) *).noSpaces
      interpreter.dispatchRequest(strRequest).map { response =>
        expect.same(
          Some(Right(expectedResponse)),
          response.map(_.as[List[JsonRpcResponse[Json]]])
        )
      }
    }
  }

  private def createInterpreter(se: List[JsonRpcServerEndpoint[IO]]) = {
    toInterpreter(se).getOrElse(throw new RuntimeException("cannot create interpreter"))
  }

  override def toInterpreter(
      se: List[JsonRpcServerEndpoint[IO]]
  ): Either[ServerInterpreter.InterpretationError, ServerInterpreter[IO, Json]] = {
    ServerInterpreter(se, new CirceJsonSupport, CustomInterceptors().interceptors)(new CatsMonadError[IO])
  }
}
