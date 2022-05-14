package io.iohk.armadillo.openrpc.model
import sttp.tapir.apispec.{ReferenceOr, Schema}

case class OpenRpcDocument(openrpc: String = "1.2.1", info: OpenRpcInfo, methods: List[OpenRpcMethod])

case class OpenRpcInfo(version: String, title: String)

case class OpenRpcMethod(name: String, params: List[OpenRpcParam], result: OpenRpcResult)

case class OpenRpcParam(name: String, schema: ReferenceOr[Schema])

case class OpenRpcResult(name: String, schema: ReferenceOr[Schema])
