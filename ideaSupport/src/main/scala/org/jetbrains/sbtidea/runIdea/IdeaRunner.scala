package org.jetbrains.sbtidea.runIdea

import java.io.File
import java.nio.file.Path

class IdeaRunner(intellijBaseDirectory: Path,
                 vmOptions: IntellijVMOptions,
                 blocking: Boolean,
                 programArguments: Seq[String] = Seq.empty) extends IntellijAwareRunner(intellijBaseDirectory, blocking) {

  override protected def buildJavaArgs: Seq[String] = {
    val intellijPlatformJarsFolder = intellijBaseDirectory.resolve("lib")
    List("-cp", s"$intellijPlatformJarsFolder${File.separator}*") ++
      (vmOptions.asSeq().filter(_.nonEmpty) :+ IntellijVMOptions.IDEA_MAIN) ++
      programArguments
  }
}
