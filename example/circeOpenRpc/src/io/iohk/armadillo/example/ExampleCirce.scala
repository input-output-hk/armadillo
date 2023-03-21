package io.iohk.armadillo.example

import cats.effect.{ExitCode, IO, IOApp}
import io.circe.generic.semiauto._
import io.circe.literal._
import io.circe.syntax.EncoderOps
import io.circe.{Decoder, Encoder}
import io.iohk.armadillo._
import io.iohk.armadillo.json.circe._
import io.iohk.armadillo.openrpc.OpenRpcDocsInterpreter
import io.iohk.armadillo.openrpc.circe.ArmadilloOpenRpcCirce
import io.iohk.armadillo.openrpc.circe.yaml.RichOpenRpcDocument
import io.iohk.armadillo.openrpc.model.OpenRpcInfo
import sttp.apispec.AnySchema
import sttp.tapir.{Schema, Validator}

object ExampleCirce extends IOApp {

  case class DescriptionResponse(msg: String)

  implicit val rpcBlockResponseEncoder: Encoder[DescriptionResponse] = deriveEncoder
  implicit val rpcBlockResponseDecoder: Decoder[DescriptionResponse] = deriveDecoder
  implicit val rpcBlockResponseSchema: Schema[DescriptionResponse] = Schema.derived

  // Note that only the name is required. Validations, description and example(s) are optionals.
  private val nameParam = param[String]("name")
    .description("Your name.")
    .examples(Set("John Doe", "Jane Doe")) // Multiple examples can be provided
    .validate(Validator.minLength(1).and(Validator.maxLength(100)))

  private val ageParam = param[Int]("age")
    .example(42)
    .description("Your age.")
    .validate(Validator.positive)

  private val descriptionResult = result[DescriptionResponse]("description")
    .description("A personalized greeting message.")
    .examples(
      Set(
        DescriptionResponse("Hello John Doe, you are 42 years old"),
        DescriptionResponse("Hello Jane Doe, you are 42 years old")
      )
    )

  val describeMeEndpoint: JsonRpcServerEndpoint[IO] = jsonRpcEndpoint(m"describe_me")
    .description("Returns a description of a person based on a name and an age.") // An endpoint can also have a description
    .summary("A short summary.")
    .in(nameParam.and(ageParam))
    .out(descriptionResult)
    .serverLogic[IO] { case (name, age) =>
      IO(Right(DescriptionResponse(s"Hello $name, you are $age years old")))
    }

  case object CustomArmadilloOpenRpcCirce extends ArmadilloOpenRpcCirce {
    override val anyObjectEncoding: AnySchema.Encoding = AnySchema.Encoding.Boolean

    override def openApi30: Boolean = true
  }

  override def run(args: List[String]): IO[ExitCode] = {
    val info: OpenRpcInfo = OpenRpcInfo("1.0.0", "Describe me!")
    val document = OpenRpcDocsInterpreter().toOpenRpc(info, List(describeMeEndpoint.endpoint))

    // Render the OpenRpcDocument in yaml and json.
    // Please note the import used, that will allow to render the specification in different version of OpenAPI
    for {
      _ <- displayStepMessage("Json - Recent version of OpenAPI")
      _ <- {
        import io.iohk.armadillo.openrpc.circe._
        IO.println(document.asJson)
      }

      _ <- displayStepMessage("Json - Version 3.0 of OpenAPI")
      _ <- {
        import CustomArmadilloOpenRpcCirce._
        IO.println(document.asJson)
      }

      _ <- displayStepMessage("Yaml - Recent version of OpenAPI")
      _ <- {
        import io.iohk.armadillo.openrpc.circe._
        IO.println(document.toYaml)
      }

      _ <- displayStepMessage("Yaml - Version 3.0 of OpenAPI")
      _ <- {
        import CustomArmadilloOpenRpcCirce._
        IO.println(document.toYaml)
      }
    } yield ExitCode.Success
  }

  private def displayStepMessage(step: String): IO[Unit] = {
    val separator = IO.println("_" * 50)
    separator >> IO.println(step) >> separator
  }
}
