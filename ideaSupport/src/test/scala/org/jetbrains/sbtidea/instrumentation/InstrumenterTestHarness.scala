package org.jetbrains.sbtidea.instrumentation

import org.scalatest.Assertions

import java.lang.reflect.InvocationTargetException
import java.net.URLClassLoader
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}
import javax.tools.ToolProvider
import scala.collection.JavaConverters.asScalaIteratorConverter

/**
 * Shared harness for the instrumenter tests: compiles Java fixture sources at test runtime,
 * lets the test instrument the resulting class files and loads them in a throwaway
 * classloader. Loading and invoking the instrumented classes also runs the JVM bytecode
 * verifier over the rewritten methods.
 */
trait InstrumenterTestHarness { this: Assertions =>

  protected def annotationsJar: Path =
    Paths.get(classOf[org.jetbrains.annotations.NotNull].getProtectionDomain.getCodeSource.getLocation.toURI)

  protected def compileFixture(sources: Map[String, String], debugInfo: Boolean = false): Path = {
    val dir = Files.createTempDirectory("instrumentation-test")
    val javaFiles = sources.map { case (relativePath, content) =>
      val file = dir.resolve(relativePath)
      Files.createDirectories(file.getParent)
      Files.write(file, content.getBytes(StandardCharsets.UTF_8))
      file.toString
    }.toSeq
    val args = Seq("-classpath", annotationsJar.toString, "-d", dir.toString) ++
      (if (debugInfo) Seq("-g") else Seq.empty) ++
      javaFiles
    val exitCode = ToolProvider.getSystemJavaCompiler.run(null, null, null, args: _*)
    assert(exitCode == 0, s"javac exited with code $exitCode")
    dir
  }

  protected def classFiles(dir: Path): Seq[Path] = {
    val stream = Files.walk(dir)
    try stream.iterator().asScala.filter(_.toString.endsWith(".class")).toList
    finally stream.close()
  }

  protected def classLoader(dir: Path): ClassLoader =
    new URLClassLoader(Array(dir.toUri.toURL), getClass.getClassLoader)

  protected def loadClass(dir: Path, name: String): Class[_] =
    classLoader(dir).loadClass(name)

  protected def invokeStatic(clazz: Class[_], methodName: String, args: AnyRef*): AnyRef = {
    val method = clazz.getMethods.find(_.getName == methodName)
      .getOrElse(fail(s"Method $methodName not found in ${clazz.getName}"))
    method.invoke(null, args: _*)
  }

  protected def interceptCause(clazz: Class[_], methodName: String, args: AnyRef*): Throwable =
    intercept[InvocationTargetException](invokeStatic(clazz, methodName, args: _*)).getCause
}
