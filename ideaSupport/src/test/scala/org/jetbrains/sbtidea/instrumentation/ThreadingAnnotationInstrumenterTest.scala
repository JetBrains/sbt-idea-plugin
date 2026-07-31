package org.jetbrains.sbtidea.instrumentation

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import java.nio.file.{Files, Path}
import scala.collection.JavaConverters.asScalaBufferConverter

/**
 * Tests the threading annotation instrumentation against stub versions of the IntelliJ Platform
 * annotation and assertion classes, compiled at test runtime. The stub `ThreadingAssertions`
 * methods record their invocations, so the tests can assert exactly which assertion was
 * generated for which annotation.
 */
class ThreadingAnnotationInstrumenterTest extends AnyFunSuite with Matchers with InstrumenterTestHarness {

  test("each threading annotation generates a call to its ThreadingAssertions method") {
    val dir = compileAndInstrument(
      "Annotated.java" ->
        """import com.intellij.util.concurrency.annotations.*;
          |public class Annotated {
          |  @RequiresEdt
          |  public static void edt() {}
          |  @RequiresBackgroundThread
          |  public static void background() {}
          |  @RequiresReadLock
          |  public static void readLock() {}
          |  @RequiresReadLockAbsence
          |  public static void readLockAbsence() {}
          |  @RequiresWriteLock
          |  public static void writeLock() {}
          |}""".stripMargin
    )
    val loader = classLoader(dir)
    val clazz = loader.loadClass("Annotated")
    Seq("edt", "background", "readLock", "readLockAbsence", "writeLock")
      .foreach(clazz.getMethod(_).invoke(null))
    recordedCalls(loader) shouldBe Seq(
      "assertEventDispatchThread",
      "assertBackgroundThread",
      "softAssertReadAccess",
      "assertNoReadAccess",
      "assertWriteAccess"
    )
  }

  test("the assertion is generated before the method body") {
    val dir = compileAndInstrument(
      "BodyOrder.java" ->
        """import com.intellij.util.concurrency.ThreadingAssertions;
          |import com.intellij.util.concurrency.annotations.RequiresEdt;
          |public class BodyOrder {
          |  @RequiresEdt
          |  public static void test() { ThreadingAssertions.calls.add("body"); }
          |}""".stripMargin
    )
    val loader = classLoader(dir)
    loader.loadClass("BodyOrder").getMethod("test").invoke(null)
    recordedCalls(loader) shouldBe Seq("assertEventDispatchThread", "body")
  }

  test("generateAssertion = false disables the instrumentation") {
    val dir = compileThreadingFixture(
      "OptedOut.java" ->
        """import com.intellij.util.concurrency.annotations.RequiresEdt;
          |public class OptedOut {
          |  @RequiresEdt(generateAssertion = false)
          |  public static void test() {}
          |}""".stripMargin
    )
    val classFile = dir.resolve("OptedOut.class")
    val before = Files.readAllBytes(classFile)
    ThreadingAnnotationInstrumenter.instrument(classFile)
    val after = Files.readAllBytes(classFile)
    assert(java.util.Arrays.equals(before, after), "class file must not be modified")
    val loader = classLoader(dir)
    loader.loadClass("OptedOut").getMethod("test").invoke(null)
    recordedCalls(loader) shouldBe empty
  }

  test("classes without threading annotations are not modified") {
    val dir = compileThreadingFixture(
      "Unannotated.java" ->
        """public class Unannotated {
          |  public static void test() {}
          |}""".stripMargin
    )
    val classFile = dir.resolve("Unannotated.class")
    val before = Files.readAllBytes(classFile)
    ThreadingAnnotationInstrumenter.instrument(classFile)
    val after = Files.readAllBytes(classFile)
    assert(java.util.Arrays.equals(before, after), "class file must not be modified")
  }

  test("a method with two threading annotations gets only one assertion (matching IntelliJ's instrumenter)") {
    val dir = compileAndInstrument(
      "DoubleAnnotated.java" ->
        """import com.intellij.util.concurrency.annotations.*;
          |public class DoubleAnnotated {
          |  @RequiresEdt
          |  @RequiresWriteLock
          |  public static void test() {}
          |}""".stripMargin
    )
    val loader = classLoader(dir)
    loader.loadClass("DoubleAnnotated").getMethod("test").invoke(null)
    recordedCalls(loader) should have size 1
  }

  private val ThreadingAssertionsSource: (String, String) =
    "com/intellij/util/concurrency/ThreadingAssertions.java" ->
      """package com.intellij.util.concurrency;
        |import java.util.ArrayList;
        |import java.util.List;
        |public class ThreadingAssertions {
        |  public static final List<String> calls = new ArrayList<>();
        |  public static void assertEventDispatchThread() { calls.add("assertEventDispatchThread"); }
        |  public static void assertBackgroundThread() { calls.add("assertBackgroundThread"); }
        |  public static void softAssertReadAccess() { calls.add("softAssertReadAccess"); }
        |  public static void assertNoReadAccess() { calls.add("assertNoReadAccess"); }
        |  public static void assertWriteAccess() { calls.add("assertWriteAccess"); }
        |}""".stripMargin

  private val AnnotationSources: Seq[(String, String)] =
    Seq("RequiresEdt", "RequiresBackgroundThread", "RequiresReadLock", "RequiresReadLockAbsence", "RequiresWriteLock")
      .map { name =>
        s"com/intellij/util/concurrency/annotations/$name.java" ->
          s"""package com.intellij.util.concurrency.annotations;
             |import java.lang.annotation.*;
             |@Retention(RetentionPolicy.CLASS)
             |@Target({ElementType.METHOD, ElementType.CONSTRUCTOR})
             |public @interface $name {
             |  boolean generateAssertion() default true;
             |}""".stripMargin
      }

  private def compileThreadingFixture(fixtureSources: (String, String)*): Path =
    compileFixture((AnnotationSources ++ Seq(ThreadingAssertionsSource) ++ fixtureSources).toMap)

  private def compileAndInstrument(fixtureSources: (String, String)*): Path = {
    val dir = compileThreadingFixture(fixtureSources: _*)
    classFiles(dir).foreach(ThreadingAnnotationInstrumenter.instrument)
    dir
  }

  /** Reads the invocations recorded by the stub `ThreadingAssertions` in the given fixture classloader. */
  private def recordedCalls(loader: ClassLoader): Seq[String] =
    loader.loadClass("com.intellij.util.concurrency.ThreadingAssertions")
      .getField("calls").get(null).asInstanceOf[java.util.List[String]].asScala.toList
}
