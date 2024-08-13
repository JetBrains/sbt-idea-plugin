package org.jetbrains.sbtidea.productInfo

import org.jetbrains.sbtidea.JbrPlatform

/**
 * The class represents a subset of fields of `product-info.json` file in IntelliJ installation root.
 * It contains only those fields which we actually use
 *
 * Similar entity from Gradle plugin:<br>
 * [[https://github.com/JetBrains/intellij-platform-gradle-plugin/blob/main/src/main/kotlin/org/jetbrains/intellij/platform/gradle/models/ProductInfo.kt#L36]]
 *
 * See also [[org.jetbrains.sbtidea.download.BuildInfo]]
 */
case class ProductInfo(
  name: String,
  version: String,
  versionSuffix: Option[String],
  buildNumber: String,
  productCode: String,
  modules: Seq[String],
  launch: Seq[Launch],
  layout: Seq[LayoutItem]
) {

  def productModulesNames: Seq[String] =
    productModulesLayout.map(_.name)

  def productModulesLayout: Seq[LayoutItem] =
    layout.filter(_.kind == LayoutItemKind.ProductModuleV2)

  /**
   * Finds the [[Launch]] object for the given OS and architecture corresponding to the [[JbrPlatform]]
   */
  def launchFor(jbrPlatform: JbrPlatform): Launch = {
    val os = jbrPlatform.os
    val arch = jbrPlatform.arch

    val archInProductFormat = arch match {
      case JbrPlatform.Arch.x64 => "amd64"
      case JbrPlatform.Arch.aarch6 => "aarch64"
      case other =>
        throw new IllegalArgumentException(s"Unsupported JVM architecture: '$other'")
    }

    val osInProductFormat = os match {
      case  JbrPlatform.Os.windows => OS.Windows
      case  JbrPlatform.Os.linux => OS.Linux
      case  JbrPlatform.Os.osx => OS.macOs
      case other =>
        throw new IllegalArgumentException(s"Unsupported OS: $other")
    }

    val foundLaunch = launch.find(l => l.os == osInProductFormat && l.arch == archInProductFormat)
    foundLaunch.getOrElse {
      throw new IllegalArgumentException(
        s"""Could not find launch information for the OS: '$os' ($arch).
           |Available OS/architectures:
           |${launch.map(l => (l.os, l.arch)).mkString("\n")}""".stripMargin)
    }
  }
}

case class LayoutItem(
  name: String,
  kind: LayoutItemKind,
  classPath: Option[Seq[String]]
)

sealed trait LayoutItemKind
object LayoutItemKind {
  case object Plugin extends LayoutItemKind
  case object PluginAlias extends LayoutItemKind
  case object ProductModuleV2 extends LayoutItemKind
  case object ModuleV2 extends LayoutItemKind
  case class Unknown(value: String) extends LayoutItemKind
}

/**
 * Represents a launch configuration for a product.
 *
 * @param os The target operating system for the launch.
 * @param arch The architecture of the target system.
 * @param launcherPath The path to the OS-specific launcher executable.
 * @param javaExecutablePath The path to the Java executable.
 * @param vmOptionsFilePath The path to the file containing VM options.
 * @param startupWmClass The startup window class (WM_CLASS) for the application.
 * @param bootClassPathJarNames The names of the JAR files to be included in the boot classpath.
 */
case class Launch(
  os: OS,
  arch: String,
  launcherPath: String,
  javaExecutablePath: Option[String],
  vmOptionsFilePath: String,
  startupWmClass: Option[String],
  bootClassPathJarNames: Seq[String],
  additionalJvmArguments: Seq[String],
)

sealed trait OS
object OS {
  case object Windows extends OS
  case object macOs extends OS
  case object Linux extends OS
}