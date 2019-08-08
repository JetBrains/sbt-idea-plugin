package org.jetbrains.sbtidea.tasks.packaging.structure

abstract class ProjectStructureExtractor {
  def extract: Seq[ProjectNode]
}
