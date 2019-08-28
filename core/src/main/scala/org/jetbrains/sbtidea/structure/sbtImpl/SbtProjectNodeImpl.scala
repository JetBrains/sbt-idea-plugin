package org.jetbrains.sbtidea.structure.sbtImpl

import org.jetbrains.sbtidea.structure.Library
import sbt._


case class SbtProjectNodeImpl(ref: ProjectRef,
                              var parents: Seq[SbtProjectNodeImpl],
                              var children: Seq[SbtProjectNodeImpl],
                              var libs: Seq[Library])
  extends SbtProjectNode { override type T = SbtProjectNodeImpl }