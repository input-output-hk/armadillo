package io.iohk.armadillo.openrpc.circe.yaml

import io.circe.Encoder
import io.circe.syntax._
import io.circe.yaml.Printer
import io.iohk.armadillo.openrpc.model.OpenRpcDocument

trait ArmadilloOpenRpcCirceYaml {
  implicit class RichOpenRpcDocument(document: OpenRpcDocument) {
    def toYaml(implicit documentEncoder: Encoder[OpenRpcDocument]): String =
      Printer(dropNullKeys = true, preserveOrder = true).pretty(document.asJson(documentEncoder))
  }
}
