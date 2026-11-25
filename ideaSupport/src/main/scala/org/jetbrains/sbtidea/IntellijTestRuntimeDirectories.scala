package org.jetbrains.sbtidea

import sbt.ForkOptions

import java.nio.file.{Path, Paths}

/**
 * Builds isolated IntelliJ Platform runtime directories for an individual forked test JVM.
 *
 * By default, sbt-idea-plugin isolates tests by checkout and target SDK build. Consumers can additionally isolate
 * individual JVMs by applying an explicit runtime ID to a particular `Tests.SubProcess`.
 * Explicit IDs are used verbatim and are independent of the default checkout identity.
 */
final class IntellijTestRuntimeDirectories private (ideaHome: Path) {
  import IntellijTestRuntimeDirectories.*

  /**
   * Creates the system, config, and log directories for a path-safe runtime identifier.
   */
  def forRuntimeId(runtimeId: String): RuntimeDirectories = {
    validateRuntimeId(runtimeId)

    val system = ideaHome.resolve(s"test-system-$runtimeId")
    RuntimeDirectories(
      system = system,
      config = ideaHome.resolve(s"test-config-$runtimeId"),
      log = system.resolve("log")
    )
  }

  /**
   * Replaces IntelliJ Platform system, config, log, and plugin-discovery VM options in `forkOptions`
   * with directories unique to `runtimeId`. Generated selector/properties-file defaults follow the new config;
   * separately configured values, unrelated JVM options, and other fork settings are preserved.
   */
  def applyTo(forkOptions: ForkOptions, runtimeId: String): ForkOptions = {
    val directories = forRuntimeId(runtimeId)
    val directoryOptions = directories.jvmOptions :+ s"-Didea.plugins.path=${pluginsDirectory(directories.config)}"
    val previousOptions = forkOptions.runJVMOptions
    val previousDefaults = IntellijRuntimeProperties.value(previousOptions, "idea.config.path")
      .map(config => IntellijRuntimeProperties.defaults(Paths.get(config)).toMap).getOrElse(Map.empty[String, String])
    val propertiesOptions = IntellijRuntimeProperties.defaults(directories.config).map { case (key, defaultValue) =>
      // ForkOptions carries strings, not provenance. Values matching the old defaults are managed;
      // a different effective value is an explicit override and must survive relocation.
      val value = IntellijRuntimeProperties.value(previousOptions, key)
        .filterNot(value => previousDefaults.get(key).contains(value)).getOrElse(defaultValue)
      s"-D$key=$value"
    }
    val retainedOptions = forkOptions.runJVMOptions.filterNot(option =>
      isIntellijDirectoryOption(option) || option.startsWith("-Didea.plugins.path=") ||
        IntellijRuntimeProperties.isOption(option, IntellijRuntimeProperties.Selector) ||
        IntellijRuntimeProperties.isOption(option, IntellijRuntimeProperties.PropertiesFile))
    forkOptions.withRunJVMOptions(retainedOptions ++ directoryOptions ++ propertiesOptions)
  }
}

object IntellijTestRuntimeDirectories {
  private[sbtidea] def pluginsDirectory(config: Path): Path = config.resolve("plugins")

  /**
   * Creates a directory transformer rooted at the IntelliJ Platform home used for tests.
   */
  def apply(ideaHome: Path): IntellijTestRuntimeDirectories =
    new IntellijTestRuntimeDirectories(ideaHome)

  final case class RuntimeDirectories(system: Path, config: Path, log: Path) {
    def jvmOptions: Vector[String] = Vector(
      s"-Didea.system.path=$system",
      s"-Didea.config.path=$config",
      s"-Didea.log.path=$log",
    )
  }

  private val RuntimeId = "[A-Za-z0-9][A-Za-z0-9._-]*".r
  private val ReplacedOptionPrefixes = Seq(
    "-Didea.system.path=",
    "-Didea.config.path=",
    "-Didea.log.path=",
  )

  private def validateRuntimeId(runtimeId: String): Unit =
    require(
      RuntimeId.pattern.matcher(runtimeId).matches(),
      "IntelliJ test runtime ID must start with a letter or digit and contain only letters, digits, '.', '_', or '-': " + runtimeId
    )

  private[sbtidea] def isIntellijDirectoryOption(option: String): Boolean =
    ReplacedOptionPrefixes.exists(option.startsWith)
}
