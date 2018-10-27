package org.jetbrains.sbtidea.tasks.packaging

import java.io.File

final case class ClassesInfo(generated: Array[File], deleted: Array[File])