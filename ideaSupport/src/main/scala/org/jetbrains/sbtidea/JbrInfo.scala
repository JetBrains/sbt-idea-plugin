package org.jetbrains.sbtidea

import org.jetbrains.sbtidea.download.jbr.JbrResolver

import java.util.Locale

sealed trait JbrInfo {
  def major: String
  def minor: String
  def kind: String
  def platform: String
  def arch: String
}

trait AutoJbrPlatform {
  def platform: String = System.getProperty("os.name", "").toLowerCase(Locale.ENGLISH) match {
    case value if value.startsWith("win") => "windows"
    case value if value.startsWith("lin") => "linux"
    case value if value.startsWith("mac") => "osx"
    case other => throw new IllegalStateException(s"OS $other is unsupported")
  }
  def arch: String = System.getProperty("os.arch") match {
    case "x86"  => "x86"
    case _      => "x64"
  }
}

trait DynamicJbrInfo {
  def major: String = throw new IllegalStateException("Static evaluation of JBR major version is unsupported in AutoJbr")
  def minor: String = throw new IllegalStateException("Static evaluation of JBR minor version is unsupported in AutoJbr")
  def kind: String  = throw new IllegalStateException("Static evaluation of JBR kind is unsupported in AutoJbr")
}

final case class JBR(major: String, minor: String, kind: String, platform: String, arch: String) extends JbrInfo
final case class AutoJbr() extends JbrInfo with AutoJbrPlatform with DynamicJbrInfo
final case class AutoJbrWithPlatform(major: String, minor: String, kind: String = JbrResolver.JBR_DCEVM_KIND) extends JbrInfo with AutoJbrPlatform
final case class AutoJbrWithKind(override val kind: String) extends JbrInfo with AutoJbrPlatform with DynamicJbrInfo
final case class AutoJbrWithArch(override val arch: String) extends JbrInfo with AutoJbrPlatform with DynamicJbrInfo
object NoJbr extends JbrInfo {
  override def major: String    = throw new IllegalStateException("unreachable")
  override def minor: String    = throw new IllegalStateException("unreachable")
  override def kind: String     = throw new IllegalStateException("unreachable")
  override def platform: String = throw new IllegalStateException("unreachable")
  override def arch: String     = throw new IllegalStateException("unreachable")
}