package org.jetbrains.sbtidea.download

import java.nio.file.{Files, Paths}

import org.jetbrains.sbtidea.Keys.String2Plugin
import org.jetbrains.sbtidea.{Keys, PluginLogger}
import org.scalatest.{FunSuite, Ignore, Matchers}

@Ignore
final class CommunityIdeaUpdaterTest extends FunSuite with Matchers with IdeaMock {

  private val logger = new PluginLogger {
    override def info(msg: => String): Unit = println(msg)
    override def warn(msg: => String): Unit = println(msg)
    override def error(msg: => String): Unit = println(msg)
  }

  test("IdeaUpdater Integration Test") {
    val dumbModeKey = "IdeaUpdater.dumbMode"
    sys.props += dumbModeKey -> "idea"
//    val tmpDir = Files.createTempDirectory("IdeaUpdaterTest")
    val tmpDir = Paths.get("/", "tmp", "IdeaUpdater")
    Files.createDirectories(tmpDir.resolve("plugins"))
    val updater = new CommunityIdeaUpdater(tmpDir, logger)
    updater.updateIdeaAndPlugins(
      BuildInfo.apply("192.6262.9", Keys.IdeaEdition.Community),
      "mobi.hsz.idea.gitignore".toPlugin :: "org.intellij.scala:2019.2.1144:Nightly".toPlugin :: Nil
    )
//    sbt.IO.delete(tmpDir.toFile)
    sys.props -= dumbModeKey
  }

  test("install mock IDEA test") {
    val installDir = installIdeaMock
    installDir.toFile.exists() shouldBe true
    installDir.toFile.list() should contain allElementsOf Seq("lib", "bin", "plugins", "build.txt")
  }


}
