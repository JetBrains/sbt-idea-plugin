package org.jetbrains.sbtidea

import java.util.Locale

sealed trait JbrInfo {
  def version: JbrVersion
  def kind: JbrKind
  def platform: JbrPlatform
}

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

final case class JBR(
  version: JbrVersion,
  kind: JbrKind,
  platform: JbrPlatform
) extends JbrInfo

final case class AutoJbr(
  explicitVersion: Option[JbrVersion] = None,
  explicitKind: Option[JbrKind] = None ,
  explicitPlatform: Option[JbrPlatform] = None
) extends JbrInfo {

  /** version will be calculated in [[org.jetbrains.sbtidea.download.jbr.JbrResolver getJbrVersion]] */
  override def version: JbrVersion =
    throw new IllegalStateException("Static evaluation of JBR version is unsupported in AutoJbr")

  /** kind will be calculated in [[org.jetbrains.sbtidea.download.jbr.JbrResolver getJbrKind]] */
  override def kind: JbrKind =
    throw new IllegalStateException("Static evaluation of JBR kind is unsupported in AutoJbr")

  override def platform: JbrPlatform =
    explicitPlatform.getOrElse(JbrPlatform.auto)
}

object NoJbr extends JbrInfo {
  override def version: JbrVersion   = throw new IllegalStateException("unreachable")
  override def kind: JbrKind         = throw new IllegalStateException("unreachable")
  override def platform: JbrPlatform = throw new IllegalStateException("unreachable")
}


/**
 * For all possible values see [[https://github.com/JetBrains/JetBrainsRuntime/releases]]
 * Example
 * {{{
 * full  : 11_0_14_1b2119.3
 * major : 11_0_14_1
 * minor : 2119.3
 * }}}
 */
final case class JbrVersion(major: String, minor: String)
object JbrVersion {
  @throws[IllegalStateException]
  def parse(fullVersion: String): JbrVersion = {
    val lastIndexOfB = fullVersion.lastIndexOf('b')
    if (lastIndexOfB > -1) {
      val major = fullVersion.substring(0, lastIndexOfB)
      val minor = fullVersion.substring(lastIndexOfB + 1)
      JbrVersion(major, minor)
    }
    else {
      throw new IllegalStateException(s"Malformed jbr version: $fullVersion")
    }
  }
}

/** For all possible values see [[https://github.com/JetBrains/JetBrainsRuntime/releases]] */
final case class JbrKind(value: String)

//noinspection ScalaUnusedSymbol (can be used by sbt plugin users)
object JbrKind {
  val JBR_VANILLA: JbrKind    = JbrKind("jbr")
  val JBR_JCEF: JbrKind       = JbrKind("jbr_jcef")
  val JBR_DCEVM: JbrKind      = JbrKind("jbr_dcevm")
  val JBR_FAST_DEBUG: JbrKind = JbrKind("jbr_fd")
  val JBR_SDK: JbrKind        = JbrKind("jbrsdk")
}

/**
 * For all possible values see [[https://github.com/JetBrains/JetBrainsRuntime/releases]]
 */
final case class JbrPlatform(os: String, arch: String)

//noinspection ScalaUnusedSymbol (can be used by sbt plugin users)
object JbrPlatform {
  object Arch {
    val aarch6 = "aarch64"
    val x64 = "x64"
  }

  object Os {
    val linux = "linux"
    val osx = "osx"
    val windows = "windows"
  }

  val linux_aarch64: JbrPlatform = JbrPlatform(Os.linux, Arch.aarch6)
  val linux_x64: JbrPlatform = JbrPlatform(Os.linux, Arch.x64)

  val osx_aarch64: JbrPlatform = JbrPlatform(Os.osx, Arch.aarch6)
  val osx_x64: JbrPlatform = JbrPlatform(Os.osx, Arch.x64)

  val windows_x64: JbrPlatform = JbrPlatform(Os.windows, Arch.x64)

  def auto: JbrPlatform = {
    val osName = System.getProperty("os.name", "").toLowerCase(Locale.ENGLISH) match {
      case value if value.startsWith("win") => Os.windows
      case value if value.startsWith("lin") => Os.linux
      case value if value.startsWith("mac") => Os.osx
      case other => throw new IllegalStateException(s"OS $other is unsupported")
    }
    val osArch = System.getProperty("os.arch") match {
      case Arch.aarch6 => Arch.aarch6
      case _ => Arch.x64
    }
    JbrPlatform(osName, osArch)
  }
}