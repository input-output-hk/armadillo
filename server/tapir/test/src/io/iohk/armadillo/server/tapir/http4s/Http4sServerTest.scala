package io.iohk.armadillo.server.tapir.http4s

import cats.effect.IO
import io.iohk.armadillo.server.AbstractServerSuite
import sttp.client3.StringBody
import sttp.tapir.server.ServerEndpoint

object Http4sServerTest extends BaseSuite with AbstractServerSuite[StringBody, ServerEndpoint[Any, IO]]
