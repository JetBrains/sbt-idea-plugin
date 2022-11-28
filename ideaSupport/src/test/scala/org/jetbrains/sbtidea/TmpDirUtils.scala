package org.jetbrains.sbtidea

import java.nio.file.attribute.FileAttribute
import java.nio.file.{Files, Path}

import org.jetbrains.sbtidea.download.NioUtils

trait TmpDirUtils {

  def createTempFile(prefix: String, suffix: String, fileAttributes: FileAttribute[?]*): Path = {
    val res = Files.createTempFile(prefix, suffix, fileAttributes *)
    TmpDirUtils.allocatedTmpDirs += res
    res
  }

  def newTmpDir: Path = {
    val dir = Files.createTempDirectory(getClass.getName)
    TmpDirUtils.allocatedTmpDirs += dir
    dir
  }
}

object TmpDirUtils {
  private val allocatedTmpDirs = new scala.collection.mutable.ListBuffer[Path]()
  Runtime.getRuntime.addShutdownHook(new Thread() {
    override def run(): Unit = allocatedTmpDirs.foreach(NioUtils.delete)
  })
}
