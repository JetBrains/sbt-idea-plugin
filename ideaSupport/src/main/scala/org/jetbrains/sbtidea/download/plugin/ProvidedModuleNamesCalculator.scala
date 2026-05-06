package org.jetbrains.sbtidea.download.plugin

import org.jetbrains.sbtidea.moduleDescriptors.ModuleDescriptor
import org.jetbrains.sbtidea.productInfo.{OS, ProductInfo}
import org.jetbrains.sbtidea.PathExt

import java.nio.file.{Files, Path}

/**
 * Computes provided module names from an IDE installation.
 *
 * This object deliberately contains no caching or debug instrumentation. Callers decide whether
 * and how to memoize the result.
 */
private[plugin] object ProvidedModuleNamesCalculator {

  def calculate(productInfo: ProductInfo, platformPath: Path): Set[String] = {
    val platformJars = getPlatformJars(productInfo, platformPath)
    val bundledPluginJars = getBundledPluginJars(platformPath)

    val collectedJars = (platformJars ++ bundledPluginJars)
      .map(jar => platformPath.relativize(jar).invariantSeparatorsPathString)

    val moduleDescriptorsFile = platformPath.resolve("modules").resolve("module-descriptors.jar")
    ModuleDescriptor.parseDescriptors(moduleDescriptorsFile).collect {
      case desc if desc.path.exists(collectedJars.contains) =>
        desc.name
    }.toSet
  }

  private def getPlatformJars(productInfo: ProductInfo, platformPath: Path): Set[Path] = {
    val bootClasspath: Seq[String] = productInfo.launch
      .filter(_.os == OS.current)
      .flatMap(_.bootClassPathJarNames)
      .map("lib/" + _)

    val ijCorePluginId = "com.intellij"
    val ijCorePluginClasspath: Seq[String] = productInfo.layout
      .filter(_.name == ijCorePluginId)
      .flatMap(_.classPath).flatten

    val finalClasspath = bootClasspath ++ ijCorePluginClasspath
    finalClasspath
      .map(platformPath.resolve)
      .filter(path => Files.exists(path))
      .toSet
  }

  private def getBundledPluginJars(platformPath: Path): Set[Path] = {
    val allPluginsJarsLocations = listDirectoryEntries(platformPath.resolve("plugins"))
      .flatMap(plugin => Seq(plugin.resolve("lib"), plugin.resolve("lib").resolve("modules")))

    allPluginsJarsLocations
      .filter(path => Files.exists(path))
      .flatMap(listDirectoryEntries(_, "*.jar"))
      .toSet
  }
}
