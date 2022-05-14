package io.iohk.armadillo.openrpc

import cats.effect.IO
import io.circe.Printer
import io.circe.syntax.EncoderOps
import io.iohk.armadillo.openrpc.Basic.hello_in_int_out_string
import io.iohk.armadillo.openrpc.circe.yaml.*
import io.iohk.armadillo.openrpc.model.{OpenRpcDocument, OpenRpcInfo}
import weaver.SimpleIOSuite
import io.iohk.armadillo.openrpc.circe.*

object VerifyJsonTest extends SimpleIOSuite {

  test("hello_in_int_out_string") {
    compare(
      "hello_in_int_out_string.json",
      OpenRpcDocsInterpreter().toOpenRpc(OpenRpcInfo("1.0.0", "Demo Pet Store"), List(hello_in_int_out_string))
    )
  }

  private def compare(file: String, document: OpenRpcDocument) = {
    load(file).use { expected =>
      val actual = document.asJson
      val actualJsonNoIndent = noIndentation(Printer.spaces2.copy(dropNullValues = true, colonLeft = "").print(actual))
      IO.delay(expect.same(expected, actualJsonNoIndent))
    }
  }
}
