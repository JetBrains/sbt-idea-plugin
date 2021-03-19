package org.jetbrains.sbtidea.tasks

import sbt._

import scala.language.higherKinds

trait SbtIdeaTaskBase[T[_], V] {
  def createTask: Def.Initialize[T[V]]
}

trait SbtIdeaTask[V]      extends SbtIdeaTaskBase[sbt.Task, V]
trait SbtIdeaInputTask[V] extends SbtIdeaTaskBase[sbt.InputTask, V]
