package org.jetbrains.sbtidea.instrumentation

import org.jetbrains.sbtidea.instrumentation.notNullVerification.FailSafeClassReader
import org.jetbrains.sbtidea.instrumentation.threadingModelHelper.{TMHAssertionGenerator, TMHAssertionGenerator2, TMHInstrumenter}
import org.objectweb.asm.ClassWriter

import java.nio.file.{Files, Path}
import java.util

private object ThreadingAnnotationInstrumenter {

  private val Generators: util.Set[? <: TMHAssertionGenerator] = TMHAssertionGenerator2.generators(
    "com/intellij/util/concurrency/ThreadingAssertions",
    "com/intellij/util/concurrency/annotations"
  )

  def instrument(classFile: Path): Unit = {
    val bytes = Files.readAllBytes(classFile)
    val reader = new FailSafeClassReader(bytes)
    // The generated assertion is a zero-argument static ()V call at method entry,
    // which changes neither stack map frames nor the maximum stack size.
    val writer = new ClassWriter(reader, 0)
    if (TMHInstrumenter.instrument(reader, writer, Generators, /*generateLineNumbers =*/ false)) {
      Files.write(classFile, writer.toByteArray)
    }
  }
}
