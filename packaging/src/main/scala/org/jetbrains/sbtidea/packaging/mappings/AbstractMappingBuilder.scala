package org.jetbrains.sbtidea.packaging.mappings
import java.io.File

import org.jetbrains.sbtidea.packaging.Mappings
import org.jetbrains.sbtidea.packaging.structure.PackagedProjectNode

trait AbstractMappingBuilder {
  def outputDir: File
  def buildMappings(nodes: Seq[PackagedProjectNode]): Mappings
}
