package io.iohk.armadillo.openrpc

import io.iohk.armadillo.json.circe._
import io.iohk.armadillo._

object Basic {
  val basic: JsonRpcEndpoint[Int, Unit, String] = jsonRpcEndpoint(m"hello")
    .in(param[Int]("param1"))
    .out[String]("response")

  val multiple_params: JsonRpcEndpoint[(Int, String), Unit, String] = jsonRpcEndpoint(m"hello")
    .in(param[Int]("param1") and param[String]("param2"))
    .out[String]("response")

  val withInfo: JsonRpcEndpoint[Int, Unit, String] = jsonRpcEndpoint(m"hello")
    .description("A verbose explanation of the method behavior")
    .summary("A short summary of what the method does")
    .tag(
      JsonRpcEndpointTag("The name of the tag")
        .summary("A short summary of the tag")
        .description("A verbose explanation for the tag")
        .externalDocs(
          JsonRpcEndpointExternalDocs("http://example.com")
            .description("A verbose explanation of the target documentation")
        )
    )
    .in(
      param[Int]("param1")
        .summary("A short summary of the content that is being described")
        .deprecated()
        .description("A verbose explanation of the content descriptor behavior")
    )
    .out(
      result[String]("response")
        .summary("A short summary of the content that is being described")
        .description("A verbose explanation of the content descriptor behavior")
    )
}
