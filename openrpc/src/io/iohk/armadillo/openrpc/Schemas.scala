package io.iohk.armadillo.openrpc

import io.iohk.armadillo.JsonRpcCodec
import sttp.apispec.{ReferenceOr, Schema => ASchema, SchemaType}
import sttp.tapir.{Schema => TSchema, SchemaType => TSchemaType}

import scala.annotation.tailrec

/** Converts a tapir schema to an OpenAPI/AsyncAPI reference (if the schema is named), or to the appropriate schema. */
class Schemas(
    tschemaToASchema: SchemaToOpenRpcSchema,
    nameToSchemaReference: NameToSchemaReference,
    markOptionsAsNullable: Boolean
) {
  def apply[T](codec: JsonRpcCodec[T], replaceOptionWithCoproduct: Boolean): ReferenceOr[ASchema] =
    apply(codec.schema, replaceOptionWithCoproduct)

  @tailrec
  private def apply(schema: TSchema[_], replaceOptionWithCoproduct: Boolean): ReferenceOr[ASchema] = {
    schema.name match {
      case Some(name) => Left(nameToSchemaReference.map(name))
      case None =>
        schema.schemaType match {
          case TSchemaType.SArray(TSchema(_, Some(name), isOptional, _, _, _, _, _, _, _, _)) =>
            Right(ASchema(SchemaType.Array).copy(items = Some(Left(nameToSchemaReference.map(name)))))
              .map(s => if (isOptional && markOptionsAsNullable) s.copy(nullable = Some(true)) else s)
          case TSchemaType.SOption(ts) =>
            if (replaceOptionWithCoproduct) {
              val synthesised = TSchema( // TODO deduplicate (ToNamedSchemas)
                TSchemaType.SCoproduct[Any](
                  subtypes = List(
                    ts,
                    TSchema(schemaType = TSchemaType.SProduct(List.empty), name = Some(TSchema.SName("Null")), description = Some("null"))
                  ),
                  discriminator = None
                )(_ => None),
                name = ts.name.map(sn => sn.copy(fullName = s"${sn.fullName}OrNull"))
              )
              apply(synthesised, replaceOptionWithCoproduct)
            } else {
              apply(ts, replaceOptionWithCoproduct)
            }
          case _ => tschemaToASchema(schema)
        }
    }
  }
}
