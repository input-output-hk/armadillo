package io.iohk.armadillo.openrpc

import cats.effect.IO
import io.iohk.armadillo.openrpc.Basic.hello_in_int_out_string
import io.iohk.armadillo.openrpc.circe.yaml.*
import io.iohk.armadillo.openrpc.model.{OpenRpcDocument, OpenRpcInfo}
import weaver.SimpleIOSuite

object VerifyYamlTest extends SimpleIOSuite {

  test("hello_in_int_out_string") {
    compare(
      "hello_in_int_out_string.yaml",
      OpenRpcDocsInterpreter().toOpenRpc(OpenRpcInfo("1.0.0", "Demo Pet Store"), List(hello_in_int_out_string))
    )
  }

  private def compare(file: String, document: OpenRpcDocument) = {
    load(file).use { expected =>
      val actual = noIndentation(document.toYaml)
      IO.delay(expect.same(expected, actual))
    }
  }
}
