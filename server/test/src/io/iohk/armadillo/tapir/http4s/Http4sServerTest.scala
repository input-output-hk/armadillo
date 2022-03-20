package io.iohk.armadillo.tapir.http4s

import cats.effect.IO
import io.circe.Json
import io.circe.literal.*
import io.iohk.armadillo.Armadillo.*
import io.iohk.armadillo.tapir.http4s.Endpoints.*

import java.lang.Integer.parseInt

object Http4sServerTest extends BaseSuite {
  // TODO add test for non-unique methods

  test(hello_in_int_out_string)(int => IO.pure(Right(int.toString)))(
    request = JsonRpcRequest.v2("hello", json"[42]", 1),
    expectedResponse = JsonRpcResponse.v2(json"${"42"}", 1)
  )

  testNotification(hello_in_int_out_string)(int => IO.pure(Right(int.toString)))(
    request = Notification.v2("hello", json"[42]")
  )

  test(hello_in_int_out_string, "by_name")(int => IO.pure(Right(int.toString)))(
    request = JsonRpcRequest.v2("hello", json"""{"param1": 42}""", 1),
    expectedResponse = JsonRpcResponse.v2(json"${"42"}", 1)
  )

  test(hello_in_multiple_int_out_string) { case (int1, int2) => IO.pure(Right(s"${int1 + int2}")) }(
    request = JsonRpcRequest.v2("hello", json"[42, 43]", 1),
    expectedResponse = JsonRpcResponse.v2(json"${"85"}", 1)
  )

  test(hello_in_multiple_int_out_string, "by_name") { case (int1, int2) => IO.pure(Right(s"${int1 + int2}")) }(
    request = JsonRpcRequest.v2("hello", json"""{"param1": 42, "param2": 43}""", 1),
    expectedResponse = JsonRpcResponse.v2(json"${"85"}", 1)
  )

  test(empty)(_ => IO.delay(Right(println("hello from server"))))(
    request = JsonRpcRequest.v2("empty", json"""[]""", 1),
    expectedResponse = JsonRpcResponse.v2(Json.Null, 1)
  )

  test(empty, "method not found")(_ => IO.delay(Right(println("hello from server"))))(
    request = JsonRpcRequest.v2("non_existing_method", json"""[]""", 1),
    expectedResponse = JsonRpcResponse.error_v2(json"""{"code": -32601, "message": "Method not found"}""", Some(1))
  )

  test(empty, "no_data_error")(_ => IO.pure(Left(JsonRpcError.noData(123, "error"))))(
    request = JsonRpcRequest.v2("empty", json"[]", 1),
    expectedResponse = JsonRpcResponse.error_v2(json"""{"code": 123, "message": "error"}""", Some(1))
  )

  test(error_with_data)(_ => IO.pure(Left(JsonRpcError(123, "error", 42))))(
    request = JsonRpcRequest.v2("error_with_data", json"[]", 1),
    expectedResponse = JsonRpcResponse.error_v2(json"""{"code": 123, "message": "error", "data": 42}""", Some(1))
  )

  test(empty, "internal server error")(_ => IO.raiseError(new RuntimeException("something went wrong")))(
    request = JsonRpcRequest.v2("empty", json"[]", 1),
    expectedResponse = JsonRpcResponse.error_v2(json"""{"code": -32603, "message": "Internal error"}""", Some(1))
  )

  testMultiple("batch_request_successful")(
    List(
      hello_in_int_out_string.serverLogic[IO](int => IO.pure(Right(int.toString))),
      e1_int_string_out_int.serverLogic[IO](str => IO.delay(Right(parseInt(str))))
    )
  )(
    request = List(
      JsonRpcRequest.v2("hello", json"[11]", "1"),
      JsonRpcRequest.v2("e1", json"""{"param1": "22"}""", 2)
    ),
    expectedResponse = List(
      JsonRpcResponse.v2(Json.fromString("11"), 1),
      JsonRpcResponse.v2(Json.fromInt(22), 2)
    )
  )

  testMultiple("batch_request_mixed: success & error & notification")(
    List(
      hello_in_int_out_string.serverLogic[IO](int => IO.pure(Right(int.toString))),
      e1_int_string_out_int.serverLogic[IO](str => IO.delay(Right(parseInt(str)))),
      error_with_data.serverLogic[IO](_ => IO.pure(Left(JsonRpcError(123, "error", 123))))
    )
  )(
    request = List(
      JsonRpcRequest.v2("hello", json"[11]", "1"),
      Notification.v2("e1", json"""{"param1": "22"}"""),
      JsonRpcRequest.v2("error_with_data", json"[11]", "3")
    ),
    expectedResponse = List(
      JsonRpcResponse.v2(Json.fromString("11"), 1),
      JsonRpcResponse.error_v2(json"""{"code": 123, "message": "error", "data": 123}""", Some(3))
    )
  )
}
