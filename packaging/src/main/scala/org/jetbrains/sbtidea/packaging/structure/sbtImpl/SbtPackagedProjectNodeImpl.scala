package org.jetbrains.sbtidea.packaging.structure.sbtImpl

import org.jetbrains.sbtidea.packaging.structure.{PackagedProjectNode, ProjectPackagingOptions}
import org.jetbrains.sbtidea.structure.Library
import org.jetbrains.sbtidea.structure.sbtImpl.SbtProjectNode
import sbt.ProjectRef

//trait SbtPackagedProjectNode extends SbtProjectNode with PackagedProjectNode {
////  override type T = SbtPackagedProjectNode
//}

case class SbtPackagedProjectNodeImpl(override val ref: ProjectRef,
                                      var parents: Seq[PackagedProjectNode],
                                      var children: Seq[PackagedProjectNode],
                                      var libs: Seq[Library],
                                      var packagingOptions: ProjectPackagingOptions
) extends SbtProjectNode with PackagedProjectNode {
  override type T = PackagedProjectNode
}