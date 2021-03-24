package org.jetbrains.sbtidea.verifier

import java.io.File
import scala.language.postfixOps


case class PluginVerifierOptions(version: String,
                                 reportsDir: File,
                                 teamcity: Boolean,
                                 teamcityGrouping: Boolean,
                                 offline: Boolean,
                                 pluginDir: File,
                                 ideaDir: File,
                                 failureLevels: Set[FailureLevel],
                                 additionalCommonOpts: Seq[String],
                                 overrideIDEs: Seq[String])

object PluginVerifierOptions {

  implicit class PVOEx(val pvo: PluginVerifierOptions) extends AnyVal {
    import pvo._
    def buildOptions: Seq[String] = {
      val buffer = new scala.collection.mutable.ListBuffer[String]()
      buffer += "-verification-reports-dir"
      buffer += reportsDir.getAbsolutePath
      if (teamcity)         buffer += "-team-city"
      if (teamcityGrouping) buffer += "-tc-grouping"
      if (offline)          buffer += "-offline"
      buffer ++= additionalCommonOpts
      buffer += "check-plugin"
      buffer += pluginDir.getAbsolutePath
      if (overrideIDEs.isEmpty)
        buffer += ideaDir.getAbsolutePath
      else
        buffer ++= overrideIDEs
      buffer
    }
  }

}