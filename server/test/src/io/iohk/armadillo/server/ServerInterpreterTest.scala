package io.iohk.armadillo.server

import cats.effect.IO
import io.circe.{Encoder, Json}
import io.iohk.armadillo._
import io.iohk.armadillo.server.ServerInterpreter.ServerResponse
import org.json4s.{Extraction, JValue}
import sttp.tapir.integ.cats.CatsMonadError

object CirceServerInterpreterTest
    extends ServerInterpreterTest[Json]
    with AbstractCirceSuite[String, ServerInterpreter[IO, Json]]
    with CirceEndpoints {

  override def encode[B: Encoder](b: B): Json = Encoder[B].apply(b)

  override def circeJsonToRaw(c: Json): Json = c

  override def rawEnc: Encoder[Json] = implicitly

}

object Json4sServerInterpreterTest
    extends ServerInterpreterTest[JValue]
    with AbstractJson4sSuite[String, ServerInterpreter[IO, JValue]]
    with Json4sEndpoints {

  override def encode[B: Enc](b: B): JValue = Extraction.decompose(b)

  override def circeJsonToRaw(c: Json): JValue = org.json4s.jackson.parseJson(c.noSpaces)

  override def rawEnc: Enc[JValue] = ()

  override implicit def jsonRpcRequestEncoder: Unit = ()
}

trait ServerInterpreterTest[Raw]
    extends AbstractServerSuite[Raw, String, ServerInterpreter[IO, Raw]]
    with AbstractBaseSuite[Raw, String, ServerInterpreter[IO, Raw]]
    with Endpoints {

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

  def encode[B: Enc](b: B): Raw

  override def testNotification[I, E, O](endpoint: JsonRpcEndpoint[I, E, O], suffix: String)(
      f: I => IO[Either[E, O]]
  )(request: JsonRpcRequest[Raw]): Unit = {
    test(endpoint.showDetail + " as notification " + suffix) {
      val interpreter = createInterpreter(List(endpoint.serverLogic(f)))
      val strRequest = jsonSupport.stringify(encode(request))
      interpreter.dispatchRequest(strRequest).map { response =>
        expect.same(Option.empty, response)
      }
    }
  }

  override def testInvalidRequest[I, E, O](name: String)(request: String, expectedResponse: JsonRpcResponse[Raw]): Unit = {
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

  override def test[I, E, O, B: Enc](endpoint: JsonRpcEndpoint[I, E, O], suffix: String)(
      f: I => IO[Either[E, O]]
  )(request: B, expectedResponse: JsonRpcResponse[Raw]): Unit = {
    test(endpoint.showDetail + " " + suffix) {
      val interpreter = createInterpreter(List(endpoint.serverLogic(f)))
      val strRequest = jsonSupport.stringify(encode(request))
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
  )(request: JsonRpcRequest[Raw], expectedResponse: JsonRpcResponse[Raw]): Unit = {
    test(endpoint.showDetail + " " + suffix) {
      val interpreter = createInterpreter(List(endpoint.serverLogic(f)))
      val strRequest = jsonSupport.stringify(encode(request))
      interpreter.dispatchRequest(strRequest).map { response =>
        val expectedServerResponse = ServerResponse.ServerFailure(jsonSupport.encodeResponse(expectedResponse))
        expect.same(Some(expectedServerResponse), response)
      }
    }
  }

  override def testMultiple[B: Enc](name: String)(
      se: List[JsonRpcServerEndpoint[IO]]
  )(request: List[B], expectedResponses: List[JsonRpcResponse[Raw]]): Unit = {
    test(name) {
      val interpreter = createInterpreter(se)
      val strRequest = jsonSupport.stringify(jsonSupport.asArray(request.map(encode[B])))
      interpreter.dispatchRequest(strRequest).map { response =>
        val expectedServerInterpreterResponse = if (expectedResponses.isEmpty) {
          Option.empty
        } else {
          val json = jsonSupport.asArray(expectedResponses.map(jsonSupport.encodeResponse))
          Some(ServerResponse.Success(json))
        }
        expect.same(expectedServerInterpreterResponse, response)
      }
    }
  }

}
