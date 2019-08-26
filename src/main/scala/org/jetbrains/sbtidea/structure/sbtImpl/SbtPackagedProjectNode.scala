package org.jetbrains.sbtidea.structure.sbtImpl
import org.jetbrains.sbtidea.packaging.{PackagedProjectNode, ProjectPackagingOptions}
import org.jetbrains.sbtidea.structure.Library
import sbt.ProjectRef

trait SbtPackagedProjectNode extends SbtProjectNode with PackagedProjectNode {
  override type T = SbtPackagedProjectNode
}

case class SbtPackagedProjectNodeImpl(override val ref: ProjectRef,
                                     override var parents: Seq[SbtPackagedProjectNodeImpl],
                                     override var children: Seq[SbtPackagedProjectNodeImpl],
                                     override var libs: Seq[Library],
                                     override var packagingOptions: ProjectPackagingOptions
) extends SbtPackagedProjectNode {
  override type T = this.type
}