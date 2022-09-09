package io.iohk.armadillo

import sttp.apispec.{ExampleMultipleValue, ExampleSingleValue, ExampleValue}
import sttp.tapir.{Codec, Schema => TSchema, SchemaType => TSchemaType}

import scala.collection.immutable

package object openrpc {
  implicit class SortListMap[K, V](m: immutable.ListMap[K, V]) {
    def sortByKey(implicit ko: Ordering[K]): immutable.ListMap[K, V] = sortBy(_._1)
    def sortBy[B: Ordering](f: ((K, V)) => B): immutable.ListMap[K, V] = {
      m.toList.sortBy(f).toListMap
    }
  }

  implicit class IterableToListMap[A](xs: Iterable[A]) {
    def toListMap[T, U](implicit ev: A <:< (T, U)): immutable.ListMap[T, U] = {
      val b = immutable.ListMap.newBuilder[T, U]
      for (x <- xs)
        b += x

      b.result()
    }
  }

  private[openrpc] def propagateMetadataForOption[T, E](schema: TSchema[T], opt: TSchemaType.SOption[T, E]): TSchemaType.SOption[T, E] = {
    opt.copy(
      element = opt.element.copy(
        description = schema.description.orElse(opt.element.description),
        format = schema.format.orElse(opt.element.format),
        deprecated = schema.deprecated || opt.element.deprecated
      )
    )(opt.toOption)
  }
  private def rawToString[T](v: Any): String = v.toString
  private[openrpc] def exampleValue[T](v: String): ExampleValue = ExampleSingleValue(v)
  private[openrpc] def exampleValue[T](codec: Codec[_, T, _], e: T): Option[ExampleValue] = exampleValue(codec.schema, codec.encode(e))
  private[openrpc] def exampleValue[T](schema: TSchema[_], raw: Any): Option[ExampleValue] = {
    (raw, schema.schemaType) match {
      case (it: Iterable[_], TSchemaType.SArray(_)) => Some(ExampleMultipleValue(it.map(rawToString).toList))
      case (it: Iterable[_], _)                     => it.headOption.map(v => ExampleSingleValue(rawToString(v)))
      case (it: Option[_], _)                       => it.map(v => ExampleSingleValue(rawToString(v)))
      case (v, _)                                   => Some(ExampleSingleValue(rawToString(v)))
    }
  }
}
