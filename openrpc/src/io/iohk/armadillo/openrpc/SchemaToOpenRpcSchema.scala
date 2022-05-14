package io.iohk.armadillo.openrpc

import sttp.tapir.{Schema as TSchema, SchemaType as TSchemaType}
import sttp.tapir.apispec.{Discriminator, Reference, ReferenceOr, SchemaFormat, SchemaType, Schema as ASchema}

import scala.collection.immutable

class SchemaToOpenRpcSchema() {
  def apply[T](schema: TSchema[T], isOptionElement: Boolean = false): ReferenceOr[ASchema] = {
    schema.schemaType match {
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
                case s @ TSchema(_, Some(name), _, _, _, _, _, _, _, _) => f.name.encodedName -> apply(s)
                case schema                                      => f.name.encodedName -> apply(schema)
              }
            }.toListMap
          )
        )
      case TSchemaType.SArray(s @ TSchema(_, Some(name), _, _, _, _, _, _, _,_)) =>
        Right(ASchema(SchemaType.Array).copy(items = Some(apply(s))))
      case TSchemaType.SArray(el) => Right(ASchema(SchemaType.Array).copy(items = Some(apply(el))))
      case TSchemaType.SOption(s @ TSchema(_, Some(name), _, _, _, _, _, _, _,_)) => apply(s)
      case TSchemaType.SOption(el)                                          => apply(el, isOptionElement = true)
      case TSchemaType.SBinary()      => Right(ASchema(SchemaType.String).copy(format = SchemaFormat.Binary))
      case TSchemaType.SDate()        => Right(ASchema(SchemaType.String).copy(format = SchemaFormat.Date))
      case TSchemaType.SDateTime()    => Right(ASchema(SchemaType.String).copy(format = SchemaFormat.DateTime))
      case TSchemaType.SRef(fullName) => ???
      case TSchemaType.SCoproduct(schemas, d) =>
        Right(
          ASchema
            .apply(
              schemas
                .map {
                  case s @ TSchema(_, Some(name), _, _, _, _, _, _, _,_) => apply(s)
                  case t                                           => apply(t)
                }
                .sortBy {
                  case Left(Reference(ref)) => ref
                  case Right(schema)        => schema.`type`.map(_.value).getOrElse("") + schema.toString
                },
              d.map(tDiscriminatorToADiscriminator)
            )
        )
      case TSchemaType.SOpenProduct(valueSchema) =>
        Right(
          ASchema(SchemaType.Object).copy(
            required = List.empty,
            additionalProperties = Some(valueSchema.name match {
              case Some(name) => ???
              case _          => apply(valueSchema)
            })
          )
        )
    }
  }

  private def tDiscriminatorToADiscriminator(discriminator: TSchemaType.SDiscriminator): Discriminator = {
    val schemas = Some(
      discriminator.mapping.map { case (k, TSchemaType.SRef(fullName)) =>
        k -> ???
      }.toListMap
    )
    Discriminator(discriminator.name.encodedName, schemas)
  }

  implicit class IterableToListMap[A](xs: Iterable[A]) {
    def toListMap[T, U](implicit ev: A <:< (T, U)): immutable.ListMap[T, U] = {
      val b = immutable.ListMap.newBuilder[T, U]
      for (x <- xs)
        b += x

      b.result()
    }
  }
}
