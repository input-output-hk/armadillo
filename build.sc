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
    ivy"com.softwaremill.sttp.tapir::tapir-core::${Version.Tapir}"
  )

  object test extends Tests with CommonTestModule
}

object circe extends CommonModule {
  override def moduleDeps = Seq(core, tapir)
  override def ivyDeps = Agg(
    ivy"com.softwaremill.sttp.tapir::tapir-json-circe:${Version.Tapir}"
  )

  object test extends Tests with CommonTestModule

}

object json4s extends CommonModule {
  override def moduleDeps = Seq(core, tapir)
  override def ivyDeps = Agg(
    ivy"com.softwaremill.sttp.tapir::tapir-json-json4s:${Version.Tapir}"
  )

  object test extends Tests with CommonTestModule

}

object tapir extends CommonModule {
  override def moduleDeps = Seq(core)
  override def ivyDeps = Agg(
    ivy"com.softwaremill.sttp.tapir::tapir-core::${Version.Tapir}"
  )

  object test extends Tests with CommonTestModule
}

object example extends CommonModule {
  override def moduleDeps = Seq(core, tapir, circe, json4s, trace4cats)

  override def ivyDeps =
    Agg(
      ivy"org.typelevel::cats-effect::3.3.5",
      ivy"org.http4s::http4s-dsl::${Version.Http4s}",
      ivy"org.http4s::http4s-circe::${Version.Http4s}",
      ivy"org.http4s::http4s-blaze-server::${Version.Http4s}",
      ivy"com.softwaremill.sttp.tapir::tapir-http4s-server::${Version.Tapir}",
      ivy"com.softwaremill.sttp.tapir::tapir-cats::${Version.Tapir}",
      ivy"org.json4s::json4s-core::${Version.Json4s}",
      ivy"org.json4s::json4s-jackson:${Version.Json4s}",
      ivy"io.janstenpickle::trace4cats-log-exporter::${Version.Trace4cats}",
      ivy"io.janstenpickle::trace4cats-avro-exporter::${Version.Trace4cats}",
      ivy"ch.qos.logback:logback-classic:1.2.7",
    )
}

object trace4cats extends CommonModule {
  override def moduleDeps = Seq(core)
  override def ivyDeps = Agg(
    ivy"io.janstenpickle::trace4cats-base::${Version.Trace4cats}",
    ivy"io.janstenpickle::trace4cats-core::${Version.Trace4cats}",
    ivy"io.janstenpickle::trace4cats-inject::${Version.Trace4cats}",
    ivy"com.softwaremill.sttp.tapir::tapir-cats::${Version.Tapir}"
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

object Version {
  val Trace4cats = "0.12.0"
  val Tapir = "0.20.0"
  val Http4s = "0.23.10"
  val Json4s = "4.0.4"
}