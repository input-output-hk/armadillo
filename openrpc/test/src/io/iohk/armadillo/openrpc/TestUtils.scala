package io.iohk.armadillo.openrpc

import cats.effect.{IO, Resource}

import scala.io.Source

object TestUtils {
  private[openrpc] def load(fileName: String): Resource[IO, String] = {
    Resource
      .make(
        IO.blocking(
          Source
            .fromInputStream(classOf[VerifyYamlTest.type].getResourceAsStream(s"/$fileName"))
        )
      )(source => IO.delay(source.close()))
      .map(_.getLines().mkString("\n"))
      .map(noIndentation)
  }
  private[openrpc] def noIndentation(s: String): String = s.trim
}
