import $ivy.`com.goyeau::mill-scalafix_mill0.9:0.2.8`
import $ivy.`de.tototec::de.tobiasroeser.mill.vcs.version_mill0.9:0.1.1`
import $ivy.`io.github.davidgregory084::mill-tpolecat:0.2.0`
import com.goyeau.mill.scalafix.ScalafixModule
import io.github.davidgregory084.TpolecatModule
import mill._
import mill.scalalib._
import mill.scalalib.scalafmt.ScalafmtModule

object core extends CommonModule {
  override def ivyDeps = Agg(
    ivy"com.softwaremill.sttp.tapir::tapir-core::0.20.0"
  )

  object test extends Tests with CommonTestModule
}

object circe extends CommonModule {
  override def moduleDeps = Seq(core, tapir)
  override def ivyDeps = Agg(
    ivy"com.softwaremill.sttp.tapir::tapir-json-circe:0.20.0"
  )

  object test extends Tests with CommonTestModule

}

object json4s extends CommonModule {
  override def moduleDeps = Seq(core, tapir)
  override def ivyDeps = Agg(
    ivy"com.softwaremill.sttp.tapir::tapir-json-json4s:0.20.0"
  )

  object test extends Tests with CommonTestModule

}

object tapir extends CommonModule {
  override def moduleDeps = Seq(core)
  override def ivyDeps = Agg(
    ivy"com.softwaremill.sttp.tapir::tapir-core::0.20.0"
  )

  object test extends Tests with CommonTestModule
}

object example extends CommonModule {
  override def moduleDeps = Seq(core, tapir, circe, json4s, trace4cats)

  override def ivyDeps = Agg(
    ivy"org.typelevel::cats-effect::3.3.5",
    ivy"org.http4s::http4s-dsl::0.23.10",
    ivy"org.http4s::http4s-circe::0.23.10",
    ivy"org.http4s::http4s-blaze-server::0.23.10",
    ivy"com.softwaremill.sttp.tapir::tapir-http4s-server::0.20.0",
    ivy"com.softwaremill.sttp.tapir::tapir-cats::0.20.0",
    ivy"org.json4s::json4s-core::4.0.4",
    ivy"org.json4s::json4s-jackson:4.0.4",
    ivy"io.janstenpickle::trace4cats-log-exporter::0.12.0",
  ivy"io.janstenpickle::trace4cats-avro-exporter::0.12.0",
    ivy"ch.qos.logback:logback-classic:1.2.7",
  )
}

object trace4cats extends CommonModule {
  override def moduleDeps = Seq(core)
  override def ivyDeps = Agg(
    ivy"io.janstenpickle::trace4cats-base::0.12.0",
    ivy"io.janstenpickle::trace4cats-core::0.12.0",
    ivy"io.janstenpickle::trace4cats-inject::0.12.0",
    ivy"com.softwaremill.sttp.tapir::tapir-cats::0.20.0"
  )
}

trait BaseModule extends ScalaModule with ScalafmtModule with TpolecatModule with ScalafixModule {
  override def scalacOptions = T {
    super.scalacOptions().filterNot(Set("-Xfatal-warnings")) ++ Seq(
      "-Ymacro-annotations"
    )
  }

}

trait CommonTestModule extends BaseModule with TestModule {
  override def ivyDeps = Agg(
    ivy"org.scalameta::munit::0.7.29"
  )
  override def testFramework = "munit.Framework"
}

trait CommonModule extends BaseModule {
  def scalaVersion = "2.13.8"

  override def scalacPluginIvyDeps = Agg(
    ivy"org.typelevel:::kind-projector:0.13.2"
  )
}
