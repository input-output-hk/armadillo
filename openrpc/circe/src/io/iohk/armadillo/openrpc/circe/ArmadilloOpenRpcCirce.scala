package io.iohk.armadillo.openrpc.circe

import io.circe.generic.semiauto._
import io.circe.parser.parse
import io.circe.{Encoder, Json}
import io.iohk.armadillo.openrpc.model._
import sttp.tapir.apispec._

import scala.collection.immutable.ListMap

trait ArmadilloOpenRpcCirce {
  // note: these are strict val-s, order matters!
  implicit def encoderReferenceOr[T: Encoder]: Encoder[ReferenceOr[T]] = {
    case Left(Reference(ref)) => Json.obj(("$ref", Json.fromString(ref)))
    case Right(t)             => implicitly[Encoder[T]].apply(t)
  }

  implicit val docsExtensionValue: Encoder[ExtensionValue] = Encoder.instance(e => parse(e.value).getOrElse(Json.fromString(e.value)))

  implicit val encoderExampleSingleValue: Encoder[ExampleSingleValue] = {
    case ExampleSingleValue(value: String)     => parse(value).getOrElse(Json.fromString(value))
    case ExampleSingleValue(value: Int)        => Json.fromInt(value)
    case ExampleSingleValue(value: Long)       => Json.fromLong(value)
    case ExampleSingleValue(value: Float)      => Json.fromFloatOrString(value)
    case ExampleSingleValue(value: Double)     => Json.fromDoubleOrString(value)
    case ExampleSingleValue(value: Boolean)    => Json.fromBoolean(value)
    case ExampleSingleValue(value: BigDecimal) => Json.fromBigDecimal(value)
    case ExampleSingleValue(value: BigInt)     => Json.fromBigInt(value)
    case ExampleSingleValue(null)              => Json.Null
    case ExampleSingleValue(value)             => Json.fromString(value.toString)
  }
  implicit val encoderExampleValue: Encoder[ExampleValue] = {
    case e: ExampleSingleValue        => encoderExampleSingleValue(e)
    case ExampleMultipleValue(values) => Json.arr(values.map(v => encoderExampleSingleValue(ExampleSingleValue(v))): _*)
  }

  implicit val schemaTypeEncoder: Encoder[SchemaType] = { e => Encoder.encodeString(e.value) }
  implicit val encoderReference: Encoder[Reference] = deriveEncoder[Reference]
  implicit val encoderDiscriminator: Encoder[Discriminator] = deriveEncoder[Discriminator]
  implicit val schemaEncoder: Encoder[Schema] = deriveEncoder[Schema]

  implicit val paramEncoder: Encoder[OpenRpcParam] = deriveEncoder[OpenRpcParam]
  implicit val resultEncoder: Encoder[OpenRpcResult] = deriveEncoder[OpenRpcResult]

  implicit val extDescriptionEncoder: Encoder[OpenRpcExternalDocs] = deriveEncoder[OpenRpcExternalDocs]
  implicit val tagsEncoder: Encoder[OpenRpcMethodTag] = deriveEncoder[OpenRpcMethodTag]

  implicit val methodEncoder: Encoder[OpenRpcMethod] = deriveEncoder[OpenRpcMethod]
  implicit val infoEncoder: Encoder[OpenRpcInfo] = deriveEncoder[OpenRpcInfo]
  implicit val documentEncoder: Encoder[OpenRpcDocument] = deriveEncoder[OpenRpcDocument]

  implicit def encodeList[T: Encoder]: Encoder[List[T]] = {
    case Nil        => Json.Null
    case l: List[T] => Json.arr(l.map(i => implicitly[Encoder[T]].apply(i)): _*)
  }
  implicit def encodeListMap[V: Encoder]: Encoder[ListMap[String, V]] = doEncodeListMap(nullWhenEmpty = true)

  private def doEncodeListMap[V: Encoder](nullWhenEmpty: Boolean): Encoder[ListMap[String, V]] = {
    case m: ListMap[String, V] if m.isEmpty && nullWhenEmpty => Json.Null
    case m: ListMap[String, V] =>
      val properties = m.mapValues(v => implicitly[Encoder[V]].apply(v)).toList
      Json.obj(properties: _*)
  }
}
