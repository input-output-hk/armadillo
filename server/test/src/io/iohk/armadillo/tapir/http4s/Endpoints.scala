package io.iohk.armadillo.tapir.http4s

import io.iohk.armadillo.Armadillo.{jsonRpcEndpoint, param}
import io.iohk.armadillo.{JsonRpcEndpoint, MethodName}
import io.iohk.armadillo.json.circe.*

object Endpoints {
  val hello_in_int_out_string: JsonRpcEndpoint[Int, Unit, String] = jsonRpcEndpoint(MethodName("hello"))
    .in(param[Int]("param1"))
    .out[String]("response")

  val hello_in_multiple_int_out_string: JsonRpcEndpoint[(Int, Int), Unit, String] = jsonRpcEndpoint(MethodName("hello"))
    .in(param[Int]("param1").and(param[Int]("param2")))
    .out[String]("response")
}
