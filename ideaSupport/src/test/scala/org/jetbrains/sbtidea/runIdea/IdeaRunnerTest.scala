package org.jetbrains.sbtidea.runIdea

import org.jetbrains.sbtidea.IntellijTestRuntimeDirectories.RuntimeDirectories
import org.jetbrains.sbtidea.download.jbr.JbrInstaller
import org.jetbrains.sbtidea.productInfo.ProductInfoExtraDataProvider
import org.jetbrains.sbtidea.{CapturingLogger, IntelliJPlatform, IntellijTestRuntimeDirectories, PluginLogger, TmpDirUtils}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import java.io.File
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path, Paths}
import scala.collection.JavaConverters.*

class IdeaRunnerTest extends AnyFunSuite with Matchers with TmpDirUtils {
  private val javaExecutable = Paths.get(System.getProperty("java.home"), "bin", "java").toAbsolutePath

  test("preparing an IDEA launch keeps JVM and application arguments separate without logging or creating directories") {
    val root = newTmpDir
    val provider = new StubProductInfo(root)
    val builder = vmOptionsBuilder(root, provider)
    val directories = RuntimeDirectories(root.resolve("custom system"), root.resolve("shared config"), root.resolve("other logs"))
    val options = CustomIntellijVMOptions(
      debugInfo = None, extraOptions = Seq("-Didea.system.path=stale", "-Dretained=value with spaces"))
    val applicationArguments = Seq("traverseUI", "/output with spaces", "-Didea.system.path=application argument")
    val runner = new IdeaRunner(root.resolve("sdk"), provider, options,
      builder.withRuntimeDirectories(directories), blocking = true, discardOutput = false,
      programArguments = applicationArguments)

    val output = CapturingLogger.captureLog {
      val launch = runner.prepareLaunch()
      launch.jvmOptions.take(3) shouldBe Seq(
        "-cp", provider.bootJars.mkString(File.pathSeparator), IntellijVMOptionsBuilder.PathClassLoaderOption)
      launch.jvmOptions.filter(IntellijTestRuntimeDirectories.isIntellijDirectoryOption) shouldBe directories.jvmOptions
      launch.jvmOptions should contain("-Dretained=value with spaces")
      launch.jvmOptions should not contain ""
      launch.mainClass shouldBe "com.intellij.idea.Main"
      launch.programArguments shouldBe applicationArguments
      launch.arguments.takeRight(applicationArguments.size + 1) shouldBe
        Seq("com.intellij.idea.Main") ++ applicationArguments
    }
    output shouldBe empty
    withClue("Preparation must read the boot classpath exactly once: ") {
      provider.classpathReads shouldBe 1
    }
    Seq(root.resolve("sdk"), root.resolve("home"), directories.system, directories.config, directories.log)
      .foreach(path => assert(!Files.exists(path), s"Launch preparation must not create $path"))
  }

  test("development logging and execution share one prepared launch and ignore option-like application arguments") {
    assertRuntimeLogging(purpose = "development")
  }

  test("indexing logging and execution share one prepared launch and ignore option-like application arguments") {
    assertRuntimeLogging(purpose = "build-index")
  }

  test("the generic runner retains jar launches and existing command construction overrides") {
    val root = newTmpDir
    val sdk = sdkWithRuntime(root)
    val arguments = Seq("-jar", root.resolve("verifier with spaces.jar").toString, "check-plugin", "-Dapplication.option=value")
    class ExposedRunner extends IntellijAwareRunner(sdk, blocking = true, discardOutput = false) {
      override protected def getJavaExecutable(jre: JRE): Path = javaExecutable
      override protected def buildJavaArgs: Seq[String] = arguments
      def command: Seq[String] = buildFullCommand.asScala.toVector
    }
    new ExposedRunner().command shouldBe javaExecutable.toString +: arguments

    val source = writeProbe(root)
    val observed = root.resolve("generic-observed.txt")
    val overridden = new IntellijAwareRunner(root.resolve("unavailable sdk"), blocking = true, discardOutput = false) {
      override protected def buildJavaArgs: Seq[String] = fail("The existing full-command override must remain authoritative")
      override protected def buildFullCommand: java.util.List[String] = Seq(
        javaExecutable.toString, "-Didea.system.path=system", "-Didea.config.path=config", "-Didea.log.path=logs",
        source.toString, observed.toString,
      ).asJava
    }
    CapturingLogger.captureLog {
      overridden.run() shouldBe 0
    } shouldBe empty
    Files.readAllLines(observed).asScala.toVector shouldBe Seq("system", "config", "logs")
    assert(!Files.exists(root.resolve("unavailable sdk")), "The command override must not require SDK resolution")
  }

  private def assertRuntimeLogging(purpose: String): Unit = {
    val root = Files.createDirectories(newTmpDir.resolve("paths with spaces"))
    val source = writeProbe(root)
    val sdk = sdkWithRuntime(root)
    val provider = new StubProductInfo(root)
    val paths = Seq("system", "config", "logs").map(root.resolve)
    // ProcessBuilder does not invoke a shell: quotes in raw JVM properties are literal.
    val values = Seq(paths.head.toString, "\"" + paths(1) + "\"", paths(2).toString)
    val options = Seq("system", "config", "log").zip(values).map { case (key, value) => s"-Didea.$key.path=$value" }
    val observed = root.resolve("observed.txt")
    val applicationArguments = Seq("-Didea.system.path=application argument", "com.intellij.idea.Main", "ordinary argument")
    var preparations = 0
    val runner = new IdeaRunner(sdk, provider, CustomIntellijVMOptions(),
      vmOptionsBuilder(root, provider), blocking = true, discardOutput = false) {
      override private[runIdea] def prepareLaunch(): IdeaRunner.PreparedLaunch = {
        preparations += 1
        IdeaRunner.PreparedLaunch("-Didea.system.path=stale" +: options, source.toString,
          observed.toString +: applicationArguments)
      }

      override protected def getJavaExecutable(jre: JRE): Path = javaExecutable
    }

    val output = CapturingLogger.captureLog {
      runner.runWithRuntimeLogging(purpose, "root", PluginLogger) shouldBe 0
    }
    withClue("Logging and execution must share a single preparation: ") {
      preparations shouldBe 1
    }
    val actual = Files.readAllLines(observed).asScala.toVector
    actual.take(3) shouldBe values
    actual.drop(3) shouldBe applicationArguments
    output shouldBe Seq(
      s"[info] IntelliJ runtime ($purpose, root)",
      s"[info]   System: ${values.head}",
      s"[info]   Config: ${values(1)}",
      s"[info]   Logs: ${values(2)}",
    )
    paths.foreach(path => assert(!Files.exists(path), s"Reporting must not create runtime directory $path"))
  }

  private def vmOptionsBuilder(root: Path, provider: ProductInfoExtraDataProvider): IntellijVMOptionsBuilder =
    IntellijVMOptionsBuilder(IntelliJPlatform.Idea, provider, root.resolve("plugin"), root.resolve("home"), root.resolve("sdk"))

  private def sdkWithRuntime(root: Path): Path = {
    val sdk = root.resolve("sdk")
    val jbr = Files.createDirectories(JbrInstaller.getJbrHome(sdk))
    Files.write(jbr.resolve("release"), "JAVA_VERSION=\"17.0.13\"\n".getBytes(UTF_8))
    sdk
  }

  private def writeProbe(root: Path): Path = {
    val source = root.resolve("Probe.java")
    Files.write(source, """
      |import java.nio.file.*;
      |import java.util.*;
      |public class Probe {
      |  public static void main(String[] args) throws Exception {
      |    List<String> observed = new ArrayList<>(Arrays.asList(
      |      System.getProperty("idea.system.path"), System.getProperty("idea.config.path"),
      |      System.getProperty("idea.log.path")));
      |    observed.addAll(Arrays.asList(args).subList(1, args.length));
      |    Files.write(Paths.get(args[0]), observed);
      |  }
      |}
      |""".stripMargin.getBytes(UTF_8))
    source
  }

  private final class StubProductInfo(root: Path) extends ProductInfoExtraDataProvider {
    val bootJars: Seq[File] = Seq(root.resolve("first jar.jar").toFile, root.resolve("second.jar").toFile)
    var classpathReads: Int = 0
    override def vmOptionsAll: Seq[String] = Seq("-Didea.config.path=SDK default", "")
    override def bootClasspathJars: Seq[File] = {
      classpathReads += 1
      bootJars
    }
    override def productModulesJars: Seq[File] = fail("Launch preparation must not resolve product modules")
    override def testFrameworkJars: Seq[File] = fail("Launch preparation must not resolve test-framework jars")
  }
}
