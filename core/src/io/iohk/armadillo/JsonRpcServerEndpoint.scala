package io.iohk.armadillo

import sttp.monad.MonadError

abstract class JsonRpcServerEndpoint[F[_]] {
  type INPUT
  type ERROR_OUTPUT
  type OUTPUT

  def endpoint: JsonRpcEndpoint[INPUT, ERROR_OUTPUT, OUTPUT]
  def logic: MonadError[F] => INPUT => F[Either[JsonRpcError[ERROR_OUTPUT], OUTPUT]]
}

object JsonRpcServerEndpoint {

  /** The full type of a server endpoint, capturing the types of all input/output parameters. Most of the time, the simpler
    * `JsonRpcServerEndpoint[F]` can be used instead.
    */
  type Full[_INPUT, _ERROR_OUTPUT, _OUTPUT, F[_]] = JsonRpcServerEndpoint[F] {
    type INPUT = _INPUT
    type ERROR_OUTPUT = _ERROR_OUTPUT
    type OUTPUT = _OUTPUT
  }

  def apply[INPUT, ERROR_OUTPUT, OUTPUT, F[_]](
      endpoint: JsonRpcEndpoint[INPUT, ERROR_OUTPUT, OUTPUT],
      logic: MonadError[F] => INPUT => F[Either[JsonRpcError[ERROR_OUTPUT], OUTPUT]]
  ): JsonRpcServerEndpoint.Full[INPUT, ERROR_OUTPUT, OUTPUT, F] = {
    type _INPUT = INPUT
    type _ERROR_OUTPUT = ERROR_OUTPUT
    type _OUTPUT = OUTPUT
    val e = endpoint
    val f = logic
    new JsonRpcServerEndpoint[F] {
      override type INPUT = _INPUT
      override type ERROR_OUTPUT = _ERROR_OUTPUT
      override type OUTPUT = _OUTPUT

      override def endpoint: JsonRpcEndpoint[INPUT, ERROR_OUTPUT, OUTPUT] = e

      override def logic: MonadError[F] => INPUT => F[Either[JsonRpcError[ERROR_OUTPUT], OUTPUT]] = f
    }
  }
}
