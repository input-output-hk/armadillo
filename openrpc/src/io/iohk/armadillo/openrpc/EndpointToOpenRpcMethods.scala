package io.iohk.armadillo.openrpc

import io.iohk.armadillo.openrpc.EndpointToOpenRpcMethods.EmptyResult
import io.iohk.armadillo.openrpc.model._
import io.iohk.armadillo.{AnyEndpoint, JsonRpcCodec, JsonRpcEndpoint, JsonRpcErrorOutput, JsonRpcIO, JsonRpcInput}
import sttp.apispec.Schema
import sttp.tapir.SchemaType

class EndpointToOpenRpcMethods(schemas: Schemas) {

  def methods(es: List[AnyEndpoint]): List[OpenRpcMethod] = {
    es.map(convertEndpoint)
  }

  private def convertEndpoint(endpoint: JsonRpcEndpoint[_, _, _]): OpenRpcMethod = {
    val result = convertResult(endpoint)
    OpenRpcMethod(
      name = endpoint.methodName.asString,
      params = RequiredList(convertParams(endpoint.input)),
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
        .toList,
      errors = convertError(endpoint.error)
    )
  }

  private def convertParams(jsonRpcInput: JsonRpcInput[_]): List[OpenRpcParam] = {
    jsonRpcInput match {
      case o: JsonRpcIO.Single[_] => List(convertParam(o))
      case _: JsonRpcIO.Empty[_]  => List.empty
      case JsonRpcInput.Pair(left, right) =>
        convertParams(left) ++ convertParams(right)
    }
  }

  private def convertParam[I](jsonRpcInput: JsonRpcIO.Single[I]) = {
    val schema = schemas(jsonRpcInput.codec, replaceOptionWithCoproduct = false)
    OpenRpcParam(
      name = jsonRpcInput.name,
      schema = schema.map(updateSchema(_, jsonRpcInput.codec, jsonRpcInput.info.examples)),
      required = jsonRpcInput.codec.schema.schemaType match {
        case SchemaType.SOption(_) => false
        case _                     => true
      },
      deprecated = jsonRpcInput.info.deprecated,
      summary = jsonRpcInput.info.summary,
      description = jsonRpcInput.info.description
    )
  }

  private def convertResult[O](endpoint: JsonRpcEndpoint[_, _, O]) = {
    endpoint.output match {
      case _: JsonRpcIO.Empty[O] => EmptyResult
      case single: JsonRpcIO.Single[O] =>
        val schema = schemas(single.codec, replaceOptionWithCoproduct = true)
        OpenRpcResult(
          name = single.name,
          schema = schema.map(updateSchema(_, single.codec, single.info.examples)),
          summary = single.info.summary,
          description = single.info.description
        )
    }
  }

  private def updateSchema[T](schema: Schema, codec: JsonRpcCodec[T], examples: Set[T]) = {
    schema.copy(example = exampleValue(codec, examples))
  }

  private def convertError(errorOutput: JsonRpcErrorOutput[_]): List[OpenRpcError] = {
    errorOutput match {
      case single: JsonRpcErrorOutput.Fixed[_] =>
        List(OpenRpcError(single.code, single.message, None))
      case single: JsonRpcErrorOutput.FixedWithData[_] =>
        val schema = schemas(single.codec, replaceOptionWithCoproduct = true)
        List(OpenRpcError(single.code, single.message, Some(schema)))
      case JsonRpcErrorOutput.OneOf(variants, _) =>
        variants.flatMap(v => convertError(v.output))
      case _ => List.empty
    }
  }
}
object EndpointToOpenRpcMethods {
  private val EmptyResult =
    OpenRpcResult(name = "empty result", schema = Right(Schema())) // TODO rename to no-response as it carries the intent clearer

}
