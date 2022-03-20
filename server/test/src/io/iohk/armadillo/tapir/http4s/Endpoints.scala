package io.iohk.armadillo.tapir.http4s

import io.circe.{Decoder, Encoder}
import io.iohk.armadillo.Armadillo.{JsonRpcError, JsonRpcErrorWithData, JsonRpcErrorNoData, error, jsonRpcEndpoint, noDataError, param}
import io.iohk.armadillo.{Armadillo, JsonRpcEndpoint, MethodName}
import io.iohk.armadillo.json.circe.*
import sttp.tapir.Schema

object Endpoints {
  val hello_in_int_out_string: JsonRpcEndpoint[Int, Unit, String] = jsonRpcEndpoint(MethodName("hello"))
    .in(param[Int]("param1"))
    .out[String]("response")

  val hello_in_multiple_int_out_string: JsonRpcEndpoint[(Int, Int), Unit, String] = jsonRpcEndpoint(MethodName("hello"))
    .in(param[Int]("param1").and(param[Int]("param2")))
    .out[String]("response")

  val empty: JsonRpcEndpoint[Unit, Unit, Unit] = jsonRpcEndpoint(MethodName("empty"))

  val error_with_data: JsonRpcEndpoint[Unit, Int, Unit] = jsonRpcEndpoint(MethodName("error_with_data"))
    .errorOut(
      error[Int]
    )
}
