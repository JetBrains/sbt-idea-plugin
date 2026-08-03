package org.jetbrains.sbtidea

import sbt.ForkOptions

import java.nio.file.Path

/**
 * Builds isolated IntelliJ Platform runtime directories for an individual forked test JVM.
 *
 * The default sbt-idea-plugin test configuration is intentionally unchanged. Consumers opt in
 * by applying these directories to the `ForkOptions` of a particular `Tests.SubProcess`.
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
   * Replaces IntelliJ Platform directory VM options in `forkOptions` with directories that are
   * unique to `runtimeId`. All unrelated JVM options and all fork settings are preserved.
   */
  def applyTo(forkOptions: ForkOptions, runtimeId: String): ForkOptions = {
    val directoryOptions = forRuntimeId(runtimeId).jvmOptions
    val retainedOptions = forkOptions.runJVMOptions.filterNot(isIntellijDirectoryOption)
    forkOptions.withRunJVMOptions(retainedOptions ++ directoryOptions)
  }
}

object IntellijTestRuntimeDirectories {
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

  private def isIntellijDirectoryOption(option: String): Boolean =
    ReplacedOptionPrefixes.exists(option.startsWith)
}
