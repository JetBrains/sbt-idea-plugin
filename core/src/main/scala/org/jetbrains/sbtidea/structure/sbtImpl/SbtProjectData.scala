package org.jetbrains.sbtidea.structure.sbtImpl

import sbt.*
import sbt.Def.Classpath

import java.io.File
import scala.language.implicitConversions


/**
 * @param cp runtime classpath required to run the plugin, should include runtime dependencies as well<br>
 *           (see https://github.com/JetBrains/sbt-idea-plugin/issues/135)
 */
case class SbtProjectData(thisProject: ProjectRef,
                          thisProjectName: String,
                          cp: Classpath,
                          definedDeps: Seq[ModuleID],
                          productDirs: Seq[File],
                          report: UpdateReport) extends CommonSbtProjectData