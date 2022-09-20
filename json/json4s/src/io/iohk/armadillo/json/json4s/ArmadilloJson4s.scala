package io.iohk.armadillo.json.json4s

import io.iohk.armadillo._
import org.json4s.JsonAST.JValue
import org.json4s.{Extraction, Formats, Serialization}
import sttp.tapir.{DecodeResult, Schema}

import scala.util.{Failure, Success, Try}

trait ArmadilloJson4s {
  implicit def jsonRpcCodec[H: Schema](implicit formats: Formats, serialization: Serialization, manifest: Manifest[H]): JsonRpcCodec[H] =
    new JsonRpcCodec[H] {
      override type L = JValue

      override def encode(h: H): JValue = Extraction.decompose(h)

      override def schema: Schema[H] = implicitly[Schema[H]]

      override def decode(l: JValue): DecodeResult[H] = {
        Try(
          if (manifest.runtimeClass == classOf[Option[_]])
            l.extractOpt(implicitly, manifest.typeArguments.head).asInstanceOf[H]
          else
            l.extract[H]
        ) match {
          case Failure(exception) => DecodeResult.Error(l.toString, exception)
          case Success(value)     => DecodeResult.Value(value)
        }
      }
    }
}
