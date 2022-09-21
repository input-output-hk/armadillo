package io.iohk.armadillo.server

import cats.effect.IO
import io.circe.Json
import io.circe.literal._
import io.iohk.armadillo.server.Endpoints._
import io.iohk.armadillo.server.ServerInterpreter.InterpretationError
import io.iohk.armadillo.{JsonRpcError, JsonRpcRequest, JsonRpcResponse, Notification}

import java.lang.Integer.parseInt

trait AbstractServerSuite[Raw, Body, Interpreter] extends AbstractBaseSuite[Raw, Body, Interpreter] with Endpoints {
  implicit def circeJsonToRaw(c: Json): Raw
  implicit def jsonRpcRequestEncoder: Enc[JsonRpcRequest[Raw]]
  implicit def rawEnc: Enc[Raw]

  test(hello_in_int_out_string)(int => IO.pure(Right(int.toString)))(
    request = JsonRpcRequest.v2[Raw]("hello", json"[42]", 1),
    expectedResponse = JsonRpcResponse.v2[Raw](json"${"42"}", 1)
  )

  test(hello_in_int_out_string, "invalid params")(int => IO.pure(Right(int.toString)))(
    request = JsonRpcRequest.v2[Raw]("hello", json"[true]", 1),
    expectedResponse = JsonRpcResponse.error_v2[Raw](json"""{"code": -32602, "message": "Invalid params"}""", 1)
  )

  test(hello_in_int_out_string, "too many params")(int => IO.pure(Right(int.toString)))(
    request = JsonRpcRequest.v2[Raw]("hello", json"[42, 43]", 1),
    expectedResponse = JsonRpcResponse.error_v2[Raw](json"""{"code": -32602, "message": "Invalid params"}""", 1)
  )

  test(hello_in_int_out_string_by_name, "expected params by name")(int => IO.pure(Right(int.toString)))(
    request = JsonRpcRequest.v2[Raw]("hello", json"[42]", 1),
    expectedResponse = JsonRpcResponse.error_v2[Raw](json"""{"code": -32602, "message": "Invalid params"}""", 1)
  )

  test(hello_in_int_out_string_by_position, "expected params by pos")(int => IO.pure(Right(int.toString)))(
    request = JsonRpcRequest.v2[Raw]("hello", json"""{"param1": 42}""", 1),
    expectedResponse = JsonRpcResponse.error_v2[Raw](json"""{"code": -32602, "message": "Invalid params"}""", 1)
  )

  test(hello_in_int_out_string_validated, "param validation passed")(int => IO.pure(Right(int.toString)))(
    request = JsonRpcRequest.v2[Raw]("hello", json"""{"param1": 42}""", 1),
    expectedResponse = JsonRpcResponse.v2[Raw](json"""${"42"}""", 1)
  )

  test(hello_in_int_out_string_validated, "param by name validation failed")(int => IO.pure(Right(int.toString)))(
    request = JsonRpcRequest.v2[Raw]("hello", json"""{"param1": -42}""", 1),
    expectedResponse = JsonRpcResponse.error_v2[Raw](json"""{"code": -32602, "message": "Invalid params"}""", 1)
  )

  test(hello_in_int_out_string_validated, "param by position validation failed")(int => IO.pure(Right(int.toString)))(
    request = JsonRpcRequest.v2[Raw]("hello", json"""[ -42 ]""", 1),
    expectedResponse = JsonRpcResponse.error_v2[Raw](json"""{"code": -32602, "message": "Invalid params"}""", 1)
  )

  testNotification(hello_in_int_out_string)(int => IO.pure(Right(int.toString)))(
    request = Notification.v2[Raw]("hello", json"[42]")
  )

  test(hello_in_int_out_string, "by_name")(int => IO.pure(Right(int.toString)))(
    request = JsonRpcRequest.v2[Raw]("hello", json"""{"param1": 42}""", 1),
    expectedResponse = JsonRpcResponse.v2[Raw](json"${"42"}", 1)
  )

  test(hello_in_multiple_int_out_string) { case (int1, int2) => IO.pure(Right(s"${int1 + int2}")) }(
    request = JsonRpcRequest.v2[Raw]("hello", json"[42, 43]", 1),
    expectedResponse = JsonRpcResponse.v2[Raw](json"${"85"}", 1)
  )

  test(hello_in_multiple_int_out_string, "by_name") { case (int1, int2) => IO.pure(Right(s"${int1 + int2}")) }(
    request = JsonRpcRequest.v2[Raw]("hello", json"""{"param1": 42, "param2": 43}""", 1),
    expectedResponse = JsonRpcResponse.v2[Raw](json"${"85"}", 1)
  )

  test(hello_in_multiple_validated, "validation passed") { case (int1, int2) => IO.pure(Right(s"${int1 + int2}")) }(
    request = JsonRpcRequest.v2[Raw]("hello", json"""{"param1": -9, "param2": 12}""", 1),
    expectedResponse = JsonRpcResponse.v2[Raw](json"${"3"}", 1)
  )

  test(hello_in_multiple_validated, "first param validation failed") { case (int1, int2) => IO.pure(Right(s"${int1 + int2}")) }(
    request = JsonRpcRequest.v2[Raw]("hello", json"""{"param1": 42, "param2": 12}""", 1),
    expectedResponse = JsonRpcResponse.error_v2[Raw](json"""{"code": -32602, "message": "Invalid params"}""", 1)
  )

  test(hello_in_multiple_validated, "second param validation failed") { case (int1, int2) => IO.pure(Right(s"${int1 + int2}")) }(
    request = JsonRpcRequest.v2[Raw]("hello", json"""{"param1": -9, "param2": 100}""", 1),
    expectedResponse = JsonRpcResponse.error_v2[Raw](json"""{"code": -32602, "message": "Invalid params"}""", 1)
  )

  test(hello_with_validated_product, "product validation passed") { case (a, b) => IO.pure(Right(s"$a-$b")) }(
    request = JsonRpcRequest.v2[Raw]("hello", json"""[ [1, "Bob"] ]""", 1),
    expectedResponse = JsonRpcResponse.v2[Raw](json""""1-Bob"""", 1)
  )

  test(hello_with_validated_product, "product validation failed") { case (a, b) => IO.pure(Right(s"$a-$b")) }(
    request = JsonRpcRequest.v2[Raw]("hello", json"""[ [101, "Bob"] ]""", 1),
    expectedResponse = JsonRpcResponse.error_v2[Raw](json"""{"code": -32602, "message": "Invalid params"}""", 1)
  )

  test(hello_with_validated_coproduct, "coproduct validation passed") { e => IO.pure(Right(s"Hello ${e.id}")) }(
    request = JsonRpcRequest.v2[Raw]("hello", json"""[ {"Person": {"name": "Bob", "id": 1}} ]""", 1),
    expectedResponse = JsonRpcResponse.v2[Raw](json""""Hello 1"""", 1)
  )

  test(hello_with_validated_coproduct, "coproduct validation failed") { e => IO.pure(Right(s"Hello ${e.id}")) }(
    request = JsonRpcRequest.v2[Raw]("hello", json"""[ {"Person": {"name": "Bob", "id": 100}} ]""", 1),
    expectedResponse = JsonRpcResponse.error_v2[Raw](json"""{"code": -32602, "message": "Invalid params"}""", 1)
  )

  test(hello_with_validated_branch_of_coproduct, "validation fail when passed as vector") { e => IO.pure(Right(s"Hello ${e.id}")) }(
    request = JsonRpcRequest.v2[Raw]("hello", json"""[ {"Person": {"name": "", "id": 100}} ]""", 1),
    expectedResponse = JsonRpcResponse.error_v2[Raw](json"""{"code": -32602, "message": "Invalid params"}""", 1)
  )

  test(hello_with_validated_branch_of_coproduct, "validation fail when passed as object") { e => IO.pure(Right(s"Hello ${e.id}")) }(
    request = JsonRpcRequest.v2[Raw]("hello", json"""{"param1":{"Person": {"name": "", "id": 100}}}""", 1),
    expectedResponse = JsonRpcResponse.error_v2[Raw](json"""{"code": -32602, "message": "Invalid params"}""", 1)
  )

  test(empty)(_ => IO.delay(Right(println("hello from server"))))(
    request = JsonRpcRequest.v2[Raw]("empty", json"""[]""", 1),
    expectedResponse = JsonRpcResponse.v2[Raw](Json.Null, 1)
  )

  test(empty, "method not found")(_ => IO.delay(Right(println("hello from server"))))(
    request = JsonRpcRequest.v2[Raw]("non_existing_method", json"""[]""", 1),
    expectedResponse = JsonRpcResponse.error_v2[Raw](json"""{"code": -32601, "message": "Method not found"}""", 1)
  )

  test(error_no_data, "no_data_error")(_ => IO.pure(Left(JsonRpcError.noData(123, "error"))))(
    request = JsonRpcRequest.v2[Raw]("error_no_data", json"[]", 1),
    expectedResponse = JsonRpcResponse.error_v2[Raw](json"""{"code": 123, "message": "error"}""", 1)
  )

  test(error_with_data)(_ => IO.pure(Left(JsonRpcError.withData(123, "error", 42))))(
    request = JsonRpcRequest.v2[Raw]("error_with_data", json"[]", 1),
    expectedResponse = JsonRpcResponse.error_v2[Raw](json"""{"code": 123, "message": "error", "data": 42}""", 1)
  )

  test(fixed_error, "fixed_error")(_ => IO.pure(Left(())))(
    request = JsonRpcRequest.v2[Raw]("fixed_error", json"[]", 1),
    expectedResponse = JsonRpcResponse.error_v2[Raw](json"""{"code": 200, "message": "something went wrong"}""", 1)
  )

  test(fixed_error_with_data, "fixed_error_with_data")(_ => IO.pure(Left("custom error message")))(
    request = JsonRpcRequest.v2[Raw]("fixed_error_with_data", json"[]", 1),
    expectedResponse =
      JsonRpcResponse.error_v2[Raw](json"""{"code": 200, "message": "something went wrong", "data": "custom error message"}""", 1)
  )

  test(oneOf_fixed_errors_with_data, "oneOf_fixed_errors - small")(_ => IO.pure(Left(ErrorInfoSmall("aaa"))))(
    request = JsonRpcRequest.v2[Raw]("fixed_error", json"[]", 1),
    expectedResponse = JsonRpcResponse.error_v2[Raw](json"""{"code": 201, "message": "something went really wrong", "data":{"msg":"aaa"}}""", 1)
  )

  test(oneOf_fixed_errors_with_data, "oneOf_fixed_errors - big")(_ => IO.pure(Left(ErrorInfoBig("aaa", 123))))(
    request = JsonRpcRequest.v2[Raw]("fixed_error", json"[]", 1),
    expectedResponse =
      JsonRpcResponse.error_v2[Raw](json"""{"code": 200, "message": "something went wrong", "data":{"msg":"aaa", "code": 123}}""", 1)
  )

  test(oneOf_fixed_errors_value_matcher, "oneOf_fixed_errors_value_matcher - left")(_ => IO.pure(Left(Left(()))))(
    request = JsonRpcRequest.v2[Raw]("fixed_error", json"[]", 1),
    expectedResponse = JsonRpcResponse.error_v2[Raw](json"""{"code": 201, "message": "something went really wrong"}""", 1)
  )

  test(oneOf_fixed_errors_value_matcher, "oneOf_fixed_errors_value_matcher - right")(_ => IO.pure(Left(Right(()))))(
    request = JsonRpcRequest.v2[Raw]("fixed_error", json"[]", 1),
    expectedResponse = JsonRpcResponse.error_v2[Raw](json"""{"code": 200, "message": "something went wrong"}""", 1)
  )

  testServerError(empty, "internal server error")(_ => IO.raiseError(new RuntimeException("something went wrong")))(
    request = JsonRpcRequest.v2[Raw]("empty", json"[]", 1),
    expectedResponse = JsonRpcResponse.error_v2[Raw](json"""{"code": -32603, "message": "Internal error"}""", 1)
  )

  testMultiple("batch_request_successful")(
    List(
      hello_in_int_out_string.serverLogic[IO](int => IO.pure(Right(int.toString))),
      e1_int_string_out_int.serverLogic[IO](str => IO.delay(Right(parseInt(str))))
    )
  )(
    request = List[JsonRpcRequest[Raw]](
      JsonRpcRequest.v2[Raw]("hello", json"[11]", "1"),
      JsonRpcRequest.v2[Raw]("e1", json"""{"param1": "22"}""", 2)
    ),
    expectedResponse = List(
      JsonRpcResponse.v2[Raw](Json.fromString("11"), 1),
      JsonRpcResponse.v2[Raw](Json.fromInt(22), 2)
    )
  )

  testMultiple("batch_request_mixed: success & error & notification")(
    List(
      hello_in_int_out_string.serverLogic[IO](int => IO.pure(Right(int.toString))),
      e1_int_string_out_int.serverLogic[IO](str => IO.delay(Right(parseInt(str)))),
      error_with_data.serverLogic[IO](_ => IO.pure(Left(JsonRpcError.withData(123, "error", 123))))
    )
  )(
    request = List[JsonRpcRequest[Raw]](
      JsonRpcRequest.v2[Raw]("hello", json"[11]", "1"),
      Notification.v2[Raw]("e1", json"""{"param1": "22"}"""),
      JsonRpcRequest.v2[Raw]("error_with_data", json"[]", "3")
    ),
    expectedResponse = List(
      JsonRpcResponse.v2[Raw](Json.fromString("11"), 1),
      JsonRpcResponse.error_v2[Raw](json"""{"code": 123, "message": "error", "data": 123}""", 3)
    )
  )

  testMultiple("batch_request internal server error")(
    List(
      hello_in_int_out_string.serverLogic[IO](_ => IO.raiseError(new RuntimeException("something went wrong"))),
      e1_int_string_out_int.serverLogic[IO](_ => IO.raiseError(new RuntimeException("something went wrong"))),
      error_with_data.serverLogic[IO](_ => IO.raiseError(new RuntimeException("something went wrong")))
    )
  )(
    request = List[JsonRpcRequest[Raw]](
      JsonRpcRequest.v2[Raw]("hello", json"[11]", "1"),
      Notification.v2[Raw]("e1", json"""{"param1": "22"}"""),
      JsonRpcRequest.v2[Raw]("error_with_data", json"[]", "3")
    ),
    expectedResponse = List(
      JsonRpcResponse.error_v2[Raw](json"""{"code": -32603, "message": "Internal error"}""", 1),
      JsonRpcResponse.error_v2[Raw](json"""{"code": -32603, "message": "Internal error"}""", 3)
    )
  )

  testMultiple("batch_request method_not_found")(List.empty)(
    request = List[JsonRpcRequest[Raw]](
      JsonRpcRequest.v2[Raw]("non_existing_method_1", json"[11]", "1"),
      Notification.v2[Raw]("non_existing_method_2", json"""{"param1": "22"}"""),
      JsonRpcRequest.v2[Raw]("non_existing_method_3", json"[11]", "3")
    ),
    expectedResponse = List(
      JsonRpcResponse.error_v2[Raw](json"""{"code": -32601, "message": "Method not found"}""", 1),
      JsonRpcResponse.error_v2[Raw](json"""{"code": -32601, "message": "Method not found"}""", 3)
    )
  )

  testMultiple("batch_request invalid request")(List.empty)(
    request = List[Raw](
      json"""{"jsonrpc": "2.0", "method": 1, "params": "bar"}""",
      json"""{"jsonrpc": "2.0", "method": 1, "params": "bar"}"""
    ),
    expectedResponse = List(
      JsonRpcResponse.error_v2[Raw](json"""{"code": -32600, "message": "Invalid Request"}"""),
      JsonRpcResponse.error_v2[Raw](json"""{"code": -32600, "message": "Invalid Request"}""")
    )
  )

  testMultiple("batch_request only notifications")(
    List(
      hello_in_int_out_string.serverLogic[IO](int => IO.pure(Right(int.toString))),
      e1_int_string_out_int.serverLogic[IO](str => IO.delay(Right(parseInt(str))))
    )
  )(
    request = List[JsonRpcRequest[Raw]](
      Notification.v2[Raw]("hello", json"[11]"),
      Notification.v2[Raw]("e1", json"""{"param1": "22"}""")
    ),
    expectedResponse = List.empty
  )

  test("should return error when trying to pass non-unique methods to tapir interpreter") {
    val se = hello_in_int_out_string.serverLogic[IO](int => IO.pure(Right(int.toString)))
    val result = toInterpreter(List(se, se))
    IO.delay(expect.same(result, Left(InterpretationError.NonUniqueMethod(List(hello_in_int_out_string.methodName)))))
  }

  test(hello_in_int_out_string, "invalid request")(int => IO.pure(Right(int.toString)))(
    request = json"""{"jsonrpc": "2.0", "method": 1, "params": "bar"}""": Raw,
    expectedResponse = JsonRpcResponse.error_v2[Raw](json"""{"code": -32600, "message": "Invalid Request"}""")
  )

  test(hello_in_int_out_string, "invalid request structure")(int => IO.pure(Right(int.toString)))(
    request = json"""123""": Raw,
    expectedResponse = JsonRpcResponse.error_v2[Raw](json"""{"code": -32600, "message": "Invalid Request"}""")
  )

  testInvalidRequest("parse error - invalid json")(
    request = invalidJson,
    expectedResponse = JsonRpcResponse.error_v2[Raw](json"""{"code": -32700, "message": "Parse error"}""")
  )

  testInvalidRequest("parse error - root is not an object")(
    request = jsonNotAnObject,
    expectedResponse = JsonRpcResponse.error_v2[Raw](json"""{"code": -32700, "message": "Parse error"}""")
  )

  test(optional_input, "all inputs provided using positional style") { case (s, i) =>
    IO.pure(Right(s"$i${s.getOrElse("")}"))
  }(
    request = JsonRpcRequest.v2[Raw]("optional_input", json"""["alice", 1]""", 1),
    expectedResponse = JsonRpcResponse.v2[Raw](Json.fromString("1alice"), 1)
  )

  test(optional_input_last, "all inputs provided using positional style (optional param last)") { case (s, i) =>
    IO.pure(Right(s"$s${i.getOrElse("")}"))
  }(
    request = JsonRpcRequest.v2[Raw]("optional_input_last", json"""["alice", 1]""", 1),
    expectedResponse = JsonRpcResponse.v2[Raw](Json.fromString("alice1"), 1)
  )

  test(optional_input, "all inputs provided using by-name style") { case (s, i) =>
    IO.pure(Right(s"$i${s.getOrElse("")}"))
  }(
    request = JsonRpcRequest.v2[Raw]("optional_input", json"""{"p2": 1, "p1": "alice"}""", 1),
    expectedResponse = JsonRpcResponse.v2[Raw](Json.fromString("1alice"), 1)
  )

  test(optional_input, "optional input omitted when using positional style") { case (s, i) =>
    IO.pure(Right(s"$i${s.getOrElse("")}"))
  }(request = JsonRpcRequest.v2[Raw]("optional_input", json"""[1]""", 1), expectedResponse = JsonRpcResponse.v2(Json.fromString("1"), 1))

  test(optional_input_last, "optional input omitted when using positional style (optional param last)") { case (s, i) =>
    IO.pure(Right(s"$s${i.getOrElse("")}"))
  }(
    request = JsonRpcRequest.v2[Raw]("optional_input_last", json"""["alice"]""", 1),
    expectedResponse = JsonRpcResponse.v2[Raw](Json.fromString("alice"), 1)
  )

  test(optional_input, "optional input omitted when using by-name style") { case (s, i) =>
    IO.pure(Right(s"$i${s.getOrElse("")}"))
  }(
    request = JsonRpcRequest.v2[Raw]("optional_input", json"""{"p2": 1}""", 1),
    expectedResponse = JsonRpcResponse.v2[Raw](Json.fromString("1"), 1)
  )

  test(optional_input, "should fail when mandatory input omitted when using by-name style") { case (s, i) =>
    IO.pure(Right(s"$i${s.getOrElse("")}"))
  }(
    request = JsonRpcRequest.v2[Raw]("optional_input", json"""{"p1": "alice"}""", 1),
    expectedResponse = JsonRpcResponse.error_v2[Raw](json"""{"code": -32602, "message": "Invalid params"}""", 1)
  )

  test(optional_input, "should fail when mandatory input omitted when using by-pos style") { case (s, i) =>
    IO.pure(Right(s"$i${s.getOrElse("")}"))
  }(
    request = JsonRpcRequest.v2[Raw]("optional_input", json"""["alice"]""", 1),
    expectedResponse = JsonRpcResponse.error_v2[Raw](json"""{"code": -32602, "message": "Invalid params"}""", 1)
  )

  test(optional_input, "should fail when given more parameters than expected - positional style") { case (s, i) =>
    IO.pure(Right(s"$i${s.getOrElse("")}"))
  }(
    request = JsonRpcRequest.v2[Raw]("optional_input", json"""["alice", 1, 2]""", 1),
    expectedResponse = JsonRpcResponse.error_v2[Raw](json"""{"code": -32602, "message": "Invalid params"}""", 1)
  )

  test(optional_input, "should fail when given more parameters than expected - by-name style") { case (s, i) =>
    IO.pure(Right(s"$i${s.getOrElse("")}"))
  }(
    request = JsonRpcRequest.v2[Raw]("optional_input", json"""{"p1": "alice", "p2": 1, "p3": 2}""", 1),
    expectedResponse = JsonRpcResponse.error_v2[Raw](json"""{"code": -32602, "message": "Invalid params"}""", 1)
  )

  test(optional_output, "should return optional response")(_ => IO.pure(Right(Option.empty[String])))(
    request = JsonRpcRequest.v2[Raw]("optional_output", json"""{}""", 1),
    expectedResponse = JsonRpcResponse.v2[Raw](Json.Null, 1)
  )

  test(output_without_params, "should return response when no params attribute is missing")(_ => IO.pure(Right("params is not required")))(
    request = JsonRpcRequest.v2[Raw]("output_without_params", 1),
    expectedResponse = JsonRpcResponse.v2[Raw](Json.fromString("params is not required"), 1)
  )
}
