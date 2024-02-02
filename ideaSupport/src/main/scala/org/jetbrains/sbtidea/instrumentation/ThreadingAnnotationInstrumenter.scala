package org.jetbrains.sbtidea.instrumentation

import org.objectweb.asm.*

import java.nio.file.{Files, Path}

private object ThreadingAnnotationInstrumenter {

  def instrument(classFile: Path): Unit = {
    val bytes = Files.readAllBytes(classFile)
    val reader = new ClassReader(bytes)
    if (requiresInstrumentation(reader)) {
      doInstrumentation(classFile, reader)
    }
  }

  private def requiresInstrumentation(reader: ClassReader): Boolean = {
    val searcher = new AnnotationSearcher()
    reader.accept(searcher, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES)
    searcher.requiresInstrumentation
  }

  private def doInstrumentation(classFile: Path, reader: ClassReader): Unit = {
    val writer = new ClassWriter(reader, 0)
    val instrumenter = new Instrumenter(writer)
    reader.accept(instrumenter, 0)
    Files.write(classFile, writer.toByteArray)
  }

  private val GenerateAssertion = "generateAssertion"

  private val ThreadingAssertionsSignature = "com/intellij/util/concurrency/ThreadingAssertions"

  private val AnnotationClasses: Map[String, String] =
    Map(
      "RequiresBackgroundThread" -> "assertBackgroundThread",
      "RequiresEdt" -> "assertEventDispatchThread",
      "RequiresReadLock" -> "assertReadAccess",
      "RequiresReadLockAbsence" -> "assertNoReadAccess",
      "RequiresWriteLock" -> "assertWriteAccess"
    ).map { case (annotation, method) =>
      val fqcn = s"com.intellij.util.concurrency.annotations.$annotation"
      val signature = fqcn.replace('.', '/')
      val descriptor = s"L$signature;"
      (descriptor, method)
    }

  private final class AnnotationSearcher extends ClassVisitor(Opcodes.ASM9) {
    var requiresInstrumentation: Boolean = false

    override def visitMethod(access: Int, name: String, descriptor: String, signature: String, exceptions: Array[String]): MethodVisitor =
      new MethodVisitor(Opcodes.ASM9) {
        override def visitAnnotation(descriptor: String, visible: Boolean): AnnotationVisitor = {
          if (!requiresInstrumentation) {
            requiresInstrumentation = AnnotationClasses.contains(descriptor)
          }
          null
        }
      }
  }

  private final class Instrumenter(visitor: ClassVisitor) extends ClassVisitor(Opcodes.ASM9, visitor) {
    override def visitMethod(access: Int, name: String, descriptor: String, signature: String, exceptions: Array[String]): MethodVisitor = {
      val methodVisitor = super.visitMethod(access, name, descriptor, signature, exceptions)
      new MethodInstrumenter(methodVisitor)
    }
  }

  private final class MethodInstrumenter(visitor: MethodVisitor) extends MethodVisitor(Opcodes.ASM9, visitor) {
    private var assertionMethod: Option[String] = None

    override def visitAnnotation(descriptor: String, visible: Boolean): AnnotationVisitor = {
      val annotationVisitor = super.visitAnnotation(descriptor, visible)
      if (AnnotationClasses.contains(descriptor)) {
        new AnnotationChecker(annotationVisitor, () => {
          assertionMethod = AnnotationClasses.get(descriptor)
        })
      } else annotationVisitor
    }

    override def visitCode(): Unit = {
      assertionMethod.foreach { method =>
        super.visitMethodInsn(Opcodes.INVOKESTATIC, ThreadingAssertionsSignature, method, "()V", false)
      }
      super.visitCode()
    }
  }

  private final class AnnotationChecker(visitor: AnnotationVisitor, onShouldGenerateAssertion: () => Unit) extends AnnotationVisitor(Opcodes.ASM9, visitor) {
    private var shouldGenerateAssertion: Boolean = true

    override def visit(name: String, value: Any): Unit = {
      super.visit(name, value)
      if (name == GenerateAssertion && value == java.lang.Boolean.FALSE) {
        shouldGenerateAssertion = false
      }
    }

    override def visitEnd(): Unit = {
      super.visitEnd()
      if (shouldGenerateAssertion) {
        onShouldGenerateAssertion()
      }
    }
  }
}
