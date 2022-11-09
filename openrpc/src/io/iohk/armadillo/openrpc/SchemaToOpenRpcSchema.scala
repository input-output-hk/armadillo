package io.iohk.armadillo.openrpc

import sttp.apispec.{
  ArraySchemaType,
  BasicSchemaType,
  Discriminator,
  ExampleSingleValue,
  ExampleValue,
  Pattern,
  Reference,
  ReferenceOr,
  Schema => ASchema,
  SchemaFormat,
  SchemaType
}
import sttp.tapir.{Schema => TSchema, SchemaType => TSchemaType, Validator}

class SchemaToOpenRpcSchema(
    nameToSchemaReference: NameToSchemaReference,
    markOptionsAsNullable: Boolean,
    infoToKey: Map[TSchema.SName, String]
) {
  def apply[T](schema: TSchema[T], examples: Option[ExampleValue] = None, isOptionElement: Boolean = false): ReferenceOr[ASchema] = {
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
      case TSchemaType.SOption(el)                                                => apply(el, examples, isOptionElement = true)
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
                  case Left(Reference(ref, _, _)) => ref
                  case Right(schema) =>
                    schema.`type`
                      .map {
                        case ArraySchemaType(value)      => value.sortBy(schemaType => schemaType.value).mkString
                        case schemaType: BasicSchemaType => schemaType.value
                      }
                      .getOrElse("") + schema.toString
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
      .map(addMetadata(_, schema, examples))
      .map(addValidatorInfo(_, schema.validator))
  }

  private def addMetadata(oschema: ASchema, tschema: TSchema[_], examples: Option[ExampleValue]): ASchema = {
    oschema.copy(
      title = tschema.name.flatMap(infoToKey.get),
      description = tschema.description.orElse(oschema.description),
      default = tschema.default.flatMap { case (_, raw) => raw.flatMap(r => exampleValue(tschema, r)) }.orElse(oschema.default),
      example = examples.orElse(tschema.encodedExample.flatMap(exampleValue(tschema, _))).orElse(oschema.example),
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

  private def addValidatorInfo(schema: ASchema, validator: Validator[_]): ASchema = {
    validator match {
      case m @ Validator.Min(value, exclusive) =>
        val minimum = BigDecimal(m.valueIsNumeric.toDouble(value))
        schema.copy(minimum = Some(minimum), exclusiveMinimum = Some(exclusive))
      case m @ Validator.Max(value, exclusive) =>
        val maximum = BigDecimal(m.valueIsNumeric.toDouble(value))
        schema.copy(maximum = Some(maximum), exclusiveMaximum = Some(exclusive))
      case Validator.Enumeration(possibleValues, encode, _) =>
        val encodedEnumValues = possibleValues.map(v => ExampleSingleValue(encode.flatMap(_(v)).getOrElse(v.toString)))
        schema.copy(`enum` = Some(encodedEnumValues))
      case Validator.Pattern(value)   => schema.copy(pattern = Some(Pattern(value)))
      case Validator.MinLength(value) => schema.copy(minLength = Some(value))
      case Validator.MaxLength(value) => schema.copy(maxLength = Some(value))
      case Validator.MinSize(value)   => schema.copy(minItems = Some(value))
      case Validator.MaxSize(value)   => schema.copy(maxItems = Some(value))
      case Validator.All(validators)  => validators.foldLeft(schema)(addValidatorInfo)
      case Validator.Custom(_, showMessage) =>
        val newDescription = Some(List(schema.description, showMessage).flatten.mkString("\n")).filter(_.nonEmpty)
        schema.copy(description = newDescription)
      case Validator.Mapped(wrapped, _) => addValidatorInfo(schema, wrapped)
      case Validator.Any(_)             => schema
    }
  }

}
