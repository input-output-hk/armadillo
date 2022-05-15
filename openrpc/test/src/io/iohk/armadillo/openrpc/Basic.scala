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

  val productArray: JsonRpcEndpoint[List[Pet], Unit, Unit] = jsonRpcEndpoint(m"createPet")
    .in(param[List[Pet]]("pet"))

  val nestedProducts: JsonRpcEndpoint[Book, Unit, Unit] = jsonRpcEndpoint(m"createPet")
    .in(param[Book]("book"))

  case class Pet(name: String)

  val productDuplicatedNames: JsonRpcEndpoint[(Pet, openrpc.Pet), Unit, Unit] = jsonRpcEndpoint(m"createPet")
    .in(param[Pet]("param1") and param[io.iohk.armadillo.openrpc.Pet]("param2"))

  case class F1(data: List[F1])

  val recursiveProduct: JsonRpcEndpoint[F1, Unit, Unit] = jsonRpcEndpoint(m"createPet")
    .in(param[F1]("p1"))

  case class Problem()

  object Problem {
    implicit val schema: Schema[Problem] =
      Schema[Problem](
        SchemaType.SRef(
          Schema.SName("https://example.com/models/model.yaml#/Problem")
        )
      )
  }

  case class Country(name: String)
  case class Author(name: String, country: Country)
  case class Genre(name: String, description: String)
  case class Book(title: String, genre: Genre, year: Int, author: Author)
}
