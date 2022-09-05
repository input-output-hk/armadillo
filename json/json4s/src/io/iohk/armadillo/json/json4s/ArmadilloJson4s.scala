package io.iohk.armadillo.json.json4s

import io.iohk.armadillo._
import org.json4s.JsonAST.JValue
import org.json4s.{Extraction, Formats, Serialization}
import sttp.tapir.{DecodeResult, Schema}

import scala.util.{Failure, Success, Try}

trait ArmadilloJson4s {
  implicit def jsonRpcCodec[H: Schema: Manifest](implicit formats: Formats, serialization: Serialization): JsonRpcCodec[H] =
    new JsonRpcCodec[H] {
      override type L = JValue

      override def encode(h: H): JValue = Extraction.decompose(h)

      override def schema: Schema[H] = implicitly[Schema[H]]

      override def decode(l: JValue): DecodeResult[H] = {
        Try(l.extract[H]) match {
          case Failure(exception) => DecodeResult.Error(l.toString, exception)
          case Success(value)     => DecodeResult.Value(value)
        }
      }
    }

  implicit def json4sjsonRpcCodec(implicit _schema: Schema[JValue]): JsonRpcCodec[JValue] = {
    new JsonRpcCodec[JValue] {
      override type L = JValue

      override def decode(l: JValue): DecodeResult[JValue] = DecodeResult.Value(l)

      override def encode(h: JValue): JValue = h

      override def schema: Schema[JValue] = _schema
    }
  }
}
