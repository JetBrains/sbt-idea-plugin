package org.jetbrains.sbtidea

/**
 * Describes a launch in runtime-directory summaries without affecting directory selection.
 */
private[sbtidea] sealed abstract class RuntimePurpose(val displayName: String)

private[sbtidea] object RuntimePurpose {
  /**
   * Development IDE instance launched by the `runIDE` task.
   */
  case object RunIDE extends RuntimePurpose("runIDE")

  /**
   * IntelliJ test JVM launched through sbt.
   */
  case object Tests extends RuntimePurpose("tests")

  /**
   * Headless IDE launched to generate searchable-options indexes.
   */
  case object BuildIndex extends RuntimePurpose("build-index")

  /**
   * Caller-supplied display label for an additional launch purpose.
   */
  final case class Custom(override val displayName: String) extends RuntimePurpose(displayName)
}
