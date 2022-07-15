package io.iohk.armadillo.server

import io.iohk.armadillo._
import io.iohk.armadillo.json.circe._

object Endpoints {
  val hello_in_int_out_string: JsonRpcEndpoint[Int, Unit, String] = jsonRpcEndpoint(m"hello")
    .in(param[Int]("param1"))
    .out[String]("response")

  val hello_in_int_out_string_by_name: JsonRpcEndpoint[Int, Unit, String] = jsonRpcEndpoint(m"hello", ParamStructure.ByName)
    .in(param[Int]("param1"))
    .out[String]("response")

  val hello_in_int_out_string_by_position: JsonRpcEndpoint[Int, Unit, String] =
    jsonRpcEndpoint(m"hello", ParamStructure.ByPosition)
      .in(param[Int]("param1"))
      .out[String]("response")

  val hello_in_multiple_int_out_string: JsonRpcEndpoint[(Int, Int), Unit, String] = jsonRpcEndpoint(m"hello")
    .in(param[Int]("param1").and(param[Int]("param2")))
    .out[String]("response")

  val empty: JsonRpcEndpoint[Unit, Unit, Unit] = jsonRpcEndpoint(m"empty")

  val error_with_data: JsonRpcEndpoint[Unit, Int, Unit] = jsonRpcEndpoint(m"error_with_data")
    .errorOut(error[Int])

  val e1_int_string_out_int: JsonRpcEndpoint[String, Unit, Int] = jsonRpcEndpoint(m"e1")
    .in(param[String]("param1"))
    .out[Int]("response")

  val optional_input: JsonRpcEndpoint[(Option[String], Int), Unit, String] = jsonRpcEndpoint(m"optional_input")
    .in(
      param[Option[String]]("p1").and(param[Int]("p2"))
    )
    .out[String]("response")

  val optional_output: JsonRpcEndpoint[Unit, Unit, Option[String]] = jsonRpcEndpoint(m"optional_output")
    .out[Option[String]]("response")

  val output_without_params: JsonRpcEndpoint[Unit, Unit, String] = jsonRpcEndpoint(m"output_without_params")
    .out[String]("response")
}
