package io.iohk.armadillo.openrpc

import io.iohk.armadillo.JsonRpcCodec
import io.iohk.armadillo.openrpc.OpenRpcDocsInterpreter.NamedSchema
import sttp.tapir.{Schema => TSchema, SchemaType => TSchemaType}

import scala.collection.mutable.ListBuffer

class ToNamedSchemas {
  def apply[T](codec: JsonRpcCodec[T], replaceOptionWithCoproduct: Boolean): List[NamedSchema] = {
    if (replaceOptionWithCoproduct) {
      val synthesized = codec.schema match {
        case t @ TSchema(o @ TSchemaType.SOption(element), _, _, _, _, _, _, _, _, _, _) =>
          val element1 = propagateMetadataForOption(t, o).element
          TSchema( // TODO deduplicate (Schemas)
            TSchemaType.SCoproduct[Any](
              subtypes = List(
                element1,
                TSchema(schemaType = TSchemaType.SProduct(List.empty), name = Some(TSchema.SName("Null")), description = Some("null"))
              ),
              discriminator = None
            )(_ => None),
            name = element.name.map(sn => sn.copy(fullName = s"${sn.fullName}OrNull"))
          )
        case other => other
      }
      apply(synthesized)
    } else {
      apply(codec.schema)
    }
  }

  private def apply(schema: TSchema[_]): List[NamedSchema] = {
    val thisSchema = schema.name match {
      case Some(name) => List(name -> schema)
      case None       => Nil
    }
    val nestedSchemas = schema match {
      case TSchema(TSchemaType.SArray(o), _, _, _, _, _, _, _, _, _, _)            => apply(o)
      case t @ TSchema(o: TSchemaType.SOption[_, _], _, _, _, _, _, _, _, _, _, _) =>
        // #1168: if there's an optional field which is an object, with metadata defined (such as description), this
        // needs to be propagated to the target object, so that it isn't omitted.
        apply(propagateMetadataForOption(t, o).element)
      case TSchema(st: TSchemaType.SProduct[_], _, _, _, _, _, _, _, _, _, _)        => productSchemas(st)
      case TSchema(st: TSchemaType.SCoproduct[_], _, _, _, _, _, _, _, _, _, _)      => coproductSchemas(st)
      case TSchema(st: TSchemaType.SOpenProduct[_, _], _, _, _, _, _, _, _, _, _, _) => apply(st.valueSchema)
      case _                                                                         => List.empty
    }

    thisSchema ++ nestedSchemas
  }

  private def productSchemas[T](st: TSchemaType.SProduct[T]): List[NamedSchema] = st.fields.flatMap(a => apply(a.schema))

  private def coproductSchemas[T](st: TSchemaType.SCoproduct[T]): List[NamedSchema] = st.subtypes.flatMap(apply)
}

object ToNamedSchemas {

  /** Keeps only the first object data for each `SName`. In case of recursive objects, the first one is the most complete as it contains the
    * built-up structure, unlike subsequent ones, which only represent leaves (tapir#354).
    */
  def unique(objs: Iterable[NamedSchema]): Iterable[NamedSchema] = {
    val seen: collection.mutable.Set[TSchema.SName] = collection.mutable.Set()
    val result: ListBuffer[NamedSchema] = ListBuffer()
    objs.foreach { obj =>
      if (!seen.contains(obj._1)) {
        seen.add(obj._1)
        result += obj
      }
    }
    result.toList
  }
}
