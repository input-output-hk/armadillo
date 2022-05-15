package io.iohk.armadillo.openrpc

import cats.effect.IO
import io.iohk.armadillo._
import io.iohk.armadillo.openrpc.Basic.{basic, empty, multiple_params, optionalParam, optionalProduct, product, product_with_meta, withInfo}
import io.iohk.armadillo.openrpc.TestUtils.{load, noIndentation}
import io.iohk.armadillo.openrpc.circe.yaml._
import io.iohk.armadillo.openrpc.model.{OpenRpcDocument, OpenRpcInfo}
import weaver.SimpleIOSuite

//verify that:
//method name is unique
//param name is unique
//error codes are unique
object VerifyYamlTest extends SimpleIOSuite {

  private val PetStoreInfo: OpenRpcInfo = OpenRpcInfo("1.0.0", "Demo Pet Store")

  compare("basic.yaml", OpenRpcDocsInterpreter().toOpenRpc(PetStoreInfo, List(basic)))
  compare("multiple_params.yaml", OpenRpcDocsInterpreter().toOpenRpc(PetStoreInfo, List(multiple_params)))
  compare("with_info.yaml", OpenRpcDocsInterpreter().toOpenRpc(PetStoreInfo, List(withInfo)))
  compare("optional_param.yaml", OpenRpcDocsInterpreter().toOpenRpc(PetStoreInfo, List(optionalParam)))
  compare("multiple_endpoints.yaml", OpenRpcDocsInterpreter().toOpenRpc(PetStoreInfo, List(basic, basic.copy(methodName = m"hello2"))))
  compare("empty.yaml", OpenRpcDocsInterpreter().toOpenRpc(PetStoreInfo, List(empty)))
  compare("product.yaml", OpenRpcDocsInterpreter().toOpenRpc(PetStoreInfo, List(product)))
  compare("optional_product.yaml", OpenRpcDocsInterpreter().toOpenRpc(PetStoreInfo, List(optionalProduct)))
  compare("product_with_meta.yaml", OpenRpcDocsInterpreter().toOpenRpc(PetStoreInfo, List(product_with_meta)))

  private def compare(file: String, document: OpenRpcDocument, debug: Boolean = false): Unit = {
    test(file) {
      load(file).use { expected =>
        val actual = noIndentation(document.toYaml)
        if (debug) {
          println(actual)
        }
        IO.delay(expect.same(expected, actual))
      }
    }
  }
}
