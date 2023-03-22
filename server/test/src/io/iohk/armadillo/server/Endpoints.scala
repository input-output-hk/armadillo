package io.iohk.armadillo.server

import io.circe.generic.auto._
import io.iohk.armadillo._
import io.iohk.armadillo.json.json4s.Json4sSupport
import io.iohk.armadillo.server.Endpoints._
import org.json4s.{CustomSerializer, Extraction, Formats, JArray, JField, JInt, JNothing, JNull, JObject, JString, JValue, Serialization}
import sttp.tapir.generic.auto._
import sttp.tapir.{Schema, ValidationResult, Validator}
trait CirceEndpoints extends Endpoints {
  import io.iohk.armadillo.json.circe

  override implicit def intCodec: JsonRpcCodec[Int] = circe.jsonRpcCodec
  override implicit def stringCodec: JsonRpcCodec[String] = circe.jsonRpcCodec
  override implicit def stringIntCodec: JsonRpcCodec[(String, Int)] = circe.jsonRpcCodec
  override implicit def intStringCodec: JsonRpcCodec[(Int, String)] = circe.jsonRpcCodec
  override implicit def entityCodec(implicit schema: Schema[Entity]): JsonRpcCodec[Entity] = circe.jsonRpcCodec
  override implicit def intErrorCodec: JsonRpcCodec[JsonRpcError[Int]] = circe.jsonRpcCodec
  override implicit def smallCodec: JsonRpcCodec[ErrorInfoSmall] = circe.jsonRpcCodec
  override implicit def bigCodec: JsonRpcCodec[ErrorInfoBig] = circe.jsonRpcCodec
  override implicit def optionStringCodec: JsonRpcCodec[Option[String]] = circe.jsonRpcCodec
  override implicit def optionIntCodec: JsonRpcCodec[Option[Int]] = circe.jsonRpcCodec
}

trait Json4sEndpoints extends Endpoints {
  import io.iohk.armadillo.json.json4s

  implicit val serialization: Serialization
  implicit val formats: Formats
  implicit val jsonSupport: Json4sSupport

  override implicit def intCodec: JsonRpcCodec[Int] = json4s.jsonRpcCodec
  override implicit def stringCodec: JsonRpcCodec[String] = json4s.jsonRpcCodec
  override implicit def stringIntCodec: JsonRpcCodec[(String, Int)] = json4s.jsonRpcCodec
  override implicit def intStringCodec: JsonRpcCodec[(Int, String)] = json4s.jsonRpcCodec
  override implicit def entityCodec(implicit schema: Schema[Entity]): JsonRpcCodec[Entity] = json4s.jsonRpcCodec
  override implicit def intErrorCodec: JsonRpcCodec[JsonRpcError[Int]] = json4s.jsonRpcCodec
  override implicit def smallCodec: JsonRpcCodec[ErrorInfoSmall] = json4s.jsonRpcCodec
  override implicit def bigCodec: JsonRpcCodec[ErrorInfoBig] = json4s.jsonRpcCodec
  override implicit def optionStringCodec: JsonRpcCodec[Option[String]] = json4s.jsonRpcCodec
  override implicit def optionIntCodec: JsonRpcCodec[Option[Int]] = json4s.jsonRpcCodec
}

trait Endpoints {
  implicit def intCodec: JsonRpcCodec[Int]
  implicit def stringCodec: JsonRpcCodec[String]
  implicit def stringIntCodec: JsonRpcCodec[(String, Int)]
  implicit def intStringCodec: JsonRpcCodec[(Int, String)]
  implicit def entityCodec(implicit schema: Schema[Entity]): JsonRpcCodec[Entity]
  implicit def intErrorCodec: JsonRpcCodec[JsonRpcError[Int]]
  implicit def smallCodec: JsonRpcCodec[ErrorInfoSmall]
  implicit def bigCodec: JsonRpcCodec[ErrorInfoBig]
  implicit def optionStringCodec: JsonRpcCodec[Option[String]]
  implicit def optionIntCodec: JsonRpcCodec[Option[Int]]

  val hello_in_int_out_string: JsonRpcEndpoint[Int, Unit, String] = jsonRpcEndpoint(m"hello")
    .in(param[Int]("param1"))
    .out[String]("response")

  val hello_in_int_out_string_by_name: JsonRpcEndpoint[Int, Unit, String] = jsonRpcEndpoint(m"hello", ParamStructure.ByName)
    .in(param[Int]("param1"))
    .out[String]("response")

  val hello_in_int_out_string_by_position: JsonRpcEndpoint[Int, Unit, String] =
    jsonRpcEndpoint(m"hello", ParamStructure.ByPosition)
      .in(param[Int]("param1"))
      .out[String]("response")

  val hello_in_int_out_string_validated: JsonRpcEndpoint[Int, Unit, String] =
    jsonRpcEndpoint(m"hello", ParamStructure.Either)
      .in(param[Int]("param1").validate(Validator.min(0)))
      .out[String]("response")

  val hello_in_multiple_int_out_string: JsonRpcEndpoint[(Int, Int), Unit, String] = jsonRpcEndpoint(m"hello")
    .in(param[Int]("param1").and(param[Int]("param2")))
    .out[String]("response")

  val hello_in_multiple_validated: JsonRpcEndpoint[(Int, Int), Unit, String] = jsonRpcEndpoint(m"hello")
    .in(
      param[Int]("param1")
        .validate(Validator.negative[Int].and(Validator.min(-10)))
        .and(
          param[Int]("param2")
            .validate(Validator.inRange(10, 20))
        )
    )
    .out[String]("response")

  val hello_with_validated_product: JsonRpcEndpoint[(Int, String), Unit, String] = jsonRpcEndpoint(m"hello")
    .in(param[(Int, String)]("param1").validate(Validator.max(10).contramap(_._1)))
    .out[String]("response")

  val hello_with_validated_coproduct: JsonRpcEndpoint[Entity, Unit, String] = jsonRpcEndpoint(m"hello")
    .in(param[Entity]("param1").validate(Validator.max(10).contramap(_.id)))
    .out[String]("response")

  val hello_with_validated_branch_of_coproduct: JsonRpcEndpoint[Entity, Unit, String] = {
    implicit val schemaForPerson: Schema[Person] =
      Schema.derived[Person].validate(Validator.custom(p => ValidationResult.validWhen(p.name.nonEmpty)))
    jsonRpcEndpoint(m"hello")
      .in(param[Entity]("param1"))
      .out[String]("response")
  }

  val echo_with_optional_params: JsonRpcEndpoint[(Option[String], Option[String], String), Unit, String] = jsonRpcEndpoint(m"echo")
    .in(param[Option[String]]("first").and(param[Option[String]]("second")).and(param[String]("third")))
    .out[String]("echoed")

  val empty: JsonRpcEndpoint[Unit, Unit, Unit] = jsonRpcEndpoint(m"empty")

  val error_no_data: JsonRpcEndpoint[Unit, JsonRpcError.NoData, Unit] = jsonRpcEndpoint(m"error_no_data")
    .errorOut(errorNoData)

  val error_with_data: JsonRpcEndpoint[Unit, JsonRpcError[Int], Unit] = jsonRpcEndpoint(m"error_with_data")
    .errorOut(errorWithData[Int])

  val fixed_error: JsonRpcEndpoint[Unit, Unit, Unit] = jsonRpcEndpoint(m"fixed_error")
    .errorOut(fixedError(200, "something went wrong"))

  val fixed_error_with_data: JsonRpcEndpoint[Unit, String, Unit] = jsonRpcEndpoint(m"fixed_error_with_data")
    .errorOut(fixedErrorWithData[String](200, "something went wrong"))

  val oneOf_fixed_errors_with_data: JsonRpcEndpoint[Unit, ErrorInfo, Unit] = jsonRpcEndpoint(m"fixed_error")
    .errorOut(
      oneOf[ErrorInfo](
        oneOfVariant(fixedErrorWithData[ErrorInfoSmall](201, "something went really wrong")),
        oneOfVariant(fixedErrorWithData[ErrorInfoBig](200, "something went wrong"))
      )
    )
  val oneOf_fixed_errors_value_matcher: JsonRpcEndpoint[Unit, Either[Unit, Unit], Unit] = jsonRpcEndpoint(m"fixed_error")
    .errorOut(
      oneOf(
        oneOfVariantValueMatcher[Either[Unit, Unit]](fixedError(201, "something went really wrong")) { case Left(_) => true },
        oneOfVariantValueMatcher[Either[Unit, Unit]](fixedError(200, "something went wrong")) { case Right(_) => true }
      )
    )

  val e1_int_string_out_int: JsonRpcEndpoint[String, Unit, Int] = jsonRpcEndpoint(m"e1")
    .in(param[String]("param1"))
    .out[Int]("response")

  val optional_input: JsonRpcEndpoint[(Option[String], Int), Unit, String] = jsonRpcEndpoint(m"optional_input")
    .in(
      param[Option[String]]("p1").and(param[Int]("p2"))
    )
    .out[String]("response")

  val optional_input_last: JsonRpcEndpoint[(String, Option[Int]), Unit, String] = jsonRpcEndpoint(m"optional_input_last")
    .in(
      param[String]("p1").and(param[Option[Int]]("p2"))
    )
    .out[String]("response")

  val optional_output: JsonRpcEndpoint[Unit, Unit, Option[String]] = jsonRpcEndpoint(m"optional_output")
    .out[Option[String]]("response")

  val output_without_params: JsonRpcEndpoint[Unit, Unit, String] = jsonRpcEndpoint(m"output_without_params")
    .out[String]("response")
}

object Endpoints {

  sealed trait ErrorInfo
  case class ErrorInfoSmall(msg: String) extends ErrorInfo
  case class ErrorInfoBig(msg: String, code: Int) extends ErrorInfo

  sealed trait Entity {
    def id: Int
  }
  final case class Person(name: String, id: Int) extends Entity

  object EntitySerializer
      extends CustomSerializer[Entity](implicit formats =>
        (
          Function.unlift((_: JValue).extractOpt[Person]).compose { case JObject(JField("Person", v) :: Nil) => v },
          { case person: Person => JObject("Person" -> Extraction.decompose(person)) }
        )
      )

  object IntStringPairSerializer
      extends CustomSerializer[(Int, String)](_ =>
        ({ case JArray(JInt(int) :: JString(str) :: Nil) => (int.toInt, str) }, PartialFunction.empty)
      )

  object StrictStringSerializer extends CustomSerializer[String](_ => ({ case JString(str) => str }, { case str: String => JString(str) }))

  object NoneSerializer extends CustomSerializer[None.type](_ => ({ case JNothing | JNull => None }, { case None => JNull }))
}
