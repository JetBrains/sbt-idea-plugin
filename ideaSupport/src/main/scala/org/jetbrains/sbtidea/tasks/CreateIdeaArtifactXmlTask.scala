package org.jetbrains.sbtidea.tasks

import org.jetbrains.sbtidea.packaging.PackagingKeys.{packageMappingsOffline, packageOutputDir}
import org.jetbrains.sbtidea.packaging.artifact.IdeaArtifactXmlBuilder
import sbt.Keys.{baseDirectory, thisProject}
import sbt.{Def, *}

object CreateIdeaArtifactXmlTask extends SbtIdeaTask[Unit] {
  override def createTask: Def.Initialize[Task[Unit]] = Def.taskDyn {
    val buildRoot = baseDirectory.in(ThisBuild).value
    val projectRoot = baseDirectory.in(ThisProject).value

    if (buildRoot == projectRoot)
      Def.task {
        val outputDir = packageOutputDir.value
        val mappings  = packageMappingsOffline.value
        val projectName = Keys.name.value
        val result = new IdeaArtifactXmlBuilder(projectName, outputDir).produceArtifact(mappings)
        val file = buildRoot / ".idea" / "artifacts" / s"$projectName.xml"
        IO.write(file, result)
      }
    else Def.task { }
  }
}
