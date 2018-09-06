package org.jetbrains.sbtidea.tasks.packaging

import java.nio.file.{FileSystem, Files, Path}

import sbt.Keys.TaskStreams

class DynamicPackager(myOutput: Path,
                      shader: ClassShader,
                      incrementalCache: IncrementalCache)
                     (implicit private val streams: TaskStreams) extends SimplePackager(myOutput, shader, incrementalCache) {

  override protected def outputExists(path: Path): Boolean = Files.exists(path)

  override protected def createOutputFS(output: Path): FileSystem = {
    if (!output.toFile.exists())
      Files.createDirectories(output)
    output.getFileSystem
  }

  override protected def createOutput(srcPath: Path, output: Path, outputFS: FileSystem): Path = {
    if (srcPath.toString.contains("META-INF"))
      myOutput.getParent.resolve(srcPath)
    else
      myOutput.resolve(srcPath)
  }

}
