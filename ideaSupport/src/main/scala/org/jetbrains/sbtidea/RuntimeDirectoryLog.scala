package org.jetbrains.sbtidea

/**
 * Reports launch options without resolving SDK metadata or touching runtime directories.
 */
private[sbtidea] object RuntimeDirectoryLog {
  private val propertyPrefixes = Seq(
    "-Didea.system.path=" -> "System",
    "-Didea.config.path=" -> "Config",
    "-Didea.log.path=" -> "Logs",
  )

  /**
   * Logs the runtime directories from the launch options, for example:
   *
   * {{{
   * [info] IntelliJ runtime (tests, my-plugin)
   * [info]   System: /home/me/.ScalaPluginIU/runtime/IU-263.12345.67/a0f9d9b86d/tests/system
   * [info]   Config: /home/me/.ScalaPluginIU/runtime/IU-263.12345.67/a0f9d9b86d/tests/config
   * [info]   Logs: /home/me/.ScalaPluginIU/runtime/IU-263.12345.67/a0f9d9b86d/tests/system/log
   * }}}
   */
  def report(
    purpose: RuntimePurpose,
    project: String,
    jvmOptions: Seq[String],
    logger: PluginLogger,
  ): Unit = {
    // Like the Java launcher, the last property assignment wins.
    val selected = jvmOptions.iterator.flatMap(parseDirectoryProperty).toMap

    if (selected.nonEmpty) {
      logger.info(s"IntelliJ runtime (${purpose.displayName}, $project)")
      propertyPrefixes.foreach { case (_, label) =>
        logger.info(s"  $label: ${selected.getOrElse(label, "not set (IDE default)")}")
      }
    }
  }

  private def parseDirectoryProperty(option: String): Option[(String, String)] =
    propertyPrefixes.iterator.collectFirst {
      case (prefix, label) if option.startsWith(prefix) =>
        label -> option.substring(prefix.length)
    }
}
