package org.jetbrains.sbtidea.packaging.mappings
import java.io.File

import org.jetbrains.sbtidea.packaging.{Mappings, PackagedProjectNode}

trait AbstractMappingBuilder {
  def outputDir: File
  def buildMappings(nodes: Seq[PackagedProjectNode]): Mappings
}
