package org.jetbrains.sbtidea.packaging.testUtils

import io.circe.Decoder

object JsonUtils {

  def decodeJson[T : Decoder](json: String, typeName: String): T =
    io.circe.parser.decode[T](json) match {
      case Right(value) => value
      case Left(error) =>
        throw new RuntimeException(s"Failed to decode $typeName: ${error.getMessage}", error)
    }
}
