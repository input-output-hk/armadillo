package io.iohk.armadillo.openrpc

import io.iohk.armadillo.{JsonRpcEndpoint, jsonRpcEndpoint, param}
import io.iohk.armadillo.json.circe._
import io.iohk.armadillo._

object Basic {
  val hello_in_int_out_string: JsonRpcEndpoint[Int, Unit, String] = jsonRpcEndpoint(m"hello")
    .in(param[Int]("param1"))
    .out[String]("response")
}
