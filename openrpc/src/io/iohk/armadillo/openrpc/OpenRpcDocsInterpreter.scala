package io.iohk.armadillo.openrpc

import io.iohk.armadillo.openrpc.model.*
import io.iohk.armadillo.{JsonRpcEndpoint, JsonRpcIO, JsonRpcInput}
import sttp.tapir.apispec.Schema as OpenRpcSchema

case class OpenRpcDocsInterpreter() {
  def toOpenRpc(info: OpenRpcInfo, endpoints: List[JsonRpcEndpoint[_, _, _]]): OpenRpcDocument = {
    val schemaConverter = new SchemaToOpenRpcSchema()
    OpenRpcDocument(info = info, methods = endpoints.map(convertEndpoint(schemaConverter)))
  }

  private def convertEndpoint(schemaConverter: SchemaToOpenRpcSchema)(endpoint: JsonRpcEndpoint[?, ?, ?]): OpenRpcMethod = {
    val result = convertResult(schemaConverter, endpoint)
    OpenRpcMethod(endpoint.methodName.asString, convertParams(schemaConverter)(endpoint.input), result)
  }

  private def convertParams(schemaConverter: SchemaToOpenRpcSchema)(jsonRpcInput: JsonRpcInput[?]): List[OpenRpcParam] = {
    jsonRpcInput match {
      case o: JsonRpcIO.Single[_] => List(convertParam(schemaConverter)(o))
      case _: JsonRpcIO.Empty[_] => List.empty
      case JsonRpcInput.Pair(left, right, _, _) =>
        convertParams(schemaConverter)(left) ++ convertParams(schemaConverter)(right)
    }
  }

  private def convertParam(schemaConverter: SchemaToOpenRpcSchema)(jsonRpcInput: JsonRpcIO.Single[?]) = {
    OpenRpcParam(jsonRpcInput.name,schemaConverter(jsonRpcInput.codec.schema))
  }

  private def convertResult(schemaConverter: SchemaToOpenRpcSchema, endpoint: JsonRpcEndpoint[_, _, _]) = {
    endpoint.output match {
      case _: JsonRpcIO.Empty[?] => OpenRpcResult("no-response", Right(OpenRpcSchema()))
      case single: JsonRpcIO.Single[?] =>
        val schema = schemaConverter(single.codec.schema)
        OpenRpcResult(single.name, schema)
    }
  }
}
