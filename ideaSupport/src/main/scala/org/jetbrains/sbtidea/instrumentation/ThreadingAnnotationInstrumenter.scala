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
    // Line number generation matches IDE-driven JPS builds. While the standalone JPS builder defaults
    // "tmh.generate.line.numbers" to false, the DevKit plugin injects -Dtmh.generate.line.numbers=true into every
    // build process it spawns (the registry key defaults to true in intellij.devkit.core.xml). The generated
    // assertion is annotated with the line number of the start of the method, producing better stack traces when
    // the assertion throws.
    if (TMHInstrumenter.instrument(reader, writer, Generators, /*generateLineNumbers =*/ true)) {
      Files.write(classFile, writer.toByteArray)
    }
  }
}
