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
    override def ivyDeps = Agg(
      ivy"com.softwaremill.sttp.apispec::apispec-model::${Version.Apispec}",
      ivy"com.softwaremill.sttp.apispec::jsonschema-circe::${Version.Apispec}"
    )
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

    override def ivyDeps = Agg(ivy"io.circe::circe-yaml::${Version.CirceYaml}")
  }

  override def moduleDeps = Seq(core, circeYaml)

  object test extends Tests with CommonTestModule {
    override def moduleDeps = Seq(openrpc, json.circe)
    override def ivyDeps = Agg(
      WeaverDep,
      ivy"org.typelevel::cats-effect::${Version.CatsEffect}"
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
        ivy"com.softwaremill.sttp.client3::armeria-backend-cats::${Version.Sttp}",
        ivy"org.http4s::http4s-blaze-server::${Version.Http4sBlazeServer}",
        ivy"com.softwaremill.sttp.client3::circe::${Version.Sttp}",
        ivy"org.typelevel::cats-effect::${Version.CatsEffect}"
      )
    }
  }

  object fs2 extends CommonModule with ArmadilloPublishModule {
    override def moduleDeps = Seq(core, json.circe, server)
    override def ivyDeps = Agg(
      ivy"co.fs2::fs2-core::${Version.Fs2}",
      ivy"com.softwaremill.sttp.tapir::tapir-cats::${Version.Tapir}"
    )
  }

  object test extends Tests with CommonTestModule {
    override def moduleDeps = Seq(core, json.circe, json.json4s)
    override def ivyDeps = Agg(
      WeaverDep,
      ivy"io.circe::circe-literal::${Version.Circe}",
      ivy"org.json4s::json4s-core::${Version.Json4s}",
      ivy"org.json4s::json4s-jackson:${Version.Json4s}",
      ivy"org.typelevel::cats-effect::${Version.CatsEffect}"
    )
  }

  object stub extends CommonModule with ArmadilloPublishModule {
    override def moduleDeps = Seq(core, server, server.tapir)
    override def ivyDeps = Agg(
      ivy"com.softwaremill.sttp.client3::core::${Version.Sttp}",
      ivy"com.softwaremill.sttp.tapir::tapir-sttp-stub-server::${Version.Tapir}"
    )
    object test extends Tests with CommonTestModule {
      override def moduleDeps = Seq(json.circe, stub)
      override def ivyDeps = Agg(
        WeaverDep,
        ivy"io.circe::circe-literal::${Version.Circe}",
        ivy"org.typelevel::cats-effect::${Version.CatsEffect}",
        ivy"com.softwaremill.sttp.client3::cats::${Version.Sttp}",
        ivy"com.softwaremill.sttp.client3::circe::${Version.Sttp}"
      )
    }
  }
}

object example extends CommonModule {

  object json4sApp extends CommonModule {
    override def moduleDeps = Seq(core, server.tapir, json.json4s)

    override def ivyDeps =
      Agg(
        ivy"org.typelevel::cats-effect::${Version.CatsEffect}",
        ivy"org.http4s::http4s-dsl::${Version.Http4s}",
        ivy"org.http4s::http4s-circe::${Version.Http4s}",
        ivy"org.http4s::http4s-blaze-server::${Version.Http4sBlazeServer}",
        ivy"com.softwaremill.sttp.tapir::tapir-http4s-server::${Version.Tapir}",
        ivy"com.softwaremill.sttp.tapir::tapir-cats::${Version.Tapir}",
        ivy"org.json4s::json4s-core::${Version.Json4s}",
        ivy"org.json4s::json4s-jackson:${Version.Json4s}",
        ivy"ch.qos.logback:logback-classic:${Version.Logback}",
        ivy"com.softwaremill.sttp.tapir::tapir-sttp-client::${Version.Tapir}",
        ivy"com.softwaremill.sttp.client3::armeria-backend-cats::${Version.Sttp}"
      )
  }
  object circeApp extends CommonModule {
    override def moduleDeps = Seq(core, server.tapir, json.circe)

    override def ivyDeps =
      Agg(
        ivy"org.typelevel::cats-effect::${Version.CatsEffect}",
        ivy"org.http4s::http4s-dsl::${Version.Http4s}",
        ivy"org.http4s::http4s-circe::${Version.Http4s}",
        ivy"org.http4s::http4s-blaze-server::${Version.Http4sBlazeServer}",
        ivy"com.softwaremill.sttp.tapir::tapir-http4s-server::${Version.Tapir}",
        ivy"com.softwaremill.sttp.tapir::tapir-cats::${Version.Tapir}",
        ivy"ch.qos.logback:logback-classic:${Version.Logback}",
        ivy"com.softwaremill.sttp.tapir::tapir-sttp-client::${Version.Tapir}",
        ivy"com.softwaremill.sttp.client3::armeria-backend-cats::${Version.Sttp}",
        ivy"io.circe::circe-literal::${Version.Circe}"
      )
  }
  object json4sAndTrace4cats extends CommonModule {
    override def moduleDeps = Seq(core, server.tapir, json.json4s, trace4cats)

    override def ivyDeps =
      Agg(
        ivy"org.typelevel::cats-effect::${Version.CatsEffect}",
        ivy"org.http4s::http4s-dsl::${Version.Http4s}",
        ivy"org.http4s::http4s-blaze-server::${Version.Http4sBlazeServer}",
        ivy"com.softwaremill.sttp.tapir::tapir-http4s-server::${Version.Tapir}",
        ivy"com.softwaremill.sttp.tapir::tapir-cats::${Version.Tapir}",
        ivy"org.json4s::json4s-core::${Version.Json4s}",
        ivy"org.json4s::json4s-jackson:${Version.Json4s}",
        ivy"io.janstenpickle::trace4cats-core::${Version.Trace4cats}",
        ivy"ch.qos.logback:logback-classic:${Version.Logback}",
        ivy"com.softwaremill.sttp.tapir::tapir-sttp-client::${Version.Tapir}",
        ivy"com.softwaremill.sttp.client3::armeria-backend-cats::${Version.Sttp}"
      )
  }
  object circeFs2 extends CommonModule {
    override def moduleDeps = Seq(core, server.fs2, json.circe)
    override def ivyDeps = Agg(
      ivy"co.fs2::fs2-core::${Version.Fs2}",
      ivy"co.fs2::fs2-io::${Version.Fs2}",
      ivy"com.softwaremill.sttp.tapir::tapir-cats::${Version.Tapir}",
      ivy"com.github.jnr:jnr-unixsocket:0.38.19"
    )
  }

  object tapirWebsocket extends CommonModule {
    override def moduleDeps = Seq(core, server.fs2, json.circe, server.tapir)
    override def ivyDeps = Agg(
      ivy"co.fs2::fs2-core::${Version.Fs2}",
      ivy"org.typelevel::cats-effect::${Version.CatsEffect}",
      ivy"org.http4s::http4s-dsl::${Version.Http4s}",
      ivy"org.http4s::http4s-circe::${Version.Http4s}",
      ivy"org.http4s::http4s-blaze-server::${Version.Http4sBlazeServer}",
      ivy"com.softwaremill.sttp.tapir::tapir-http4s-server::${Version.Tapir}",
      ivy"com.softwaremill.sttp.tapir::tapir-cats::${Version.Tapir}",
      ivy"ch.qos.logback:logback-classic:${Version.Logback}",
      ivy"com.softwaremill.sttp.tapir::tapir-sttp-client::${Version.Tapir}",
      ivy"com.softwaremill.sttp.client3::async-http-client-backend-fs2::${Version.Sttp}",
      ivy"io.circe::circe-literal::${Version.Circe}"
    )
  }

  object circeOpenRpc extends CommonModule {
    override def moduleDeps = Seq(core, json.circe, server.tapir, openrpc, openrpc.circe)
    override def ivyDeps = Agg(
      ivy"com.softwaremill.sttp.tapir::tapir-core::${Version.Tapir}",
      ivy"io.circe::circe-core::${Version.Circe}",
      ivy"io.circe::circe-generic::${Version.Circe}",
      ivy"io.circe::circe-literal::${Version.Circe}",
      ivy"org.typelevel::cats-effect::${Version.CatsEffect}"
    )
  }

  object json4sOpenRpc extends CommonModule {
    override def moduleDeps = Seq(core, json.json4s, server.tapir, openrpc, openrpc.circe)
    override def ivyDeps = Agg(
      ivy"com.softwaremill.sttp.tapir::tapir-core::${Version.Tapir}",
      ivy"io.circe::circe-core::${Version.Circe}",
      ivy"org.json4s::json4s-core::${Version.Json4s}",
      ivy"org.json4s::json4s-jackson:${Version.Json4s}",
      ivy"org.typelevel::cats-effect::${Version.CatsEffect}"
    )
  }

  object testing extends CommonModule {
    override def moduleDeps = Seq(core, json.circe, server.tapir, openrpc, openrpc.circe)

    object test extends Tests with CommonTestModule {
      override def moduleDeps = Seq(json.circe, server.stub)
      override def ivyDeps = Agg(
        WeaverDep,
        ivy"com.softwaremill.sttp.client3::cats::${Version.Sttp}",
        ivy"com.softwaremill.sttp.client3::core::${Version.Sttp}",
        ivy"com.softwaremill.sttp.client3::circe::${Version.Sttp}",
        ivy"com.softwaremill.sttp.tapir::tapir-sttp-stub-server::${Version.Tapir}",
        ivy"io.circe::circe-literal::${Version.Circe}",
        ivy"org.typelevel::cats-effect::${Version.CatsEffect}"
      )
    }
  }
}

object trace4cats extends CommonModule with ArmadilloPublishModule {
  override def moduleDeps = Seq(core)
  override def ivyDeps = Agg(
    ivy"io.janstenpickle::trace4cats-kernel::${Version.Trace4cats}",
    ivy"io.janstenpickle::trace4cats-core::${Version.Trace4cats}",
    ivy"com.softwaremill.sttp.tapir::tapir-cats::${Version.Tapir}"
  )
}

trait BaseModule extends ScalaModule with ScalafmtModule with TpolecatModule with ScalafixModule with ScalaMetalsSupport {
  override def semanticDbVersion = T.input { "4.4.32" }
  override def scalafixScalaBinaryVersion = "2.13"
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
  val WeaverDep = ivy"com.disneystreaming::weaver-cats:0.8.1"

  override def ivyDeps = Agg(WeaverDep)
  override def testFramework = "weaver.framework.CatsEffect"
}

trait CommonModule extends BaseModule {
  def scalaVersion = "2.13.8"
  override def scalacPluginIvyDeps = super.scalacPluginIvyDeps() ++ Agg(
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
      Developer("ghostbuster91", "Kasper Kondzielski", "https://github.com/ghostbuster91"),
      Developer("dleflohic", "Damien Le Flohic", "https://github.com/dleflohic"),
      Developer("AurelienRichez", "Aurélien Richez", "https://github.com/AurelienRichez"),
      Developer("AmbientTea", "Nikolaos Dymitriadis", "https://github.com/AmbientTea")
    )
  )

  override def artifactName: T[String] = s"armadillo-${millModuleSegments.parts.mkString("-")}"
}

object Version {
  val Trace4cats = "0.14.1"
  val Tapir = "1.2.3"
  val Http4s = "0.23.16"
  val Http4sBlazeServer = "0.23.12"
  val Json4s = "4.0.6"
  val Circe = "0.14.3"
  val CirceYaml = "0.14.2"
  val Sttp = "3.8.13"
  val CatsEffect = "3.4.2"
  val Apispec = "0.3.1"
  val Fs2 = "3.4.0"
  val Logback = "1.4.5"
}
