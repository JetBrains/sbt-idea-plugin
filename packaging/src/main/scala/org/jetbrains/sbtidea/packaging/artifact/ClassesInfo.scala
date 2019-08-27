package org.jetbrains.sbtidea.packaging.artifact

import java.io.File

final case class ClassesInfo(generated: Array[File], deleted: Array[File])