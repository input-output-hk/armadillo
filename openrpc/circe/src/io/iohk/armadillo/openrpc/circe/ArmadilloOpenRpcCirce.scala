package io.iohk.armadillo.openrpc.circe

import io.circe.generic.semiauto._
import io.circe.{Encoder, Json}
import io.iohk.armadillo.openrpc.model._
import sttp.apispec.internal.JsonSchemaCirceEncoders

trait ArmadilloOpenRpcCirce extends JsonSchemaCirceEncoders {
  // note: these are strict val-s, order matters!
  implicit val paramEncoder: Encoder[OpenRpcParam] = deriveEncoder[OpenRpcParam]
  implicit val resultEncoder: Encoder[OpenRpcResult] = deriveEncoder[OpenRpcResult]
  implicit val errorEncoder: Encoder[OpenRpcError] = deriveEncoder[OpenRpcError]

  implicit val extDescriptionEncoder: Encoder[OpenRpcExternalDocs] = deriveEncoder[OpenRpcExternalDocs]
  implicit val tagsEncoder: Encoder[OpenRpcMethodTag] = deriveEncoder[OpenRpcMethodTag]

  implicit val methodEncoder: Encoder[OpenRpcMethod] = deriveEncoder[OpenRpcMethod]
  implicit val infoEncoder: Encoder[OpenRpcInfo] = deriveEncoder[OpenRpcInfo]
  implicit val componentsEncoder: Encoder[OpenRpcComponents] = deriveEncoder[OpenRpcComponents]
  implicit val documentEncoder: Encoder[OpenRpcDocument] = deriveEncoder[OpenRpcDocument]

  implicit def encodeRequiredList[T: Encoder]: Encoder[RequiredList[T]] = { case requiredList: RequiredList[T] =>
    Json.arr(requiredList.wrapped.map(i => implicitly[Encoder[T]].apply(i)): _*)
  }
}
