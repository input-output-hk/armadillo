package io.iohk.armadillo.tapir

import io.iohk.armadillo.*
import io.iohk.armadillo.server.ServerInterpreter.{InterpretationError, Result}
import io.iohk.armadillo.server.{JsonSupport, ServerInterpreter}
import sttp.monad.MonadError
import sttp.monad.syntax.*
import sttp.tapir.Codec.JsonCodec
import sttp.tapir.EndpointIO.Info
import sttp.tapir.SchemaType.SCoproduct
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.{CodecFormat, DecodeResult, EndpointIO, RawBodyType, Schema}

import java.nio.charset.StandardCharsets

class TapirInterpreter[F[_], Raw](serverInterpreter: ServerInterpreter[F, Raw], jsonSupport: JsonSupport[Raw])(implicit
    monadError: MonadError[F]
) {

  def toTapirEndpoint: ServerEndpoint.Full[Unit, Unit, String, Unit, Raw, Any, F] = {
    sttp.tapir.endpoint.post
      .in(
        EndpointIO.Body(
          RawBodyType.StringBody(StandardCharsets.UTF_8),
          idJsonCodec,
          Info.empty
        )
      )
      .errorOut(sttp.tapir.statusCode(sttp.model.StatusCode.Ok))
      .out(EndpointIO.Body(RawBodyType.StringBody(StandardCharsets.UTF_8), jsonSupport.outRawCodec, Info.empty))
      .serverLogic[F] { input =>
        serverInterpreter
          .dispatchRequest(input)
          .map {
            case Result.RequestResponse(r) => Right(r)
            case Result.Notification()     => Left(())
          }
      }
  }
  private val idJsonCodec: JsonCodec[String] = new JsonCodec[String] {
    override def rawDecode(l: String): DecodeResult[String] = DecodeResult.Value(l)

    override def encode(h: String): String = h

    override def schema: Schema[String] = Schema[String](
      SCoproduct(Nil, None)(_ => None),
      None
    )

    override def format: CodecFormat.Json = CodecFormat.Json()
  }
}

object TapirInterpreter {
  def apply[F[_]: MonadError, Raw](
      jsonRpcEndpoints: List[JsonRpcServerEndpoint[F]],
      jsonSupport: JsonSupport[Raw]
  ): Either[InterpretationError, TapirInterpreter[F, Raw]] = {
    ServerInterpreter[F, Raw](jsonRpcEndpoints, jsonSupport).map(new TapirInterpreter(_, jsonSupport))
  }
}
