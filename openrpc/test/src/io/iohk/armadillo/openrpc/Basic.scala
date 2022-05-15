package io.iohk.armadillo.openrpc

import io.iohk.armadillo.json.circe._
import io.iohk.armadillo._
import io.circe.generic.auto._
import sttp.tapir.{Schema, SchemaType}
import sttp.tapir.generic.auto._

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

  val optionalParam: JsonRpcEndpoint[Option[Int], Unit, String] = jsonRpcEndpoint(m"hello")
    .in(param[Option[Int]]("param1"))
    .out[String]("response")

  val empty: JsonRpcEndpoint[Unit, Unit, Unit] = jsonRpcEndpoint(m"empty")

  val product: JsonRpcEndpoint[Pet, Unit, Unit] = jsonRpcEndpoint(m"createPet")
    .in(param[Pet]("pet"))

  val optionalProduct: JsonRpcEndpoint[Option[Pet], Unit, Unit] = jsonRpcEndpoint(m"createPet")
    .in(param[Option[Pet]]("pet"))

  val product_with_meta: JsonRpcEndpoint[Pet, Unit, Unit] = {
    implicit val schema: Schema[Pet] = sttp.tapir.Schema
      .derived[Pet]
      .description("Schema description")
      .deprecated(true)
      .name(Schema.SName("CustomPetName"))
    jsonRpcEndpoint(m"createPet")
      .in(param[Pet]("pet"))
  }

  val external_ref: JsonRpcEndpoint[Problem, Unit, Unit] = jsonRpcEndpoint(m"ext")
    .in(param[Problem]("problem"))

  case class Pet(name: String)

  case class Problem()

  object Problem {
    implicit val schema: Schema[Problem] =
      Schema[Problem](
        SchemaType.SRef(
          Schema.SName("https://example.com/models/model.yaml#/Problem")
        )
      )
  }
}
