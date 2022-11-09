package io.iohk.armadillo.openrpc

import io.circe.generic.auto._
import io.circe.{Encoder, Json}
import io.iohk.armadillo._
import io.iohk.armadillo.json.circe._
import sttp.tapir.Schema.derivedEnumeration
import sttp.tapir.generic.auto._
import sttp.tapir.{Schema, SchemaType, ValidationResult, Validator}

object Basic {
  val basic: JsonRpcEndpoint[Int, Unit, String] = jsonRpcEndpoint(m"hello")
    .in(param[Int]("param1"))
    .out[String]("response")

  val basicWithSingleExample: JsonRpcEndpoint[(Int, Option[String]), Unit, String] = jsonRpcEndpoint(m"hello")
    .in(param[Int]("param1").example(42).and(param[Option[String]]("param2").example(Some("test"))))
    .out(result[String]("response").example("ok"))

  val basicWithMultipleExamples: JsonRpcEndpoint[(Int, Option[String]), Unit, String] = jsonRpcEndpoint(m"hello")
    .in(param[Int]("param1").examples(Set(42, 43)).and(param[Option[String]]("param2").examples(Set(Some("test"), None))))
    .out(result[String]("response").examples(Set("ok", "ko")))

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

  case class Pet(name: String)

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

  case class Problem()

  object Problem {
    implicit val schema: Schema[Problem] =
      Schema[Problem](
        SchemaType.SRef(
          Schema.SName("https://example.com/models/model.yaml#/Problem")
        )
      )
  }

  val external_ref: JsonRpcEndpoint[Problem, Unit, Unit] = jsonRpcEndpoint(m"ext")
    .in(param[Problem]("problem"))

  val productArray: JsonRpcEndpoint[List[Pet], Unit, Unit] = jsonRpcEndpoint(m"createPet")
    .in(param[List[Pet]]("pet"))

  case class Country(name: String)
  case class Author(name: String, country: Country)
  case class Genre(name: String, description: String)
  case class Book(title: String, genre: Genre, year: Int, author: Author)

  val nestedProducts: JsonRpcEndpoint[Book, Unit, Unit] = jsonRpcEndpoint(m"createPet")
    .in(param[Book]("book"))

  val productDuplicatedNames: JsonRpcEndpoint[(Pet, openrpc.Pet), Unit, Unit] = jsonRpcEndpoint(m"createPet")
    .in(param[Pet]("param1") and param[io.iohk.armadillo.openrpc.Pet]("param2"))

  case class F1(data: List[F1])

  val recursiveProduct: JsonRpcEndpoint[F1, Unit, Unit] = jsonRpcEndpoint(m"createPet")
    .in(param[F1]("p1"))

  case class G[T](data: T)

  val genericProduct: JsonRpcEndpoint[(G[String], G[Int]), Unit, Unit] = jsonRpcEndpoint(m"createPet")
    .in(param[G[String]]("param1") and param[G[Int]]("param2"))

  val optionalResultProduct: JsonRpcEndpoint[Unit, Unit, Option[Pet]] = jsonRpcEndpoint(m"getPet")
    .out(result[Option[Pet]]("pet"))

  val resultProduct: JsonRpcEndpoint[Unit, Unit, Pet] = jsonRpcEndpoint(m"getPet")
    .out(result[Pet]("pet"))

  case class F2(data: List[F2])

  val optionalRecursiveResult: JsonRpcEndpoint[Unit, Unit, Option[F2]] = jsonRpcEndpoint(m"createPet")
    .out(result[Option[F2]]("p1"))

  case class F3(data: Option[F3])

  val arrayOfRecursiveOptionalResult: JsonRpcEndpoint[Unit, Unit, List[F3]] = jsonRpcEndpoint(m"createPet")
    .out(result[List[F3]]("p1"))

  val singleFixedError: JsonRpcEndpoint[Int, Unit, String] = jsonRpcEndpoint(m"hello")
    .in(param[Int]("param1"))
    .errorOut(fixedError[Unit](100, "My fixed error"))
    .out[String]("response")

  val singleFixedErrorWithData: JsonRpcEndpoint[Int, String, String] = jsonRpcEndpoint(m"hello")
    .in(param[Int]("param1"))
    .errorOut(fixedErrorWithData[String](100, "My fixed error"))
    .out[String]("response")

  val oneOfFixedErrors: JsonRpcEndpoint[Unit, Either[Unit, Unit], Int] = jsonRpcEndpoint(m"oneOf")
    .errorOut(
      oneOf[Either[Unit, Unit]](
        oneOfVariantValueMatcher(fixedError(201, "error1")) { case Left(_) => true },
        oneOfVariantValueMatcher(fixedError(202, "error2")) { case Right(_) => true }
      )
    )
    .out(result[Int]("p1"))

  val oneOfFixedErrorsWithData: JsonRpcEndpoint[Unit, ErrorInfo, Int] = jsonRpcEndpoint(m"oneOf")
    .errorOut(oneOf(oneOfVariant(fixedErrorWithData[ErrorInfo](201, "error1")), oneOfVariant(fixedErrorWithData[ErrorInfo](202, "error2"))))
    .out(result[Int]("p1"))

  case class ErrorInfo(bugId: Int)

  val sum: JsonRpcEndpoint[Animal, Unit, Unit] = jsonRpcEndpoint(m"createPet")
    .in(param[Animal]("animal"))

  val validatedInts = jsonRpcEndpoint(m"getPetByNumber")
    .in(
      param[Int]("number1").validate(Validator.min(1)) and
        param[Int]("number2").validate(Validator.max(10))
    )

  val validatedIntsWithExclusives = jsonRpcEndpoint(m"getPetByNumber")
    .in(
      param[Int]("number1").validate(Validator.min(1, exclusive = true)) and
        param[Int]("number2").validate(Validator.max(10, exclusive = true))
    )

  val validatedStrings = jsonRpcEndpoint(m"getPetByNumber")
    .in(
      param[String]("string1").validate(Validator.minLength(1)) and
        param[String]("string2").validate(Validator.maxLength(10)) and
        param[String]("string3").validate(Validator.pattern("\\w+"))
    )

  val validatedArrays = jsonRpcEndpoint(m"getPetByNumber")
    .in(
      param[Seq[String]]("array1").validate(Validator.minSize(1)) and
        param[Seq[String]]("array2").validate(Validator.maxSize(10))
    )

  val validatedStringEnumeration = jsonRpcEndpoint(m"getPetByNumber")
    .in(param[String]("enum").validate(Validator.enumeration(List("opt1", "opt2", "opt3"))))

  sealed trait Enum
  final case object Val1 extends Enum
  final case object Val2 extends Enum
  val validatedEnumeration = jsonRpcEndpoint(m"getPetByNumber")
    .in(
      param[Enum]("enum")(jsonRpcCodec(implicitly, implicitly, derivedEnumeration[Enum]())).validate(
        Validator.enumeration(List(Val1, Val2))
      )
    )

  val validatedAll = jsonRpcEndpoint(m"getPetByNumber")
    .in(
      param[String]("parameter").validate(
        Validator.all(
          Validator.minLength(1),
          Validator.maxLength(10),
          Validator.pattern("\\w+")
        )
      )
    )

  val validatedCustom = jsonRpcEndpoint(m"getPetByNumber")
    .in(
      param[String]("parameter").validate(
        Validator.custom(_ => ValidationResult.Valid, Some("Value needs to be correct"))
      )
    )

  val validatedMapped: JsonRpcEndpoint[Int, Unit, Unit] = {
    implicit val numberFromString: Schema[Int] = sttp.tapir.Schema.schemaForString
      .validate(Validator.maxLength(10))
      .validate(Validator.pattern("^[0-9]$"))
      .map(_.toIntOption)(_.toString)
      .name(Schema.SName("NumberFromString"))
    jsonRpcEndpoint(m"methodWithNumberFromString")
      .in(param[Int]("numberFromString"))
  }

  final case class Human(name: String, nickname: String)
  object Human {
    implicit val humanEncoder: Encoder[Human] = (human: Human) => Json.fromString(human.nickname)
  }

  final case class Data(bytes: Array[Byte]) extends AnyVal
  object Data {
    implicit val dataEncoder: Encoder[Data] = (data: Data) => Json.fromString("data: " + new String(data.bytes))
  }

  val customEncoder: JsonRpcEndpoint[(Human, Option[Data]), Unit, Boolean] = jsonRpcEndpoint(m"createHuman")
    .in(
      param[Human]("human")
        .example(Human("John", "Unknown"))
        .and(param[Option[Data]]("data").examples(Set(Some(Data("some_data".getBytes)), None)))
    )
    .out[Boolean]("result")
}
