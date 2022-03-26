package io.iohk.armadillo.server

import io.iohk.armadillo.{JsonRpcEndpoint, MethodName, ParamStructure, error, jsonRpcEndpoint, param}
import io.iohk.armadillo.json.circe.*

object Endpoints {
  val hello_in_int_out_string: JsonRpcEndpoint[Int, Unit, String] = jsonRpcEndpoint(MethodName("hello"))
    .in(param[Int]("param1"))
    .out[String]("response")

  val hello_in_int_out_string_by_name: JsonRpcEndpoint[Int, Unit, String] = jsonRpcEndpoint(MethodName("hello"), ParamStructure.ByName)
    .in(param[Int]("param1"))
    .out[String]("response")

  val hello_in_int_out_string_by_position: JsonRpcEndpoint[Int, Unit, String] =
    jsonRpcEndpoint(MethodName("hello"), ParamStructure.ByPosition)
      .in(param[Int]("param1"))
      .out[String]("response")

  val hello_in_multiple_int_out_string: JsonRpcEndpoint[(Int, Int), Unit, String] = jsonRpcEndpoint(MethodName("hello"))
    .in(param[Int]("param1").and(param[Int]("param2")))
    .out[String]("response")

  val empty: JsonRpcEndpoint[Unit, Unit, Unit] = jsonRpcEndpoint(MethodName("empty"))

  val error_with_data: JsonRpcEndpoint[Unit, Int, Unit] = jsonRpcEndpoint(MethodName("error_with_data"))
    .errorOut(error[Int])

  val e1_int_string_out_int: JsonRpcEndpoint[String, Unit, Int] = jsonRpcEndpoint(MethodName("e1"))
    .in(param[String]("param1"))
    .out[Int]("response")
}
