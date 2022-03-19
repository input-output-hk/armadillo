package io.iohk.armadillo.tapir.http4s

import cats.effect.IO
import io.circe.Json
import io.circe.literal.*
import io.iohk.armadillo.Armadillo.{JsonRpcNoDataError, JsonRpcError, JsonRpcErrorResponse, JsonRpcRequest, JsonRpcSuccessResponse}
import io.iohk.armadillo.tapir.http4s.Endpoints.*

object Http4sServerTest extends BaseSuite {

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

  test(no_data_error)(_ => IO.pure(Left(JsonRpcNoDataError(123, "error"))))(
    request = JsonRpcRequest[Json]("2.0", "no_data_error", json"[]", 1),
    expectedResponse = JsonRpcErrorResponse("2.0", json"""[{"code": 123, "message": "error"}]""", 1)
  )

  test(single_error)(_ => IO.pure(Left(JsonRpcError(123, "error", 42))))(
    request = JsonRpcRequest[Json]("2.0", "single_error", json"[]", 1),
    expectedResponse = JsonRpcErrorResponse("2.0", json"""[{"code": 123, "message": "error", "data": 42}]""", 1)
  )

  // TODO empty input should not swallow data
  test(multiple_errors)(_ => IO.pure(Left((JsonRpcError(123, "error1", 42), JsonRpcError(124, "error2", "42")))))(
    request = JsonRpcRequest[Json]("2.0", "multiple_errors", json"[]", 1),
    expectedResponse = JsonRpcErrorResponse(
      "2.0",
      json"""[{"code": 123, "message": "error1", "data": 42}, {"code": 124, "message": "error2", "data": "42"}]""",
      1
    )
  )
}
