package org.jetbrains.sbtidea.instrumentation

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import java.lang.reflect.InvocationTargetException
import java.net.URLClassLoader
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}
import javax.tools.ToolProvider
import scala.collection.JavaConverters.asScalaIteratorConverter

/**
 * Modeled on IntelliJ IDEA's `com.intellij.java.compiler.notNullVerification.NotNullVerifyingInstrumenterTest`:
 * compile small annotated Java fixtures at test runtime, instrument the class files, load them
 * in a throwaway classloader and assert on the thrown exceptions. Loading and invoking the
 * instrumented classes also runs the JVM bytecode verifier over the rewritten methods.
 */
class NotNullInstrumenterTest extends AnyFunSuite with Matchers {

  private val NotNullFqn = "org.jetbrains.annotations.NotNull"

  test("@NotNull parameter check without debug info uses the parameter index") {
    val dir = compileAndInstrument(Map(
      "SimpleParam.java" ->
        """import org.jetbrains.annotations.NotNull;
          |public class SimpleParam {
          |  public static void test(@NotNull Object o) {}
          |}""".stripMargin
    ))
    val clazz = loadClass(dir, "SimpleParam")
    invokeStatic(clazz, "test", new Object) // non-null argument must pass
    val cause = interceptCause(clazz, "test", null)
    cause shouldBe an[IllegalArgumentException]
    cause.getMessage shouldBe "Argument 0 for @NotNull parameter of SimpleParam.test must not be null"
  }

  test("@NotNull parameter check with debug info uses the parameter name") {
    val dir = compileAndInstrument(Map(
      "SimpleParam.java" ->
        """import org.jetbrains.annotations.NotNull;
          |public class SimpleParam {
          |  public static void test(@NotNull Object important) {}
          |}""".stripMargin
    ), debugInfo = true)
    val clazz = loadClass(dir, "SimpleParam")
    val cause = interceptCause(clazz, "test", null)
    cause shouldBe an[IllegalArgumentException]
    cause.getMessage shouldBe "Argument for @NotNull parameter 'important' of SimpleParam.test must not be null"
  }

  test("@NotNull return value check throws IllegalStateException") {
    val dir = compileAndInstrument(Map(
      "SimpleReturn.java" ->
        """import org.jetbrains.annotations.NotNull;
          |public class SimpleReturn {
          |  @NotNull
          |  public static Object test(Object o) { return o; }
          |}""".stripMargin
    ))
    val clazz = loadClass(dir, "SimpleReturn")
    invokeStatic(clazz, "test", new Object) // non-null return value must pass
    val cause = interceptCause(clazz, "test", null)
    cause shouldBe an[IllegalStateException]
    cause.getMessage shouldBe "@NotNull method SimpleReturn.test must not return null"
  }

  test("custom message given as the annotation value is used verbatim") {
    val dir = compileAndInstrument(Map(
      "CustomMessage.java" ->
        """import org.jetbrains.annotations.NotNull;
          |public class CustomMessage {
          |  public static void test(@NotNull("null is not allowed here") Object o) {}
          |}""".stripMargin
    ))
    val cause = interceptCause(loadClass(dir, "CustomMessage"), "test", null)
    cause shouldBe an[IllegalArgumentException]
    cause.getMessage shouldBe "null is not allowed here"
  }

  test("custom annotation from the configured list with a custom exception class") {
    val dir = compileAndInstrument(Map(
      "MyNotNull.java" ->
        """import java.lang.annotation.*;
          |@Retention(RetentionPolicy.CLASS)
          |@Target({ElementType.METHOD, ElementType.PARAMETER})
          |@interface MyNotNull {
          |  String value() default "";
          |  Class<? extends Exception> exception() default Exception.class;
          |}""".stripMargin,
      "MyException.java" ->
        """public class MyException extends RuntimeException {
          |  public MyException(String message) { super(message); }
          |}""".stripMargin,
      "CustomException.java" ->
        """public class CustomException {
          |  public static void test(@MyNotNull(exception = MyException.class) Object o) {}
          |}""".stripMargin
    ), annotations = Seq("MyNotNull"))
    val cause = interceptCause(loadClass(dir, "CustomException"), "test", null)
    cause.getClass.getName shouldBe "MyException"
    cause.getMessage shouldBe "Argument 0 for @MyNotNull parameter of CustomException.test must not be null"
  }

  test("enum constructor parameters are checked despite the synthetic name/ordinal parameters") {
    val dir = compileAndInstrument(Map(
      "TestEnum.java" ->
        """import org.jetbrains.annotations.NotNull;
          |public enum TestEnum {
          |  OK("ok"), BAD(null);
          |  TestEnum(@NotNull String s) {}
          |}""".stripMargin
    ))
    val error = intercept[ExceptionInInitializerError](Class.forName("TestEnum", true, classLoader(dir)))
    error.getCause shouldBe an[IllegalArgumentException]
    error.getCause.getMessage should fullyMatch regex "Argument \\d+ for @NotNull parameter of TestEnum\\.<init> must not be null"
  }

  test("non-static inner class constructor parameters are checked despite the synthetic outer-instance parameter") {
    val dir = compileAndInstrument(Map(
      "Outer.java" ->
        """import org.jetbrains.annotations.NotNull;
          |public class Outer {
          |  public class Inner {
          |    public Inner(@NotNull String s) {}
          |  }
          |  public static void create(String s) { new Outer().new Inner(s); }
          |}""".stripMargin
    ))
    val clazz = loadClass(dir, "Outer")
    invokeStatic(clazz, "create", "not null")
    val cause = interceptCause(clazz, "create", null)
    cause shouldBe an[IllegalArgumentException]
    cause.getMessage should fullyMatch regex "Argument \\d+ for @NotNull parameter of Outer\\$Inner\\.<init> must not be null"
  }

  test("no check is generated when the returned value is provably non-null") {
    val dir = compileFixture(Map(
      "NewObject.java" ->
        """import org.jetbrains.annotations.NotNull;
          |public class NewObject {
          |  @NotNull
          |  public static Object test() { return new Object(); }
          |}""".stripMargin
    ))
    val classFile = dir.resolve("NewObject.class")
    val before = Files.readAllBytes(classFile)
    instrumentAll(dir, Seq(NotNullFqn))
    val after = Files.readAllBytes(classFile)
    assert(java.util.Arrays.equals(before, after), "class file must not be modified")
    invokeStatic(loadClass(dir, "NewObject"), "test")
  }

  test("Kotlin bytecode is skipped (kotlinc generates its own nullability assertions)") {
    val dir = compileFixture(Map(
      "kotlin/Metadata.java" ->
        """package kotlin;
          |import java.lang.annotation.*;
          |@Retention(RetentionPolicy.RUNTIME)
          |@Target(ElementType.TYPE)
          |public @interface Metadata {}""".stripMargin,
      "KotlinLike.java" ->
        """@kotlin.Metadata
          |public class KotlinLike {
          |  public static void test(@org.jetbrains.annotations.NotNull Object o) {}
          |}""".stripMargin
    ))
    val classFile = dir.resolve("KotlinLike.class")
    val before = Files.readAllBytes(classFile)
    instrumentAll(dir, Seq(NotNullFqn))
    val after = Files.readAllBytes(classFile)
    assert(java.util.Arrays.equals(before, after), "class file must not be modified")
    invokeStatic(loadClass(dir, "KotlinLike"), "test", null) // no assertion is generated
  }

  private def annotationsJar: Path =
    Paths.get(classOf[org.jetbrains.annotations.NotNull].getProtectionDomain.getCodeSource.getLocation.toURI)

  private def compileFixture(sources: Map[String, String], debugInfo: Boolean = false): Path = {
    val dir = Files.createTempDirectory("notnull-instrumentation-test")
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

  private def instrumentAll(dir: Path, annotations: Seq[String]): Unit = {
    val loader = new URLClassLoader(Array(dir.toUri.toURL, annotationsJar.toUri.toURL))
    try {
      val stream = Files.walk(dir)
      val classFiles = try stream.iterator().asScala.filter(_.toString.endsWith(".class")).toList finally stream.close()
      classFiles.foreach(NotNullInstrumenter.instrument(_, annotations, loader))
    } finally {
      loader.close()
    }
  }

  private def compileAndInstrument(
    sources: Map[String, String],
    annotations: Seq[String] = Seq(NotNullFqn),
    debugInfo: Boolean = false
  ): Path = {
    val dir = compileFixture(sources, debugInfo)
    instrumentAll(dir, annotations)
    dir
  }

  private def classLoader(dir: Path): ClassLoader =
    new URLClassLoader(Array(dir.toUri.toURL), getClass.getClassLoader)

  private def loadClass(dir: Path, name: String): Class[_] =
    classLoader(dir).loadClass(name)

  private def invokeStatic(clazz: Class[_], methodName: String, args: AnyRef*): AnyRef = {
    val method = clazz.getMethods.find(_.getName == methodName)
      .getOrElse(fail(s"Method $methodName not found in ${clazz.getName}"))
    method.invoke(null, args: _*)
  }

  private def interceptCause(clazz: Class[_], methodName: String, args: AnyRef*): Throwable =
    intercept[InvocationTargetException](invokeStatic(clazz, methodName, args: _*)).getCause
}
