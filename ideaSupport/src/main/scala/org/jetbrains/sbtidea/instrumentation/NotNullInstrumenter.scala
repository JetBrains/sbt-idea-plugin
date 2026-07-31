package org.jetbrains.sbtidea.instrumentation

import org.jetbrains.sbtidea.instrumentation.notNullVerification.{FailSafeClassReader, NotNullVerifyingInstrumenter}
import org.objectweb.asm.{ClassWriter, Opcodes}

import java.nio.file.{Files, Path}

private object NotNullInstrumenter {

  def instrument(classFile: Path, notNullAnnotations: Seq[String], classpathLoader: ClassLoader): Unit = {
    val bytes = Files.readAllBytes(classFile)
    val reader = new FailSafeClassReader(bytes)
    val version = classFileVersion(reader)
    if (reader.getClassName != "module-info" && (version & 0xFFFF) >= Opcodes.V1_5) {
      // COMPUTE_FRAMES resolves common superclasses of the classes referenced in the rewritten methods,
      // which must happen against the project classpath rather than this plugin's own classpath.
      val writer = new ClassWriter(reader, asmClassWriterFlags(version)) {
        override def getClassLoader: ClassLoader = classpathLoader
      }
      if (NotNullVerifyingInstrumenter.processClassFile(reader, writer, notNullAnnotations.toArray)) {
        Files.write(classFile, writer.toByteArray)
      }
    }
  }

  /** Class file version in the `minor << 16 | major` format (see `com.intellij.compiler.instrumentation.InstrumenterClassWriter`). */
  private def classFileVersion(reader: FailSafeClassReader): Int =
    reader.readInt(4)

  private def asmClassWriterFlags(version: Int): Int =
    if ((version & 0xFFFF) >= Opcodes.V1_6) ClassWriter.COMPUTE_FRAMES else ClassWriter.COMPUTE_MAXS
}
