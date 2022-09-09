package io.iohk.armadillo.openrpc

import sttp.apispec.{Discriminator, Reference, ReferenceOr, Schema => ASchema, SchemaFormat, SchemaType}
import sttp.tapir.{Schema => TSchema, SchemaType => TSchemaType}

class SchemaToOpenRpcSchema(
    nameToSchemaReference: NameToSchemaReference,
    markOptionsAsNullable: Boolean,
    infoToKey: Map[TSchema.SName, String]
) {
  def apply[T](schema: TSchema[T], isOptionElement: Boolean = false): ReferenceOr[ASchema] = {
    val nullable = markOptionsAsNullable && isOptionElement
    val result = schema.schemaType match {
      case TSchemaType.SInteger() => Right(ASchema(SchemaType.Integer))
      case TSchemaType.SNumber()  => Right(ASchema(SchemaType.Number))
      case TSchemaType.SBoolean() => Right(ASchema(SchemaType.Boolean))
      case TSchemaType.SString()  => Right(ASchema(SchemaType.String))
      case p @ TSchemaType.SProduct(fields) =>
        Right(
          ASchema(SchemaType.Object).copy(
            required = p.required.map(_.encodedName),
            properties = fields.map { f =>
              f.schema match {
                case TSchema(_, Some(name), _, _, _, _, _, _, _, _, _) => f.name.encodedName -> Left(nameToSchemaReference.map(name))
                case schema                                            => f.name.encodedName -> apply(schema)
              }
            }.toListMap
          )
        )
      case TSchemaType.SArray(TSchema(_, Some(name), _, _, _, _, _, _, _, _, _)) =>
        Right(ASchema(SchemaType.Array).copy(items = Some(Left(nameToSchemaReference.map(name)))))
      case TSchemaType.SArray(el) => Right(ASchema(SchemaType.Array).copy(items = Some(apply(el))))
      case TSchemaType.SOption(TSchema(_, Some(name), _, _, _, _, _, _, _, _, _)) => Left(nameToSchemaReference.map(name))
      case TSchemaType.SOption(el)                                                => apply(el, isOptionElement = true)
      case TSchemaType.SBinary()      => Right(ASchema(SchemaType.String).copy(format = SchemaFormat.Binary))
      case TSchemaType.SDate()        => Right(ASchema(SchemaType.String).copy(format = SchemaFormat.Date))
      case TSchemaType.SDateTime()    => Right(ASchema(SchemaType.String).copy(format = SchemaFormat.DateTime))
      case TSchemaType.SRef(fullName) => Left(nameToSchemaReference.map(fullName))
      case TSchemaType.SCoproduct(schemas, d) =>
        Right(
          ASchema
            .apply(
              schemas
                .map {
                  case TSchema(_, Some(name), _, _, _, _, _, _, _, _, _) => Left(nameToSchemaReference.map(name))
                  case t                                                 => apply(t)
                }
                .sortBy {
                  case Left(Reference(ref)) => ref
                  case Right(schema)        => schema.`type`.map(_.value).getOrElse("") + schema.toString
                },
              d.map(tDiscriminatorToADiscriminator)
            )
        )
      case TSchemaType.SOpenProduct(_, valueSchema) =>
        Right(
          ASchema(SchemaType.Object).copy(
            required = List.empty,
            additionalProperties = Some(valueSchema.name match {
              case Some(name) => Left(nameToSchemaReference.map(name))
              case _          => apply(valueSchema)
            })
          )
        )
    }
    result
      .map(s => if (nullable) s.copy(nullable = Some(true)) else s)
      .map(addMetadata(_, schema))
  }

  private def addMetadata(oschema: ASchema, tschema: TSchema[_]): ASchema = {
    oschema.copy(
      title = tschema.name.flatMap(infoToKey.get),
      description = tschema.description.orElse(oschema.description),
      default = tschema.default.flatMap { case (_, raw) => raw.flatMap(r => exampleValue(tschema, r)) }.orElse(oschema.default),
      example = tschema.encodedExample.flatMap(exampleValue(tschema, _)).orElse(oschema.example),
      format = tschema.format.orElse(oschema.format),
      deprecated = (if (tschema.deprecated) Some(true) else None).orElse(oschema.deprecated)
    )
  }

  private def tDiscriminatorToADiscriminator(discriminator: TSchemaType.SDiscriminator): Discriminator = {
    val schemas = Some(
      discriminator.mapping.map { case (k, TSchemaType.SRef(fullName)) =>
        k -> nameToSchemaReference.map(fullName).$ref
      }.toListMap
    )
    Discriminator(discriminator.name.encodedName, schemas)
  }
}
