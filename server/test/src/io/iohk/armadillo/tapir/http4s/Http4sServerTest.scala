package io.iohk.armadillo.tapir.http4s

import cats.effect.IO
import io.circe.Json
import io.circe.literal.*
import io.iohk.armadillo.Armadillo.{JsonRpcError, JsonRpcRequest, JsonRpcResponse}
import io.iohk.armadillo.tapir.http4s.Endpoints.*
import cats.syntax.all._

object Http4sServerTest extends BaseSuite {

  test(hello_in_int_out_string)(int => IO.pure(Right(int.toString)))(
    request = JsonRpcRequest[Json]("2.0", "hello", json"[42]", 1),
    expectedResponse = JsonRpcResponse("2.0", json"${"42"}", 1).asRight[List[JsonRpcError[Json]]]
  )
}
