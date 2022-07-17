package io.iohk.armadillo.server

import io.circe.generic.auto._
import io.iohk.armadillo._
import io.iohk.armadillo.json.circe._
import sttp.tapir.generic.auto._

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

  val error_no_data: JsonRpcEndpoint[Unit, JsonRpcError.NoData, Unit] = jsonRpcEndpoint(m"error_no_data")
    .errorOut(error)

  val error_with_data: JsonRpcEndpoint[Unit, JsonRpcError[Int], Unit] = jsonRpcEndpoint(m"error_with_data")
    .errorOut(errorWithData[Int])

  val fixed_error: JsonRpcEndpoint[Unit, Unit, Unit] = jsonRpcEndpoint(m"fixed_error")
    .errorOut(fixedError(200, "something went wrong"))

  val fixed_error_with_data: JsonRpcEndpoint[Unit, String, Unit] = jsonRpcEndpoint(m"fixed_error_with_data")
    .errorOut(fixedErrorWithData[String](200, "something went wrong"))

  val oneOf_fixed_errors_with_data: JsonRpcEndpoint[Unit, ErrorInfo, Unit] = jsonRpcEndpoint(m"fixed_error")
    .errorOut(
      oneOf[ErrorInfo](
        oneOfVariant(fixedErrorWithData[ErrorInfoSmall](201, "something went really wrong")),
        oneOfVariant(fixedErrorWithData[ErrorInfoBig](200, "something went wrong"))
      )
    )

  sealed trait ErrorInfo
  case class ErrorInfoSmall(msg: String) extends ErrorInfo
  case class ErrorInfoBig(msg: String, code: Int) extends ErrorInfo

  val oneOf_fixed_errors_value_matcher: JsonRpcEndpoint[Unit, Either[Unit, Unit], Unit] = jsonRpcEndpoint(m"fixed_error")
    .errorOut(
      oneOf(
        oneOfVariantValueMatcher[Either[Unit, Unit]](fixedError(201, "something went really wrong")) { case Left(_) => true },
        oneOfVariantValueMatcher[Either[Unit, Unit]](fixedError(200, "something went wrong")) { case Right(_) => true }
      )
    )

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
