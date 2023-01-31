package io.iohk.armadillo.openrpc

import cats.effect.IO
import io.iohk.armadillo._
import io.iohk.armadillo.openrpc.Basic._
import io.iohk.armadillo.openrpc.TestUtils.{load, noIndentation}
import io.iohk.armadillo.openrpc.circe._
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
  compare("external_ref.yaml", OpenRpcDocsInterpreter().toOpenRpc(PetStoreInfo, List(external_ref)))
  compare("product_array.yaml", OpenRpcDocsInterpreter().toOpenRpc(PetStoreInfo, List(productArray)))
  compare("nested_product.yaml", OpenRpcDocsInterpreter().toOpenRpc(PetStoreInfo, List(nestedProducts)))
  compare("product_duplicated_names.yaml", OpenRpcDocsInterpreter().toOpenRpc(PetStoreInfo, List(productDuplicatedNames)))
  compare("recursive_product.yaml", OpenRpcDocsInterpreter().toOpenRpc(PetStoreInfo, List(recursiveProduct)))
  compare("generic_product.yaml", OpenRpcDocsInterpreter().toOpenRpc(PetStoreInfo, List(genericProduct)))
  compare("optional_result_product.yaml", OpenRpcDocsInterpreter().toOpenRpc(PetStoreInfo, List(optionalResultProduct)))
  compare("result_product.yaml", OpenRpcDocsInterpreter().toOpenRpc(PetStoreInfo, List(resultProduct)))
  compare("optional_recursive_result.yaml", OpenRpcDocsInterpreter().toOpenRpc(PetStoreInfo, List(optionalRecursiveResult)))
  compare("array_of_recursive_optional_result.yaml", OpenRpcDocsInterpreter().toOpenRpc(PetStoreInfo, List(arrayOfRecursiveOptionalResult)))
  compare("single_fixed_error.yaml", OpenRpcDocsInterpreter().toOpenRpc(PetStoreInfo, List(singleFixedError)))
  compare("single_fixed_error_with_data.yaml", OpenRpcDocsInterpreter().toOpenRpc(PetStoreInfo, List(singleFixedErrorWithData)))
  compare("one_of_fixed_errors.yaml", OpenRpcDocsInterpreter().toOpenRpc(PetStoreInfo, List(oneOfFixedErrors)))
  compare("one_of_fixed_errors_with_data.yaml", OpenRpcDocsInterpreter().toOpenRpc(PetStoreInfo, List(oneOfFixedErrorsWithData)))
  compare("sum.yaml", OpenRpcDocsInterpreter().toOpenRpc(PetStoreInfo, List(sum)))
  compare("validatedInts.yaml", OpenRpcDocsInterpreter().toOpenRpc(PetStoreInfo, List(validatedInts)))
  compare("validatedIntsWithExclusives.yaml", OpenRpcDocsInterpreter().toOpenRpc(PetStoreInfo, List(validatedIntsWithExclusives)))
  compare("validatedStrings.yaml", OpenRpcDocsInterpreter().toOpenRpc(PetStoreInfo, List(validatedStrings)))
  compare("validatedArrays.yaml", OpenRpcDocsInterpreter().toOpenRpc(PetStoreInfo, List(validatedArrays)))
  compare("validatedStringEnumeration.yaml", OpenRpcDocsInterpreter().toOpenRpc(PetStoreInfo, List(validatedStringEnumeration)))
  compare("validatedEnumerations.yaml", OpenRpcDocsInterpreter().toOpenRpc(PetStoreInfo, List(validatedEnumeration)))
  compare("validatedAll.yaml", OpenRpcDocsInterpreter().toOpenRpc(PetStoreInfo, List(validatedAll)))
  compare("validatedCustom.yaml", OpenRpcDocsInterpreter().toOpenRpc(PetStoreInfo, List(validatedCustom)))
  compare("validatedMapped.yaml", OpenRpcDocsInterpreter().toOpenRpc(PetStoreInfo, List(validatedMapped)))

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
