package org.jetbrains.sbtidea.tasks.packaging.structure.mappings
import java.io.File

import org.jetbrains.sbtidea.tasks.packaging.Mappings
import org.jetbrains.sbtidea.tasks.packaging.structure.ProjectNode

trait AbstractMappingBuilder {
  def outputDir: File
  def buildMappings(nodes: Seq[ProjectNode]): Mappings
}
