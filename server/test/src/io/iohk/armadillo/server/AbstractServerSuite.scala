package io.iohk.armadillo.server

import cats.effect.IO
import io.circe.Json
import io.circe.literal._
import io.iohk.armadillo.server.Endpoints._
import io.iohk.armadillo.server.ServerInterpreter.InterpretationError
import io.iohk.armadillo.{JsonRpcError, JsonRpcRequest, JsonRpcResponse, Notification}

import java.lang.Integer.parseInt

trait AbstractServerSuite[Body, Interpreter] extends AbstractBaseSuite[Body, Interpreter] {
  test(hello_in_int_out_string)(int => IO.pure(Right(int.toString)))(
    request = JsonRpcRequest.v2("hello", json"[42]", 1),
    expectedResponse = JsonRpcResponse.v2(json"${"42"}", 1)
  )

  test(hello_in_int_out_string, "invalid params")(int => IO.pure(Right(int.toString)))(
    request = JsonRpcRequest.v2("hello", json"[true]", 1),
    expectedResponse = JsonRpcResponse.error_v2(json"""{"code": -32602, "message": "Invalid params"}""", Some(1))
  )

  test(hello_in_int_out_string, "too many params")(int => IO.pure(Right(int.toString)))(
    request = JsonRpcRequest.v2("hello", json"[42, 43]", 1),
    expectedResponse = JsonRpcResponse.error_v2(json"""{"code": -32602, "message": "Invalid params"}""", Some(1))
  )

  test(hello_in_int_out_string_by_name, "expected params by name")(int => IO.pure(Right(int.toString)))(
    request = JsonRpcRequest.v2("hello", json"[42]", 1),
    expectedResponse = JsonRpcResponse.error_v2(json"""{"code": -32602, "message": "Invalid params"}""", Some(1))
  )

  test(hello_in_int_out_string_by_position, "expected params by pos")(int => IO.pure(Right(int.toString)))(
    request = JsonRpcRequest.v2("hello", json"""{"param1": 42}""", 1),
    expectedResponse = JsonRpcResponse.error_v2(json"""{"code": -32602, "message": "Invalid params"}""", Some(1))
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
      JsonRpcRequest.v2("error_with_data", json"[]", "3")
    ),
    expectedResponse = List(
      JsonRpcResponse.v2(Json.fromString("11"), 1),
      JsonRpcResponse.error_v2(json"""{"code": 123, "message": "error", "data": 123}""", Some(3))
    )
  )

  testMultiple("batch_request internal server error")(
    List(
      hello_in_int_out_string.serverLogic[IO](_ => IO.raiseError(new RuntimeException("something went wrong"))),
      e1_int_string_out_int.serverLogic[IO](_ => IO.raiseError(new RuntimeException("something went wrong"))),
      error_with_data.serverLogic[IO](_ => IO.raiseError(new RuntimeException("something went wrong")))
    )
  )(
    request = List(
      JsonRpcRequest.v2("hello", json"[11]", "1"),
      Notification.v2("e1", json"""{"param1": "22"}"""),
      JsonRpcRequest.v2("error_with_data", json"[]", "3")
    ),
    expectedResponse = List(
      JsonRpcResponse.error_v2(json"""{"code": -32603, "message": "Internal error"}""", Some(1)),
      JsonRpcResponse.error_v2(json"""{"code": -32603, "message": "Internal error"}""", Some(3))
    )
  )

  testMultiple("batch_request method_not_found")(List.empty)(
    request = List(
      JsonRpcRequest.v2("non_existing_method_1", json"[11]", "1"),
      Notification.v2("non_existing_method_2", json"""{"param1": "22"}"""),
      JsonRpcRequest.v2("non_existing_method_3", json"[11]", "3")
    ),
    expectedResponse = List(
      JsonRpcResponse.error_v2(json"""{"code": -32601, "message": "Method not found"}""", Some(1)),
      JsonRpcResponse.error_v2(json"""{"code": -32601, "message": "Method not found"}""", Some(3))
    )
  )

  testMultiple("batch_request invalid request")(List.empty)(
    request = List(
      json"""{"jsonrpc": "2.0", "method": 1, "params": "bar"}""",
      json"""{"jsonrpc": "2.0", "method": 1, "params": "bar"}"""
    ),
    expectedResponse = List(
      JsonRpcResponse.error_v2(json"""{"code": -32600, "message": "Invalid Request"}"""),
      JsonRpcResponse.error_v2(json"""{"code": -32600, "message": "Invalid Request"}""")
    )
  )

  testMultiple("batch_request only notifications")(
    List(
      hello_in_int_out_string.serverLogic[IO](int => IO.pure(Right(int.toString))),
      e1_int_string_out_int.serverLogic[IO](str => IO.delay(Right(parseInt(str))))
    )
  )(
    request = List(
      Notification.v2("hello", json"[11]"),
      Notification.v2("e1", json"""{"param1": "22"}""")
    ),
    expectedResponse = List.empty
  )

  test("should return error when trying to pass non-unique methods to tapir interpreter") {
    val se = hello_in_int_out_string.serverLogic[IO](int => IO.pure(Right(int.toString)))
    val result = toInterpreter(List(se, se))
    IO.delay(expect.same(result, Left(InterpretationError.NonUniqueMethod(List(hello_in_int_out_string.methodName)))))
  }

  test(hello_in_int_out_string, "invalid request")(int => IO.pure(Right(int.toString)))(
    request = json"""{"jsonrpc": "2.0", "method": 1, "params": "bar"}""",
    expectedResponse = JsonRpcResponse.error_v2(json"""{"code": -32600, "message": "Invalid Request"}""")
  )

  test(hello_in_int_out_string, "invalid request structure")(int => IO.pure(Right(int.toString)))(
    request = json"""123""",
    expectedResponse = JsonRpcResponse.error_v2(json"""{"code": -32600, "message": "Invalid Request"}""")
  )

  testInvalidRequest("parse error - invalid json")(
    request = invalidJson,
    expectedResponse = JsonRpcResponse.error_v2(json"""{"code": -32700, "message": "Parse error"}""")
  )

  testInvalidRequest("parse error - root is not an object")(
    request = jsonNotAnObject,
    expectedResponse = JsonRpcResponse.error_v2(json"""{"code": -32700, "message": "Parse error"}""")
  )

  test(optional_input, "all inputs provided using positional style") { case (s, i) =>
    IO.pure(Right(s"$i${s.getOrElse("")}"))
  }(
    request = JsonRpcRequest.v2("optional_input", json"""["alice", 1]""", 1),
    expectedResponse = JsonRpcResponse.v2(Json.fromString("1alice"), 1)
  )

  test(optional_input, "all inputs provided using by-name style") { case (s, i) =>
    IO.pure(Right(s"$i${s.getOrElse("")}"))
  }(
    request = JsonRpcRequest.v2("optional_input", json"""{"p2": 1, "p1": "alice"}""", 1),
    expectedResponse = JsonRpcResponse.v2(Json.fromString("1alice"), 1)
  )

  test(optional_input, "optional input omitted when using positional style") { case (s, i) =>
    IO.pure(Right(s"$i${s.getOrElse("")}"))
  }(request = JsonRpcRequest.v2("optional_input", json"""[1]""", 1), expectedResponse = JsonRpcResponse.v2(Json.fromString("1"), 1))

  test(optional_input, "optional input omitted when using by-name style") { case (s, i) =>
    IO.pure(Right(s"$i${s.getOrElse("")}"))
  }(
    request = JsonRpcRequest.v2("optional_input", json"""{"p2": 1}""", 1),
    expectedResponse = JsonRpcResponse.v2(Json.fromString("1"), 1)
  )

  test(optional_input, "should fail when mandatory input omitted when using by-name style") { case (s, i) =>
    IO.pure(Right(s"$i${s.getOrElse("")}"))
  }(
    request = JsonRpcRequest.v2("optional_input", json"""{"p1": "alice"}""", 1),
    expectedResponse = JsonRpcResponse.error_v2(json"""{"code": -32602, "message": "Invalid params"}""", Some(1))
  )

  test(optional_input, "should fail when mandatory input omitted when using by-pos style") { case (s, i) =>
    IO.pure(Right(s"$i${s.getOrElse("")}"))
  }(
    request = JsonRpcRequest.v2("optional_input", json"""["alice"]""", 1),
    expectedResponse = JsonRpcResponse.error_v2(json"""{"code": -32602, "message": "Invalid params"}""", Some(1))
  )

  test(optional_input, "should fail when given more parameters than expected - positional style") { case (s, i) =>
    IO.pure(Right(s"$i${s.getOrElse("")}"))
  }(
    request = JsonRpcRequest.v2("optional_input", json"""["alice", 1, 2]""", 1),
    expectedResponse = JsonRpcResponse.error_v2(json"""{"code": -32602, "message": "Invalid params"}""", Some(1))
  )

  test(optional_input, "should fail when given more parameters than expected - by-name style") { case (s, i) =>
    IO.pure(Right(s"$i${s.getOrElse("")}"))
  }(
    request = JsonRpcRequest.v2("optional_input", json"""{"p1": "alice", "p2": 1, "p3": 2}""", 1),
    expectedResponse = JsonRpcResponse.error_v2(json"""{"code": -32602, "message": "Invalid params"}""", Some(1))
  )

  test(optional_output, "should return optional response")(_ => IO.pure(Right(Option.empty[String])))(
    request = JsonRpcRequest.v2("optional_output", json"""{}""", 1),
    expectedResponse = JsonRpcResponse.v2(Json.Null, 1)
  )

  test(output_without_params, "should return response when no params attribute is missing")(_ => IO.pure(Right("params is not required")))(
    request = JsonRpcRequest.v2[Json]("output_without_params", 1),
    expectedResponse = JsonRpcResponse.v2(Json.fromString("params is not required"), 1)
  )
}
