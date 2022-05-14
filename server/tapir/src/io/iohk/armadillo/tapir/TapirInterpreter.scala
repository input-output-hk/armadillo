package io.iohk.armadillo.tapir

import io.iohk.armadillo._
import io.iohk.armadillo.server.ServerInterpreter.InterpretationError
import io.iohk.armadillo.server.{CustomInterceptors, Interceptor, JsonSupport, ServerInterpreter}
import sttp.monad.MonadError
import sttp.monad.syntax._
import sttp.tapir.Codec.JsonCodec
import sttp.tapir.EndpointIO.Info
import sttp.tapir.SchemaType.SCoproduct
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.{CodecFormat, DecodeResult, EndpointIO, RawBodyType, Schema}

import java.nio.charset.StandardCharsets

class TapirInterpreter[F[_], Raw](
    jsonSupport: JsonSupport[Raw],
    interceptors: List[Interceptor[F, Raw]] = CustomInterceptors[F, Raw]().interceptors
)(implicit
    monadError: MonadError[F]
) {

  def toTapirEndpoint(
      jsonRpcEndpoints: List[JsonRpcServerEndpoint[F]]
  ): Either[InterpretationError, ServerEndpoint.Full[Unit, Unit, String, Unit, Raw, Any, F]] = {
    ServerInterpreter[F, Raw](jsonRpcEndpoints, jsonSupport, interceptors).map(toTapirEndpointUnsafe)
  }

  private def toTapirEndpointUnsafe(serverInterpreter: ServerInterpreter[F, Raw]) = {
    sttp.tapir.endpoint.post
      .in(
        EndpointIO.Body(
          RawBodyType.StringBody(StandardCharsets.UTF_8),
          idJsonCodec,
          Info.empty
        )
      )
      .errorOut(sttp.tapir.statusCode(sttp.model.StatusCode.Ok))
      .out(EndpointIO.Body(RawBodyType.StringBody(StandardCharsets.UTF_8), outRawCodec, Info.empty))
      .serverLogic[F] { input =>
        serverInterpreter
          .dispatchRequest(input)
          .map {
            case Some(r) => Right(r)
            case None    => Left(())
          }
      }
  }

  private val outRawCodec: JsonCodec[Raw] = new JsonCodec[Raw] {
    override def rawDecode(l: String): DecodeResult[Raw] = jsonSupport.parse(l).map(jsonSupport.demateralize)

    override def encode(h: Raw): String = jsonSupport.stringify(h)

    override def schema: Schema[Raw] = Schema(
      SCoproduct(Nil, None)(_ => None),
      None
    )
    override def format: CodecFormat.Json = CodecFormat.Json()
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
