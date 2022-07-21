import $ivy.`com.goyeau::mill-scalafix_mill0.10:0.2.9`
import $ivy.`de.tototec::de.tobiasroeser.mill.vcs.version_mill0.10:0.1.4`
import $ivy.`io.github.davidgregory084::mill-tpolecat_mill0.10:0.3.0`
import com.goyeau.mill.scalafix.ScalafixModule
import de.tobiasroeser.mill.vcs.version.VcsVersion
import io.github.davidgregory084.TpolecatModule
import mill._
import mill.scalalib._
import mill.scalalib.bsp.ScalaMetalsSupport
import mill.scalalib.publish.{Developer, License, PomSettings, VersionControl}
import mill.scalalib.scalafmt.ScalafmtModule

object core extends CommonModule with ArmadilloPublishModule {
  override def ivyDeps = Agg(
    ivy"com.softwaremill.sttp.tapir::tapir-core::${Version.Tapir}"
  )

  object test extends Tests with CommonTestModule
}

object json extends CommonModule {
  object circe extends CommonModule with ArmadilloPublishModule {
    override def moduleDeps = Seq(core, server)
    override def ivyDeps = Agg(
      ivy"com.softwaremill.sttp.tapir::tapir-json-circe:${Version.Tapir}"
    )

    object test extends Tests with CommonTestModule

  }
  object json4s extends CommonModule with ArmadilloPublishModule {
    override def moduleDeps = Seq(core, server)
    override def ivyDeps = Agg(
      ivy"com.softwaremill.sttp.tapir::tapir-json-json4s:${Version.Tapir}"
    )

    object test extends Tests with CommonTestModule
  }
}

object openrpc extends CommonModule with ArmadilloPublishModule {
  object model extends CommonModule with ArmadilloPublishModule {
    override def ivyDeps = Agg(ivy"com.softwaremill.sttp.tapir::tapir-apispec-model::${Version.Tapir}")
  }
  object circe extends CommonModule with ArmadilloPublishModule {
    override def moduleDeps = Seq(model)
    override def ivyDeps = Agg(
      ivy"io.circe::circe-core::${Version.Circe}",
      ivy"io.circe::circe-parser::${Version.Circe}",
      ivy"io.circe::circe-generic::${Version.Circe}"
    )
  }
  object circeYaml extends CommonModule with ArmadilloPublishModule {
    override def moduleDeps = Seq(circe)

    override def ivyDeps = Agg(ivy"io.circe::circe-yaml::${Version.Circe}")
  }

  override def moduleDeps = Seq(core, circeYaml)

  object test extends Tests with CommonTestModule {
    override def moduleDeps = Seq(openrpc, json.circe)
    override def ivyDeps = Agg(
      WeaverDep,
      ivy"org.typelevel::cats-effect::3.2.9"
    )
  }
}

object server extends CommonModule with ArmadilloPublishModule {
  override def moduleDeps = Seq(core)
  override def ivyDeps = Agg(
    ivy"com.softwaremill.sttp.tapir::tapir-cats::${Version.Tapir}"
  )

  object tapir extends CommonModule with ArmadilloPublishModule {
    override def moduleDeps = Seq(core, server)
    override def ivyDeps = Agg(
      ivy"com.softwaremill.sttp.tapir::tapir-core::${Version.Tapir}"
    )

    object test extends Tests with CommonTestModule {
      override def moduleDeps = Seq(core, json.circe, tapir, server.test)
      override def ivyDeps = Agg(
        WeaverDep,
        ivy"com.softwaremill.sttp.tapir::tapir-http4s-server::${Version.Tapir}",
        ivy"com.softwaremill.sttp.tapir::tapir-cats::${Version.Tapir}",
        ivy"com.softwaremill.sttp.tapir::tapir-sttp-client::${Version.Tapir}",
        ivy"com.softwaremill.sttp.client3::async-http-client-backend-cats::3.4.1",
        ivy"org.http4s::http4s-blaze-server::${Version.Http4s}",
        ivy"com.softwaremill.sttp.client3::circe::3.4.1",
        ivy"org.typelevel::cats-effect::3.2.9"
      )
    }
  }

  object fs2 extends CommonModule with ArmadilloPublishModule {
    override def moduleDeps = Seq(core, json.circe, server)
    override def ivyDeps = Agg(
      ivy"co.fs2::fs2-core::3.2.5",
      ivy"com.softwaremill.sttp.tapir::tapir-cats::${Version.Tapir}"
    )
  }

  object test extends Tests with CommonTestModule { // TODO can it be simplified to `test extends CommonTestModule` ?
    override def moduleDeps = Seq(core, json.circe)
    override def ivyDeps = Agg(
      WeaverDep,
      ivy"io.circe::circe-literal::0.14.1",
      ivy"org.typelevel::cats-effect::3.2.9"
    )
  }
}

object example extends CommonModule {

  object json4sApp extends CommonModule {
    override def moduleDeps = Seq(core, server.tapir, json.json4s)

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
        ivy"ch.qos.logback:logback-classic:1.2.7",
        ivy"com.softwaremill.sttp.tapir::tapir-sttp-client::${Version.Tapir}",
        ivy"com.softwaremill.sttp.client3::async-http-client-backend-cats::3.5.1"
      )
  }
  object circeApp extends CommonModule {
    override def moduleDeps = Seq(core, server.tapir, json.circe)

    override def ivyDeps =
      Agg(
        ivy"org.typelevel::cats-effect::3.3.5",
        ivy"org.http4s::http4s-dsl::${Version.Http4s}",
        ivy"org.http4s::http4s-circe::${Version.Http4s}",
        ivy"org.http4s::http4s-blaze-server::${Version.Http4s}",
        ivy"com.softwaremill.sttp.tapir::tapir-http4s-server::${Version.Tapir}",
        ivy"com.softwaremill.sttp.tapir::tapir-cats::${Version.Tapir}",
        ivy"ch.qos.logback:logback-classic:1.2.7",
        ivy"com.softwaremill.sttp.tapir::tapir-sttp-client::${Version.Tapir}",
        ivy"com.softwaremill.sttp.client3::async-http-client-backend-cats::3.5.1",
        ivy"io.circe::circe-literal::${Version.Circe}"
      )
  }
  object json4sAndTrace4cats extends CommonModule {
    override def moduleDeps = Seq(core, server.tapir, json.json4s, trace4cats)

    override def ivyDeps =
      Agg(
        ivy"org.typelevel::cats-effect::3.3.5",
        ivy"org.http4s::http4s-dsl::${Version.Http4s}",
        ivy"org.http4s::http4s-blaze-server::${Version.Http4s}",
        ivy"com.softwaremill.sttp.tapir::tapir-http4s-server::${Version.Tapir}",
        ivy"com.softwaremill.sttp.tapir::tapir-cats::${Version.Tapir}",
        ivy"org.json4s::json4s-core::${Version.Json4s}",
        ivy"org.json4s::json4s-jackson:${Version.Json4s}",
        ivy"io.janstenpickle::trace4cats-log-exporter::${Version.Trace4cats}",
        ivy"io.janstenpickle::trace4cats-avro-exporter::${Version.Trace4cats}",
        ivy"ch.qos.logback:logback-classic:1.2.7",
        ivy"com.softwaremill.sttp.tapir::tapir-sttp-client::${Version.Tapir}",
        ivy"com.softwaremill.sttp.client3::async-http-client-backend-cats::3.5.1"
      )
  }
  object circeFs2 extends CommonModule {
    override def moduleDeps = Seq(core, server.fs2, json.circe)
    override def ivyDeps = Agg(
      ivy"co.fs2::fs2-core::3.2.5",
      ivy"co.fs2::fs2-io::3.2.5",
      ivy"com.softwaremill.sttp.tapir::tapir-cats::${Version.Tapir}",
      ivy"com.github.jnr:jnr-unixsocket:0.38.8"
    )
  }
}

object trace4cats extends CommonModule with ArmadilloPublishModule {
  override def moduleDeps = Seq(core)
  override def ivyDeps = Agg(
    ivy"io.janstenpickle::trace4cats-base::${Version.Trace4cats}",
    ivy"io.janstenpickle::trace4cats-core::${Version.Trace4cats}",
    ivy"io.janstenpickle::trace4cats-inject::${Version.Trace4cats}",
    ivy"com.softwaremill.sttp.tapir::tapir-cats::${Version.Tapir}"
  )
}

trait BaseModule extends ScalaModule with ScalafmtModule with TpolecatModule with ScalafixModule with ScalaMetalsSupport {
  override def semanticDbVersion = "4.4.32"
  override def scalafixScalaBinaryVersion =  "2.13"
  override def scalacOptions = T {
    super.scalacOptions().filterNot(Set("-Xfatal-warnings", "-Xsource:3")) ++ Seq(
      "-Ymacro-annotations",
      "-Ywarn-value-discard"
    )
  }
  override def scalafixIvyDeps =
    Agg(ivy"com.github.liancheng::organize-imports:0.6.0")
}

trait CommonTestModule extends BaseModule with TestModule {
  val WeaverDep = ivy"com.disneystreaming::weaver-cats:0.7.11"

  override def ivyDeps = Agg(WeaverDep) // TODO how to reuse it?
  override def testFramework = "weaver.framework.CatsEffect"
}

trait CommonModule extends BaseModule {
  def scalaVersion = "2.13.8"
  override def scalacPluginIvyDeps = super.scalacPluginIvyDeps() ++Agg(
    ivy"org.typelevel:::kind-projector:0.13.2"
  )
}

trait ArmadilloPublishModule extends PublishModule {

  def publishVersion = VcsVersion.vcsState().format()

  def pomSettings = PomSettings(
    description = artifactName(),
    organization = "io.iohk.armadillo",
    url = "https://github.com/input-output-hk/armadillo",
    licenses = Seq(License.`Apache-2.0`),
    versionControl = VersionControl.github("input-output-hk", "armadillo"),
    developers = Seq(
      Developer("ghostbuster91", "Kasper Kondzielski", "https://github.com/ghostbuster91")
    )
  )
}

object Version {
  val Trace4cats = "0.12.0"
  val Tapir = "1.0.0-M4"
  val Http4s = "0.23.10"
  val Json4s = "4.0.4"
  val Circe = "0.14.1"
}
