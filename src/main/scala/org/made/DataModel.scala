package org.made

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}

case class Request(userId: Int)
case class Predict(userId: Int, itemList: Array[Int])
case class Feedback(userId: Int, itemId: Int, isClicked: Int)
case class ItemCounts(itemId: Int, shows: Double, clicks: Double)


object CirceSerializers {
  implicit val requestEncoder: Encoder[Request] = deriveEncoder[Request]
  implicit val predictEncoder: Encoder[Predict] = deriveEncoder[Predict]
  implicit val feedbackEncoder: Encoder[Feedback] = deriveEncoder[Feedback]
  implicit val itemCountsEncoder: Encoder[ItemCounts] = deriveEncoder[ItemCounts]
}

object CirceDeserializers {
  implicit val requestDecoder: Decoder[Request] = deriveDecoder[Request]
  implicit val predictDecoder: Decoder[Predict] = deriveDecoder[Predict]
  implicit val feedbackDecoder: Decoder[Feedback] = deriveDecoder[Feedback]
  implicit val itemCountsDecoder: Decoder[ItemCounts] = deriveDecoder[ItemCounts]
}
