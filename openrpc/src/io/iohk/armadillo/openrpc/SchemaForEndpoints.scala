package io.iohk.armadillo.openrpc

import io.iohk.armadillo.openrpc.OpenRpcDocsInterpreter.NamedSchema
import io.iohk.armadillo.{AnyEndpoint, JsonRpcErrorOutput, JsonRpcIO, JsonRpcInput, JsonRpcOutput}
import sttp.apispec.{ReferenceOr, Schema => ASchema, SchemaType => _}
import sttp.tapir.Schema.SName

import scala.collection.immutable.ListMap

class SchemaForEndpoints(es: List[AnyEndpoint], toNamedSchemas: ToNamedSchemas, markOptionsAsNullable: Boolean) {

  private val defaultSchemaName: SName => String = info => {
    val shortName = info.fullName.split('.').last
    (shortName +: info.typeParameterShortNames).mkString("_")
  }

  def calculate(): (ListMap[String, ReferenceOr[ASchema]], Schemas) = {
    val sObjects = ToNamedSchemas.unique(es.flatMap(e => forInput(e.input) ++ forOutput(e.output) ++ forErrorOutput(e.error)))
    val infoToKey = calculateUniqueKeys(sObjects.map(_._1), defaultSchemaName)
    val objectToSchemaReference = new NameToSchemaReference(infoToKey)
    val schemaConverter = new SchemaToOpenRpcSchema(objectToSchemaReference, markOptionsAsNullable, infoToKey)
    val schemas = new Schemas(schemaConverter, objectToSchemaReference, markOptionsAsNullable)
    val infosToSchema = sObjects.map(td => (td._1, schemaConverter(td._2))).toListMap
    val schemaKeys = infosToSchema.map { case (k, v) => k -> ((infoToKey(k), v)) }
    (schemaKeys.values.toListMap, schemas)
  }

  private def forInput(input: JsonRpcInput[_]): List[NamedSchema] = {
    input match {
      case io: JsonRpcIO[_]               => forIO(io, replaceOptionWithCoproduct = false)
      case JsonRpcInput.Pair(left, right) => forInput(left) ++ forInput(right)
    }
  }

  private def forIO(io: JsonRpcIO[_], replaceOptionWithCoproduct: Boolean): List[NamedSchema] = {
    io match {
      case JsonRpcIO.Empty()             => List.empty
      case JsonRpcIO.Single(codec, _, _) => toNamedSchemas(codec, replaceOptionWithCoproduct)
    }
  }

  private def forOutput(output: JsonRpcOutput[_]): List[NamedSchema] = {
    output match {
      case io: JsonRpcIO[_] => forIO(io, replaceOptionWithCoproduct = true)
    }
  }

  private def forErrorOutput(output: JsonRpcErrorOutput[_]): List[NamedSchema] = {
    output match {
      case JsonRpcErrorOutput.FixedWithData(_, _, codec) => toNamedSchemas(codec, replaceOptionWithCoproduct = true)
      case JsonRpcErrorOutput.OneOf(variants, _)         => variants.flatMap(v => forErrorOutput(v.output))
      case _                                             => List.empty
    }
  }

  private[openrpc] def calculateUniqueKeys[T](ts: Iterable[T], toName: T => String): Map[T, String] = {
    case class Assigment(nameToT: Map[String, T], tToKey: Map[T, String])
    ts
      .foldLeft(Assigment(Map.empty, Map.empty)) { case (Assigment(nameToT, tToKey), t) =>
        val key = uniqueName(toName(t), n => !nameToT.contains(n) || nameToT.get(n).contains(t))

        Assigment(
          nameToT + (key -> t),
          tToKey + (t -> key)
        )
      }
      .tToKey
  }

  // scalafix:off DisableSyntax.var
  private[openrpc] def uniqueName(base: String, isUnique: String => Boolean): String = {
    var i = 0
    var result = base
    while (!isUnique(result)) {
      i += 1
      result = base + i
    }
    result
  }
  // scalafix:on
}
