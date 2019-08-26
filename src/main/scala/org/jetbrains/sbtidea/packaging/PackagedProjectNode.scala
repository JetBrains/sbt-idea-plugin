package org.jetbrains.sbtidea.packaging

import org.jetbrains.sbtidea.structure.ProjectNode

trait PackagedProjectNode extends ProjectNode {
  override type T = this.type
  def packagingOptions: ProjectPackagingOptions
}
