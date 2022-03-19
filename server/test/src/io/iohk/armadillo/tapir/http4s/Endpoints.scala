package io.iohk.armadillo.tapir.http4s

import io.iohk.armadillo.Armadillo.{JsonRpcNoDataError, JsonRpcError, noDataError, error, jsonRpcEndpoint, param}
import io.iohk.armadillo.{Armadillo, JsonRpcEndpoint, MethodName}
import io.iohk.armadillo.json.circe.*

object Endpoints {
  val hello_in_int_out_string: JsonRpcEndpoint[Int, Unit, String] = jsonRpcEndpoint(MethodName("hello"))
    .in(param[Int]("param1"))
    .out[String]("response")

  val hello_in_multiple_int_out_string: JsonRpcEndpoint[(Int, Int), Unit, String] = jsonRpcEndpoint(MethodName("hello"))
    .in(param[Int]("param1").and(param[Int]("param2")))
    .out[String]("response")

  val empty: JsonRpcEndpoint[Unit, Unit, Unit] = jsonRpcEndpoint(MethodName("empty"))

  val no_data_error: JsonRpcEndpoint[Unit, JsonRpcNoDataError, Unit] = jsonRpcEndpoint(MethodName("no_data_error"))
    .errorOut(noDataError)

  val single_error: JsonRpcEndpoint[Unit, JsonRpcError[Int], Unit] = jsonRpcEndpoint(MethodName("single_error"))
    .errorOut(error[Int])

  val multiple_errors: JsonRpcEndpoint[Unit, (JsonRpcError[Int], JsonRpcError[String]), Unit] =
    jsonRpcEndpoint(MethodName("multiple_errors"))
      .errorOut(error[Int].and(error[String]))
}
