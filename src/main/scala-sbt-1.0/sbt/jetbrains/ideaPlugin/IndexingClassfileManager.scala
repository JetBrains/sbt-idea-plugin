package sbt.jetbrains.ideaPlugin

import java.util
import java.util.concurrent.ConcurrentHashMap

import org.jetbrains.sbtidea.tasks.packaging.ClassesInfo
import sbt.io.syntax.File
import sbt.jetbrains.ideaPlugin.apiAdapter.SbtCompilationBackCompat._

object IndexingClassfileManager extends ClassFileManager {

  val classesInfo: util.Set[ClassesInfo] = ConcurrentHashMap.newKeySet[ClassesInfo]

  private[this] val generatedStaging = new ThreadLocal[Array[File]] {
    override def initialValue(): Array[File] = Array.empty
  }

  private[this] val deletedStaging = new ThreadLocal[Array[File]] {
    override def initialValue(): Array[File] = Array.empty
  }

  override def delete(classes:    Array[File]): Unit = deletedStaging.set(classes)
  override def generated(classes: Array[File]): Unit = generatedStaging.set(classes)

  override def complete(success: Boolean): Unit = {
    if (success) classesInfo.add(ClassesInfo(generatedStaging.get, deletedStaging.get))

    generatedStaging.remove()
    deletedStaging.remove()
  }

  def apply(unused: IncOptions): ClassFileManager = IndexingClassfileManager
}