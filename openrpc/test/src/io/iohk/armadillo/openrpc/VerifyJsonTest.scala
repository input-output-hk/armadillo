package io.iohk.armadillo.openrpc

import cats.effect.IO
import io.circe.Printer
import io.circe.syntax.EncoderOps
import io.iohk.armadillo.openrpc.Basic.basic
import io.iohk.armadillo.openrpc.circe._
import io.iohk.armadillo.openrpc.model.{OpenRpcDocument, OpenRpcInfo}
import weaver.SimpleIOSuite

object VerifyJsonTest extends SimpleIOSuite {

  private val PetStoreInfo: OpenRpcInfo = OpenRpcInfo("1.0.0", "Demo Pet Store")

  compare(
    "basic.json",
    OpenRpcDocsInterpreter().toOpenRpc(PetStoreInfo, List(basic))
  )

  private def compare(file: String, document: OpenRpcDocument): Unit = {
    test(file) {
      load(file).use { expected =>
        val actual = document.asJson
        val actualJsonNoIndent = noIndentation(Printer.spaces2.copy(dropNullValues = true, colonLeft = "").print(actual))
        IO.delay(expect.same(expected, actualJsonNoIndent))
      }
    }
  }
}
