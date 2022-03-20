package io.iohk.armadillo.tapir.http4s

import cats.effect.IO
import io.circe.Json
import io.circe.literal.*
import io.iohk.armadillo.Armadillo.*
import io.iohk.armadillo.tapir.http4s.Endpoints.*

import java.lang.Integer.parseInt

object Http4sServerTest extends BaseSuite {
  // TODO add test for non-unique methods
  // TODO add test for non-unique id within batch request

  test(hello_in_int_out_string)(int => IO.pure(Right(int.toString)))(
    request = JsonRpcRequest[Json]("2.0", "hello", json"[42]", 1),
    expectedResponse = JsonRpcSuccessResponse("2.0", json"${"42"}", 1)
  )

  test(hello_in_int_out_string, "by_name")(int => IO.pure(Right(int.toString)))(
    request = JsonRpcRequest[Json]("2.0", "hello", json"""{"param1": 42}""", 1),
    expectedResponse = JsonRpcSuccessResponse("2.0", json"${"42"}", 1)
  )

  test(hello_in_multiple_int_out_string) { case (int1, int2) => IO.pure(Right(s"${int1 + int2}")) }(
    request = JsonRpcRequest[Json]("2.0", "hello", json"[42, 43]", 1),
    expectedResponse = JsonRpcSuccessResponse("2.0", json"${"85"}", 1)
  )

  test(hello_in_multiple_int_out_string, "by_name") { case (int1, int2) => IO.pure(Right(s"${int1 + int2}")) }(
    request = JsonRpcRequest[Json]("2.0", "hello", json"""{"param1": 42, "param2": 43}""", 1),
    expectedResponse = JsonRpcSuccessResponse("2.0", json"${"85"}", 1)
  )

  test(empty)(_ => IO.delay(Right(println("hello from server"))))(
    request = JsonRpcRequest[Json]("2.0", "empty", json"""[]""", 1),
    expectedResponse = JsonRpcSuccessResponse("2.0", Json.Null, 1)
  )

  test(empty, "method not found")(_ => IO.delay(Right(println("hello from server"))))(
    request = JsonRpcRequest[Json]("2.0", "non_existing_method", json"""[]""", 1),
    expectedResponse = JsonRpcErrorResponse("2.0", json"""{"code": -32601, "message": "Method not found"}""", 1)
  )

  test(empty, "no_data_error")(_ => IO.pure(Left(JsonRpcErrorNoData(123, "error"))))(
    request = JsonRpcRequest[Json]("2.0", "empty", json"[]", 1),
    expectedResponse = JsonRpcErrorResponse("2.0", json"""{"code": 123, "message": "error"}""", 1)
  )

  test(error_with_data)(_ => IO.pure(Left(JsonRpcErrorWithData(123, "error", 42))))(
    request = JsonRpcRequest[Json]("2.0", "error_with_data", json"[]", 1),
    expectedResponse = JsonRpcErrorResponse("2.0", json"""{"code": 123, "message": "error", "data": 42}""", 1)
  )

  test(empty, "internal server error")(_ => IO.raiseError(new RuntimeException("something went wrong")))(
    request = JsonRpcRequest[Json]("2.0", "empty", json"[]", 1),
    expectedResponse = JsonRpcErrorResponse("2.0", json"""{"code": -32603, "message": "Internal error"}""", 1)
  )

  testMultiple("batch_request_successful")(
    List(
      hello_in_int_out_string.serverLogic[IO](int => IO.pure(Right(int.toString))),
      e1_int_string_out_int.serverLogic[IO](str => IO.delay(Right(parseInt(str))))
    )
  )(
    request = List(JsonRpcRequest("2.0", "hello", json"[11]", 1), JsonRpcRequest("2.0", "e1", json"""{"param1": "22"}""", 1)),
    expectedResponse = List(JsonRpcSuccessResponse("2.0", Json.fromString("11"), 1), JsonRpcSuccessResponse("2.0", Json.fromInt(22), 1))
  )
}
