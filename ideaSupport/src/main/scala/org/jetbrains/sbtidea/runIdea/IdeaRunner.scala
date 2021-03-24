package org.jetbrains.sbtidea.runIdea

import org.jetbrains.sbtidea.pathToPathExt

import java.io.File
import java.nio.file.Path

class IdeaRunner(ideaClasspath: Seq[Path],
                 vmOptions: IntellijVMOptions,
                 blocking: Boolean,
                 programArguments: Seq[String] = Seq.empty) extends IntellijAwareRunner(ideaClasspath, blocking) {

  override protected def buildJavaArgs: Seq[String] = {
    val classPath = buildCPString
    List("-cp", classPath) ++
      (vmOptions.asSeq().filter(_.nonEmpty) :+ IntellijVMOptions.IDEA_MAIN) ++
      programArguments
  }

  private def buildCPString: String = ideaClasspath.mkString(File.pathSeparator)
}
