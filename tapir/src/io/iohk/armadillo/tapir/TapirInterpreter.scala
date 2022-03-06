package io.iohk.armadillo.tapir

import io.iohk.armadillo.*
import io.iohk.armadillo.Armadillo.{JsonRpcRequest, JsonRpcResponse}
import io.iohk.armadillo.tapir.Utils.RichEndpointInput
import sttp.monad.MonadError
import sttp.monad.syntax.*
import sttp.tapir.Codec.JsonCodec
import sttp.tapir.EndpointIO.Info
import sttp.tapir.internal.ParamsAsVector
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.{CodecFormat, DecodeResult, EndpointIO, RawBodyType, Schema}

import java.nio.charset.StandardCharsets

trait Provider[W[_]] {
  def codec[T](bodyCodec: JsonCodec[T]): JsonCodec[W[T]]
}

class TapirInterpreter[F[_]](jsonSupport: JsonSupport)(implicit
    monadError: MonadError[F]
) {

  def apply(jsonRpcEndpoints: List[JsonRpcServerEndpoint[F]]): List[ServerEndpoint[Any, F]] = {
    val endpoint = sttp.tapir.endpoint.post
      .in(
        EndpointIO.Body(
          RawBodyType.StringBody(StandardCharsets.UTF_8),
          hackyCodec(jsonRpcEndpoints.map(_.endpoint), jsonSupport.requestCodec),
          Info.empty
        )
      )
      .out(EndpointIO.Body(RawBodyType.StringBody(StandardCharsets.UTF_8), jsonSupport.responseCodec, Info.empty))
      .errorOut(EndpointIO.Body(RawBodyType.StringBody(StandardCharsets.UTF_8), jsonSupport.responseCodec, Info.empty))
      .serverLogic[F] { envelop =>
        println("entered serverLogic")
        val matchedEndpoint = jsonRpcEndpoints.find(_.endpoint.methodName.value == envelop.method) match {
          case Some(value) => value
          case None        => throw new IllegalStateException("Cannot happen because filtering codec passes through only matching methods")
        }
        println(s"matchedEndpoint :$matchedEndpoint")
        val matchedBody = envelop.params.asInstanceOf[matchedEndpoint.I]
        matchedEndpoint.logic(monadError)(matchedBody).map {
          case Left(value) =>
//            val errorCodec = getCodec(matchedEndpoint.endpoint.error)
//            Left(JsonRpcResponse("2.0", jsonSupport.parse(errorCodec.encode(value)), 1))
            ???
          case Right(value) =>
            println(s"right response value: $value")
//            val outputCodec = getCodec(matchedEndpoint.endpoint.output)
//            Right(JsonRpcResponse("2.0", jsonSupport.parse(outputCodec.encode(value)), 1))
            ???
        }
      }
    List(endpoint)
  }

  private def hackyCodec(
      endpoints: List[JsonRpcEndpoint[_, _, _]],
      originalCodec: JsonCodec[JsonRpcRequest[jsonSupport.Raw]]
  ): JsonCodec[JsonRpcRequest[jsonSupport.Raw]] =
    new JsonCodec[JsonRpcRequest[jsonSupport.Raw]] {
      override def rawDecode(l: String): DecodeResult[JsonRpcRequest[jsonSupport.Raw]] = {
        println(s"entered raw decode ${l}")
        originalCodec.rawDecode(l) match {
          case failure: DecodeResult.Failure => failure
          case DecodeResult.Value(envelop) =>
            println(s"decoded envelop $envelop")
            endpoints.find(_.methodName.value == envelop.method) match {
              case Some(jsonRpcEndpoint) =>
                println("Found")
                val combinedDecoder = jsonSupport.combineDecode(jsonRpcEndpoint.input.asVectorOfBasicInputs.map(_.codec))
                val combinedDecodeResult = combinedDecoder.apply(envelop.params)
                combinedDecodeResult.map { paramsVector =>
                  val params = ParamsAsVector(paramsVector).asAny
                  JsonRpcRequest(
                    jsonrpc = envelop.jsonrpc,
                    method = envelop.method,
                    params = params.asInstanceOf[jsonSupport.Raw],
                    id = envelop.id
                  )
                }

//                val bodyCodec: JsonCodec[_] = getCodec(jsonRpcEndpoint.input)
//                val bodyAsString = jsonSupport.stringify(envelop.params)
//                println(s"bodyAsString: $bodyAsString")
//                val r = bodyCodec
//                  .rawDecode(bodyAsString)
//                  .map { decodedParams =>
//                    println("decoded")
//                    JsonRpcRequest(
//                      jsonrpc = envelop.jsonrpc,
//                      method = envelop.method,
//                      params = decodedParams.asInstanceOf[jsonSupport.Raw],
//                      id = envelop.id
//                    )
//                  }
//                println(r)
//                r
              case None =>
                println("Not Found")
                DecodeResult.Mismatch(endpoints.map(_.methodName).mkString(","), envelop.method)
            }
        }
      }

      override def encode(h: JsonRpcRequest[jsonSupport.Raw]): String = originalCodec.encode(h)

      override def schema: Schema[JsonRpcRequest[jsonSupport.Raw]] = originalCodec.schema

      override def format: CodecFormat.Json = originalCodec.format
    }

//  private def getCodec[O](jsonRpcEndpointOutput: JsonRpcOutput[O]): JsonCodec[O] = {
//    println("getCodecOut")
//    val bodyCodec: JsonCodec[O] = jsonRpcEndpointOutput match {
//      case o: JsonRpcIO.Empty[O]  => ???
//      case o: JsonRpcIO.Single[O] => o.codec
//      case JsonRpcOutput.Pair(left, right, combine, split) =>
//        List(left, right).collectFirst { case o: JsonRpcIO.Single[O] => o.codec }.getOrElse(???).asInstanceOf[JsonCodec[O]]
//    }
//    println("gotCodecOut")
//    bodyCodec
//  }

//  private def getCodec[I](jsonRpcEndpointInput: JsonRpcInput[I]): JsonCodec[I] = {
//    println("getCodecIn")
//    val bodyCodec = jsonRpcEndpointInput match {
//      case o: JsonRpcIO.Empty[I] =>
//        println("empty")
//        ???
//      case o: JsonRpcIO.Single[I] =>
//        o.codec
//      case JsonRpcInput.Pair(left, right, combine, split) =>
//        println("pair")
//        List(left, right).collectFirst { case o: JsonRpcIO.Single[I] => o.codec }.getOrElse(???).asInstanceOf[JsonCodec[I]]
//    }
//    println("gotCodecIn")
//    bodyCodec
//  }
}

object TapirInterpreter {}
