package io.iohk.armadillo.server

import cats.effect.IO
import io.circe.{Encoder, Json}
import io.iohk.armadillo._
import io.iohk.armadillo.server.Endpoints.hello_in_int_out_string
import io.iohk.armadillo.server.ServerInterpreter.ServerResponse
import sttp.tapir.integ.cats.CatsMonadError

trait AbstractServerInterpreterTest[Raw]
    extends AbstractServerSuite[Raw, String, ServerInterpreter[IO, Raw]]
    with AbstractBaseSuite[Raw, String, ServerInterpreter[IO, Raw]] {

  override def invalidJson: String = """{"jsonrpc": "2.0", "method": "foobar, "params": "bar", "baz]"""

  override def jsonNotAnObject: String = """["asd"]"""

  protected def createInterpreter(se: List[JsonRpcServerEndpoint[IO]]): ServerInterpreter[IO, Raw] = {
    toInterpreter(se).getOrElse(throw new RuntimeException("cannot create interpreter"))
  }
  override def toInterpreter(
      se: List[JsonRpcServerEndpoint[IO]]
  ): Either[ServerInterpreter.InterpretationError, ServerInterpreter[IO, Raw]] = {
    ServerInterpreter(se, jsonSupport, CustomInterceptors().interceptors)(new CatsMonadError[IO])
  }
}

object ServerInterpreterTest extends AbstractServerInterpreterTest[Json] with AbstractCirceSuite[String, ServerInterpreter[IO, Json]] {
  override def circeJsonToRaw(c: Json): Json = c
  override def rawEnc: Encoder[Json] = implicitly

  override def testNotification[I, E, O](endpoint: JsonRpcEndpoint[I, E, O], suffix: String)(
      f: I => IO[Either[E, O]]
  )(request: JsonRpcRequest[Json]): Unit = {
    test(endpoint.showDetail + " as notification " + suffix) {
      val interpreter = createInterpreter(List(endpoint.serverLogic(f)))
      val strRequest = Encoder[JsonRpcRequest[Json]].apply(request).noSpaces
      interpreter.dispatchRequest(strRequest).map { response =>
        expect.same(Option.empty, response)
      }
    }
  }

  override def testInvalidRequest[I, E, O](name: String)(request: String, expectedResponse: JsonRpcResponse[Json]): Unit = {
    test(name) {
      val interpreter = createInterpreter(List(hello_in_int_out_string.serverLogic[IO](int => IO.pure(Right(int.toString)))))
      interpreter.dispatchRequest(request).map { response =>
        val expectedServerResponse = expectedResponse match {
          case success @ JsonRpcSuccessResponse(_, _, _) => ServerResponse.Success(jsonSupport.encodeResponse(success))
          case error @ JsonRpcErrorResponse(_, _, _)     => ServerResponse.Failure(jsonSupport.encodeResponse(error))
        }
        expect.same(Some(expectedServerResponse), response)
      }
    }
  }

  override def test[I, E, O, B: Encoder](endpoint: JsonRpcEndpoint[I, E, O], suffix: String)(
      f: I => IO[Either[E, O]]
  )(request: B, expectedResponse: JsonRpcResponse[Json]): Unit = {
    test(endpoint.showDetail + " " + suffix) {
      val interpreter = createInterpreter(List(endpoint.serverLogic(f)))
      val strRequest = Encoder[B].apply(request).noSpaces
      interpreter.dispatchRequest(strRequest).map { response =>
        val expectedServerResponse = expectedResponse match {
          case success @ JsonRpcSuccessResponse(_, _, _) => ServerResponse.Success(jsonSupport.encodeResponse(success))
          case error @ JsonRpcErrorResponse(_, _, _)     => ServerResponse.Failure(jsonSupport.encodeResponse(error))
        }
        expect.same(Some(expectedServerResponse), response)
      }
    }
  }

  override def testServerError[I, E, O](endpoint: JsonRpcEndpoint[I, E, O], suffix: String)(
      f: I => IO[Either[E, O]]
  )(request: JsonRpcRequest[Json], expectedResponse: JsonRpcResponse[Json]): Unit = {
    test(endpoint.showDetail + " " + suffix) {
      val interpreter = createInterpreter(List(endpoint.serverLogic(f)))
      val strRequest = Encoder[JsonRpcRequest[Json]].apply(request).noSpaces
      interpreter.dispatchRequest(strRequest).map { response =>
        val expectedServerResponse = ServerResponse.ServerFailure(jsonSupport.encodeResponse(expectedResponse))
        expect.same(Some(expectedServerResponse), response)
      }
    }
  }

  override def testMultiple[B: Encoder](name: String)(
      se: List[JsonRpcServerEndpoint[IO]]
  )(request: List[B], expectedResponses: List[JsonRpcResponse[Json]]): Unit = {
    test(name) {
      val interpreter = createInterpreter(se)
      val strRequest = Json.arr(request.map(b => Encoder[B].apply(b)): _*).noSpaces
      interpreter.dispatchRequest(strRequest).map { response =>
        val expectedServerInterpreterResponse = if (expectedResponses.isEmpty) {
          Option.empty
        } else {
          val json = Json.fromValues(expectedResponses.map(jsonSupport.encodeResponse))
          Some(ServerResponse.Success(json))
        }
        expect.same(expectedServerInterpreterResponse, response)
      }
    }
  }

}
