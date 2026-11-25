package org.jetbrains.sbtidea

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Path
import java.security.MessageDigest

/** Defaults follow the selected config directory, including shared development configs. No paths are created. */
private[sbtidea] object IntellijRuntimeProperties {
  val Selector = "idea.paths.selector"
  val PropertiesFile = "idea.properties.file"

  def defaults(config: Path): Vector[(String, String)] = {
    val identity = config.toAbsolutePath.normalize().toString
    val digest = MessageDigest.getInstance("SHA-256").digest(identity.getBytes(UTF_8))
    val hash = digest.take(8).map(byte => f"${byte & 0xff}%02x").mkString
    Vector(Selector -> s"SbtIdeaPlugin-$hash", PropertiesFile -> config.resolve("idea.properties").toString)
  }

  def isOption(option: String, key: String): Boolean =
    option == s"-D$key" || option.startsWith(s"-D$key=")

  /** Raw JVM options use last-value-wins semantics, including a bare -Dkey (an empty value). */
  def value(options: Seq[String], key: String): Option[String] =
    options.reverseIterator.find(isOption(_, key)).map(_.stripPrefix(s"-D$key").stripPrefix("="))
}
