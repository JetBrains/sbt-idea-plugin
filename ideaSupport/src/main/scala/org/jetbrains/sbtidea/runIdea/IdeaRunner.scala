package org.jetbrains.sbtidea.runIdea

import java.io.File
import java.nio.file.Path
import scala.collection.mutable

class IdeaRunner(intellijBaseDirectory: Path,
                 vmOptions: IntellijVMOptions,
                 blocking: Boolean,
                 programArguments: Seq[String] = Seq.empty) extends IntellijAwareRunner(intellijBaseDirectory, blocking) {

  override protected def buildJavaArgs: Seq[String] = {
    val builder = mutable.ArrayBuffer[String]()

    //vm options
    val intellijPlatformJarsFolder = intellijBaseDirectory.resolve("lib")
    builder ++= List("-cp", s"$intellijPlatformJarsFolder${File.separator}*")
    builder += IntellijVMOptions.USE_PATH_CLASS_LOADER
    builder ++= vmOptions.asSeq().filter(_.nonEmpty)
    //main
    builder += IntellijVMOptions.IDEA_MAIN
    //arguments
    builder ++= programArguments

    builder.toList
  }
}
