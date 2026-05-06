package org.jetbrains.sbtidea.download.plugin

import org.jetbrains.sbtidea.download.api.IdeInstallationContext
import org.jetbrains.sbtidea.productInfo.ProductInfo
import org.jetbrains.sbtidea.PluginLogger as log

import java.nio.file.{Files, Path}

/**
 * JVM-wide cache for the module names already provided by an IDE installation.
 *
 * `intellijPluginJars` can be requested by many sbt projects in the same JVM.
 * It's calculated during each `compile` command and can lead to a huge time waste even during incremental compilation.
 * (5/10/15... seconds in total)
 *
 * Without this cache, each request rebuilds the same local IDE model: platform jars, bundled plugin jars, descriptor
 * jar paths, and the final module-name set used for dependency filtering.
 *
 * The cache load delegates to [[ProvidedModuleNamesCalculator.calculate]] and is comparatively heavyweight.
 * Its most expensive step is
 * `ModuleDescriptor.parseDescriptors`, which reads `modules/module-descriptors.jar` and decodes descriptor XML.
 *
 * Assumptions and deliberate shortcuts:
 *  - An IDE installation is usually stable while one sbt JVM is running.
 *  - The supplied `ProductInfo` is expected to describe the `product-info.json` under the same
 *    `ctx.baseDirectory`; the file stamp is the invalidation signal, not object identity.
 *  - The current OS is not part of the key because this is a JVM-local cache and `OS.current`
 *    cannot change while the JVM is running.
 *  - File stamps use path, existence, last-modified time, and size instead of content hashes;
 *    directory stamps intentionally omit size because directory size is filesystem-specific and
 *    less meaningful than directory mtime for add/remove/rename invalidation.
 *  - The bundled plugins stamp is intentionally shallow: it tracks immediate plugin names and
 *    their jar directories but does not stamp the `plugins` root, plugin root metadata, or every
 *    jar. Missing and empty `plugins` roots both produce no bundled jars, and the cached value
 *    depends on jar path membership, not jar byte contents. Normal add/remove/rename plugin
 *    updates should invalidate it, while same-path jar replacement without timestamp changes may
 *    keep the old value.
 */
private[plugin] object ProvidedModuleNamesCache {

  // Test/profiling hook: when DebugProperty is true, each cache load logs a line with this prefix.
  // Counting those lines shows how many times the expensive calculation actually ran.
  private[plugin] val DebugProperty: String = "sbtidea.providedModuleNamesCache.debug"
  private[plugin] val CalculatingProvidedModuleNamesMessagePrefix: String =
    "[sbt-idea-plugin] calculating provided module names for "

  private val cache = new CaffeineCache[ProvidedModuleNamesKey, Set[String]](log)

  def providedModuleNames(productInfo: ProductInfo)(implicit ctx: IdeInstallationContext): Set[String] = {
    val platformPath = normalize(ctx.baseDirectory)
    val key = ProvidedModuleNamesKey.from(platformPath)

    cache.getOrCompute(key, {
      if (isDebugEnabled) {
        log.info(CalculatingProvidedModuleNamesMessagePrefix + key)
      }
      ProvidedModuleNamesCalculator.calculate(productInfo, platformPath)
    })
  }

  private def isDebugEnabled: Boolean =
    java.lang.Boolean.getBoolean(DebugProperty)

  private def normalize(path: Path): Path =
    path.toAbsolutePath.normalize()

  private final case class ProvidedModuleNamesKey(
    baseDirectory: String,
    productInfoFile: FileStamp,
    moduleDescriptorsJar: FileStamp,
    bundledPluginJarDirectories: Seq[BundledPluginJarDirectoriesStamp],
  ) {
    override def toString: String =
      s"ProvidedModuleNamesKey(baseDirectory=$baseDirectory, productInfoFile=$productInfoFile, " +
        s"moduleDescriptorsJar=$moduleDescriptorsJar, bundledPluginEntryCount=${bundledPluginJarDirectories.size})"
  }

  private object ProvidedModuleNamesKey {
    def from(platformPath: Path): ProvidedModuleNamesKey =
      ProvidedModuleNamesKey(
        baseDirectory = normalize(platformPath).toString,
        productInfoFile = FileStamp.from(platformPath.resolve("product-info.json")),
        moduleDescriptorsJar = FileStamp.from(platformPath.resolve("modules").resolve("module-descriptors.jar")),
        bundledPluginJarDirectories = BundledPluginJarDirectoriesStamp.fromPluginsDirectory(platformPath.resolve("plugins")),
      )
  }

  private final case class BundledPluginJarDirectoriesStamp(
    name: String,
    libDirectory: DirectoryStamp,
    modulesDirectory: DirectoryStamp,
  )

  private object BundledPluginJarDirectoriesStamp {
    // Cheap invalidation for bundled jars: only jar directory membership matters here, so do not
    // stamp the plugins root, plugin root metadata, or every jar file just to form the cache key.
    def fromPluginsDirectory(pluginsDirectory: Path): Seq[BundledPluginJarDirectoriesStamp] =
      listDirectoryEntries(pluginsDirectory)
        .sortBy(_.getFileName.toString)
        .map(fromPluginEntry)

    private def fromPluginEntry(pluginEntry: Path): BundledPluginJarDirectoriesStamp =
      BundledPluginJarDirectoriesStamp(
        name = pluginEntry.getFileName.toString,
        libDirectory = DirectoryStamp.from(pluginEntry.resolve("lib")),
        modulesDirectory = DirectoryStamp.from(pluginEntry.resolve("lib").resolve("modules")),
      )
  }

  private final case class FileStamp(
    path: String,
    exists: Boolean,
    lastModifiedMillis: Long,
    size: Long,
  )

  private object FileStamp {
    def from(path: Path): FileStamp = {
      val normalizedPath = normalize(path)
      if (Files.exists(normalizedPath)) {
        FileStamp(
          path = normalizedPath.toString,
          exists = true,
          lastModifiedMillis = Files.getLastModifiedTime(normalizedPath).toMillis,
          size = Files.size(normalizedPath),
        )
      } else {
        FileStamp(
          path = normalizedPath.toString,
          exists = false,
          lastModifiedMillis = -1L,
          size = -1L,
        )
      }
    }
  }

  private final case class DirectoryStamp(
    path: String,
    isDirectory: Boolean,
    lastModifiedMillis: Long,
  )

  private object DirectoryStamp {
    def from(path: Path): DirectoryStamp = {
      val normalizedPath = normalize(path)
      if (Files.isDirectory(normalizedPath)) {
        DirectoryStamp(
          path = normalizedPath.toString,
          isDirectory = true,
          lastModifiedMillis = Files.getLastModifiedTime(normalizedPath).toMillis,
        )
      } else {
        DirectoryStamp(
          path = normalizedPath.toString,
          isDirectory = false,
          lastModifiedMillis = -1L,
        )
      }
    }
  }
}
