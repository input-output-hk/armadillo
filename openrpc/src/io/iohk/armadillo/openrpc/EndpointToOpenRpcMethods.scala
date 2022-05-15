package io.iohk.armadillo.openrpc

import io.iohk.armadillo.openrpc.EndpointToOpenRpcMethods.EmptyResult
import io.iohk.armadillo.{AnyEndpoint, JsonRpcEndpoint, JsonRpcIO, JsonRpcInput}
import io.iohk.armadillo.openrpc.model.{OpenRpcExternalDocs, OpenRpcMethod, OpenRpcMethodTag, OpenRpcParam, OpenRpcResult}
import sttp.tapir.SchemaType
import sttp.tapir.apispec.{ReferenceOr, Schema}

import scala.collection.immutable.ListMap

class EndpointToOpenRpcMethods(schemas: Schemas, keyToSchema: ListMap[String, ReferenceOr[Schema]]) {

  def methods(es: List[AnyEndpoint]): List[OpenRpcMethod] = {
    es.map(convertEndpoint)
  }

  private def convertEndpoint(endpoint: JsonRpcEndpoint[_, _, _]): OpenRpcMethod = {
    val result = convertResult(endpoint)
    OpenRpcMethod(
      name = endpoint.methodName.asString,
      params = convertParams(endpoint.input),
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

  private def convertParams(jsonRpcInput: JsonRpcInput[_]): List[OpenRpcParam] = {
    jsonRpcInput match {
      case o: JsonRpcIO.Single[_] => List(convertParam(o))
      case _: JsonRpcIO.Empty[_]  => List.empty
      case JsonRpcInput.Pair(left, right, _, _) =>
        convertParams(left) ++ convertParams(right)
    }
  }

  private def convertParam(jsonRpcInput: JsonRpcIO.Single[_]) = {
    val schema = schemas(jsonRpcInput.codec.schema)
    OpenRpcParam(
      name = jsonRpcInput.name,
      schema = schema,
      required = jsonRpcInput.codec.schema.schemaType match {
        case SchemaType.SOption(_) => false
        case _                     => true
      },
      deprecated = jsonRpcInput.info.deprecated,
      summary = jsonRpcInput.info.summary,
      description = jsonRpcInput.info.description
    )
  }

  private def convertResult(endpoint: JsonRpcEndpoint[_, _, _]) = {
    endpoint.output match {
      case _: JsonRpcIO.Empty[_] => EmptyResult
      case single: JsonRpcIO.Single[_] =>
        val schema = schemas(single.codec.schema)
        OpenRpcResult(name = single.name, schema = schema, summary = single.info.summary, description = single.info.description)
    }
  }
}
object EndpointToOpenRpcMethods {
  private val EmptyResult =
    OpenRpcResult(name = "empty result", schema = Right(Schema())) // TODO rename to no-response as it carries the intent clearer

}
