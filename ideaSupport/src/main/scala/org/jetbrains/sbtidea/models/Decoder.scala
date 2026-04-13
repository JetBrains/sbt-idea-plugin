package org.jetbrains.sbtidea.models

import java.io.InputStream
import java.net.URL
import java.nio.file.Path

/**
 * A decoder that can decode a node into a model data class.
 * @tparam Model the model data class
 * @tparam Node the node to be decoded (XML Elem, JSON JsValue, etc.)
 */
trait Decoder[Model, Node] {
  def decode(node: Node): Model

  def decode(str: String): Model

  def decode(url: URL): Model

  def decode(path: Path): Model

  def decode(stream: InputStream): Model
}
