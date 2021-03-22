package org.jetbrains.sbtidea.structure.sbtImpl

import org.jetbrains.sbtidea.structure.{Library, ModuleKey}

import java.io.File

case class SbtIvyLibrary(override val key: ModuleKey,
                                   override val jarFile: File) extends Library