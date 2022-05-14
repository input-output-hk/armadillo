package io.iohk.armadillo.openrpc.circe.yaml

import io.circe.syntax._
import io.circe.yaml.Printer
import io.iohk.armadillo.openrpc.circe._
import io.iohk.armadillo.openrpc.model.OpenRpcDocument

trait ArmadilloOpenRpcCirceYaml {
  implicit class RichOpenRpcDocument(document: OpenRpcDocument) {
    def toYaml: String = Printer(dropNullKeys = true, preserveOrder = true).pretty(document.asJson)
  }
}
