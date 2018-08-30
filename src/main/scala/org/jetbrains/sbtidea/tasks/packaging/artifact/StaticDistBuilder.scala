package org.jetbrains.sbtidea.tasks.packaging.artifact

import org.jetbrains.sbtidea.tasks.packaging.Mapping
import sbt.File
import sbt.Keys.TaskStreams

class StaticDistBuilder(stream: TaskStreams, target: File) extends DistBuilder(stream, target) {

  override protected def filterGroupedMappings(grouped: Seq[(sbt.File, Seq[Mapping])]): Seq[(sbt.File, Seq[Mapping])] = {
    grouped.filter { case (to, mappings) => !to.toString.contains("!") && mappings.forall(_.metaData.static) }
  }

}
