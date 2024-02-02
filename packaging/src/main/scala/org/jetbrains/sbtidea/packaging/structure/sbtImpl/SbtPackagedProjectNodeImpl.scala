package org.jetbrains.sbtidea.packaging.structure.sbtImpl

import org.jetbrains.sbtidea.packaging.structure.{PackagedProjectNode, ProjectPackagingOptions}
import org.jetbrains.sbtidea.structure.Library
import org.jetbrains.sbtidea.structure.sbtImpl.SbtProjectNode
import sbt.ProjectRef

@SerialVersionUID(2)
class SbtPackagedProjectNodeImpl(override val ref: ProjectRef,
                                 override val name: String,
                                 override val rootProjectName: Option[String],
                                 var parents: Seq[PackagedProjectNode],
                                 var children: Seq[PackagedProjectNode],
                                 var libs: Seq[Library],
                                 var packagingOptions: ProjectPackagingOptions
) extends SbtProjectNode with PackagedProjectNode with Serializable {
  override type T = PackagedProjectNode
}

object SbtPackagedProjectNodeImpl {
  def apply(ref: ProjectRef,
            name: String,
            rootProjectName: Option[String],
            parents: Seq[PackagedProjectNode],
            children: Seq[PackagedProjectNode],
            libs: Seq[Library],
            packagingOptions: ProjectPackagingOptions): SbtPackagedProjectNodeImpl =
    new SbtPackagedProjectNodeImpl(ref, name, rootProjectName, parents, children, libs, packagingOptions)

  def unapply(arg: SbtPackagedProjectNodeImpl): Option[(ProjectRef, Seq[PackagedProjectNode], Seq[PackagedProjectNode], Seq[Library], ProjectPackagingOptions)] =
    Some((arg.ref, arg.parents, arg.children, arg.libs, arg.packagingOptions))
}