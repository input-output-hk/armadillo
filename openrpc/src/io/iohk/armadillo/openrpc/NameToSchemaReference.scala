package io.iohk.armadillo.openrpc

import sttp.apispec.Reference
import sttp.tapir.{Schema => TSchema}

class NameToSchemaReference(nameToKey: Map[TSchema.SName, String]) {
  def map(name: TSchema.SName): Reference = {
    nameToKey.get(name) match {
      case Some(key) =>
        Reference.to("#/components/schemas/", key)
      case None =>
        // no reference to internal model found. assuming external reference
        Reference(name.fullName)
    }
  }
}
