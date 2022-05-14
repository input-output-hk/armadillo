package io.iohk.armadillo.openrpc

import io.iohk.armadillo.openrpc.OpenRpcDocsInterpreter.EmptyResult
import io.iohk.armadillo.openrpc.model._
import io.iohk.armadillo.{JsonRpcEndpoint, JsonRpcIO, JsonRpcInput}
import sttp.tapir.apispec.{Schema => OpenRpcSchema}

case class OpenRpcDocsInterpreter(markOptionsAsNullable: Boolean = false) {
  def toOpenRpc(info: OpenRpcInfo, endpoints: List[JsonRpcEndpoint[_, _, _]]): OpenRpcDocument = {
    val schemaConverter = new SchemaToOpenRpcSchema(markOptionsAsNullable)
    OpenRpcDocument(info = info, methods = endpoints.map(convertEndpoint(schemaConverter)))
  }

  private def convertEndpoint(schemaConverter: SchemaToOpenRpcSchema)(endpoint: JsonRpcEndpoint[_, _, _]): OpenRpcMethod = {
    val result = convertResult(schemaConverter, endpoint)
    OpenRpcMethod(
      name = endpoint.methodName.asString,
      params = convertParams(schemaConverter)(endpoint.input),
      result = result,
      summary = endpoint.info.summary,
      description = endpoint.info.description,
      tags = endpoint.info.tags
        .map(t =>
          OpenRpcMethodTag(
            name = t.name,
            summary = t.summary,
            description = t.description,
            externalDocs = t.externalDocs.map(ed => OpenRpcExternalDocs(url = ed.url, description = ed.description))
          )
        )
        .toList
    )
  }

  private def convertParams(schemaConverter: SchemaToOpenRpcSchema)(jsonRpcInput: JsonRpcInput[_]): List[OpenRpcParam] = {
    jsonRpcInput match {
      case o: JsonRpcIO.Single[_] => List(convertParam(schemaConverter)(o))
      case _: JsonRpcIO.Empty[_]  => List.empty
      case JsonRpcInput.Pair(left, right, _, _) =>
        convertParams(schemaConverter)(left) ++ convertParams(schemaConverter)(right)
    }
  }

  private def convertParam(schemaConverter: SchemaToOpenRpcSchema)(jsonRpcInput: JsonRpcIO.Single[_]) = {
    val schema = schemaConverter(jsonRpcInput.codec.schema)
    OpenRpcParam(
      name = jsonRpcInput.name,
      schema = schema,
      required = schema.right.get.nullable.getOrElse(true),
      deprecated = jsonRpcInput.info.deprecated,
      summary = jsonRpcInput.info.summary,
      description = jsonRpcInput.info.description
    )
  }

  private def convertResult(schemaConverter: SchemaToOpenRpcSchema, endpoint: JsonRpcEndpoint[_, _, _]) = {
    endpoint.output match {
      case _: JsonRpcIO.Empty[_] => EmptyResult
      case single: JsonRpcIO.Single[_] =>
        val schema = schemaConverter(single.codec.schema)
        OpenRpcResult(name = single.name, schema = schema, summary = single.info.summary, description = single.info.description)
    }
  }
}

object OpenRpcDocsInterpreter {
  private val EmptyResult = OpenRpcResult(name = "no-response", schema = Right(OpenRpcSchema()))
}
