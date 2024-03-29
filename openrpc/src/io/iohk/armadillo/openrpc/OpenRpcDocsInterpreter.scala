package io.iohk.armadillo.openrpc

import io.iohk.armadillo.AnyEndpoint
import io.iohk.armadillo.openrpc.model._
import sttp.apispec.ExampleValue
import sttp.tapir.Schema

case class OpenRpcDocsInterpreter(markOptionsAsNullable: Boolean = true) {
  def toOpenRpc(info: OpenRpcInfo, endpoints: List[AnyEndpoint]): OpenRpcDocument = {
    val sortedEndpoints = endpoints.sorted

    val toNamedSchemas = new ToNamedSchemas
    val (keyToSchema, schemas) =
      new SchemaForEndpoints(sortedEndpoints, toNamedSchemas, markOptionsAsNullable).calculate()

    val methodCreator = new EndpointToOpenRpcMethods(schemas)

    OpenRpcDocument(
      info = info,
      methods = RequiredList(methodCreator.methods(sortedEndpoints)),
      components = if (keyToSchema.nonEmpty) Some(OpenRpcComponents(List.empty, keyToSchema.sortByKey)) else None
    )
  }

}

object OpenRpcDocsInterpreter {

  type NamedSchema = (Schema.SName, Schema[_], Option[ExampleValue])
}
