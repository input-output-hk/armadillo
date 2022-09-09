package io.iohk.armadillo.openrpc.model

import sttp.apispec.{ReferenceOr, Schema}

import scala.collection.immutable.ListMap

case class OpenRpcDocument(
    openrpc: String = "1.2.1",
    info: OpenRpcInfo,
    methods: List[OpenRpcMethod],
    components: Option[OpenRpcComponents]
)

case class OpenRpcComponents(contentDescriptors: List[Unit], schemas: ListMap[String, ReferenceOr[Schema]])

case class OpenRpcInfo(version: String, title: String)

case class OpenRpcMethod(
    name: String,
    tags: List[OpenRpcMethodTag] = List.empty,
    summary: Option[String] = None,
    description: Option[String] = None,
    params: List[OpenRpcParam] = List.empty,
    errors: List[OpenRpcError] = List.empty,
    result: OpenRpcResult
)

case class OpenRpcMethodTag(
    name: String,
    summary: Option[String] = None,
    description: Option[String] = None,
    externalDocs: Option[OpenRpcExternalDocs] = None
)

case class OpenRpcExternalDocs(url: String, description: Option[String])

case class OpenRpcParam(
    name: String,
    summary: Option[String] = None,
    description: Option[String] = None,
    required: Boolean,
    schema: ReferenceOr[Schema],
    deprecated: Option[Boolean]
)

case class OpenRpcResult(name: String, summary: Option[String] = None, description: Option[String] = None, schema: ReferenceOr[Schema])

case class OpenRpcError(code: Int, message: String, data: Option[ReferenceOr[Schema]])
