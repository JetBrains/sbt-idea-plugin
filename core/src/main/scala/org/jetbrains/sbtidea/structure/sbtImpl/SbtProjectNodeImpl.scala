package org.jetbrains.sbtidea.structure.sbtImpl

import org.jetbrains.sbtidea.structure.Library
import sbt.*


class SbtProjectNodeImpl(override val ref: ProjectRef,
                         override val name: String,
                         var parents: Seq[SbtProjectNodeImpl],
                         var children: Seq[SbtProjectNodeImpl],
                         var libs: Seq[Library])
  extends SbtProjectNode {
  override type T = SbtProjectNodeImpl
}

object SbtProjectNodeImpl {

  def apply(ref: ProjectRef,
            name: String,
            parents: Seq[SbtProjectNodeImpl],
            children: Seq[SbtProjectNodeImpl],
            libs: Seq[Library]): SbtProjectNodeImpl =
    new SbtProjectNodeImpl(ref, name, parents, children, libs)

  def unapply(arg: SbtProjectNodeImpl): Option[(ProjectRef, Seq[SbtProjectNodeImpl], Seq[SbtProjectNodeImpl], Seq[Library])] =
    Some(arg.ref, arg.parents, arg.children, arg.libs)
}