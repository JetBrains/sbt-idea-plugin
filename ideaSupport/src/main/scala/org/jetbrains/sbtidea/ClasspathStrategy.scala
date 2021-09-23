package org.jetbrains.sbtidea

import org.jetbrains.sbtidea.download.Version

import scala.math.Ordering.Implicits.infixOrderingOps

final class ClasspathStrategy private(val version: Version) {
  def this(versionStr: String) = this(Version(versionStr))
}

object ClasspathStrategy {
  val Default = new ClasspathStrategy("200.0") // dummy version
  val Since_203_5251 = new ClasspathStrategy("203.5251")

  def forVersion(versionStr: String): ClasspathStrategy =
    forVersion(Version(versionStr))

  def forVersion(version: Version): ClasspathStrategy =
    if (version >= Since_203_5251.version)
      Since_203_5251
    else
      Default
}
