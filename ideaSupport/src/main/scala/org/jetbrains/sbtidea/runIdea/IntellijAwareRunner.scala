package org.jetbrains.sbtidea.runIdea

import org.jetbrains.sbtidea.PluginLogger as log
import org.jetbrains.sbtidea.download.jbr.JbrInstaller
import org.jetbrains.sbtidea.runIdea.IntellijAwareRunner.getBundledJRE
import sbt.*

import java.lang.ProcessBuilder as JProcessBuilder
import java.nio.file.Files.newInputStream
import java.nio.file.{Files, Path, Paths}
import java.util.Properties
import scala.collection.JavaConverters.*
import scala.util.Using

abstract class IntellijAwareRunner(intellijBaseDirectory: Path, blocking: Boolean, discardOutput: Boolean) {

  //NOTE: most of these methods could be extracted to companion object

  protected def detectLocalJRE: JRE = {
    val localHome = Paths.get(System.getProperties.getProperty("java.home"))
    val localVersion = getMajorVersion
    JRE(localHome, localVersion)
  }

  private def getMajorVersion: Int = try { // use Java 9+ version API via reflection, so it can be compiled for older versions
    val runtime_version = classOf[Runtime].getMethod("version")
    val version = runtime_version.invoke(null)
    val version_major = runtime_version.getReturnType.getMethod("feature")
    version_major.invoke(version).asInstanceOf[Int]
  } catch {
    case _: Exception =>
      // before Java 9 system property 'java.specification.version'
      // is of the form '1.major', so return the int after '1.'
      val versionString = System.getProperty("java.specification.version")
      versionString.substring(2).toInt
  }

  protected def getJavaExecutable(jre: JRE): Path = {
    val path =
      if (System.getProperty("os.name").startsWith("Win"))
        jre.root / "bin" / "java.exe"
      else
        jre.root / "bin" / "java"
    if (path.toFile.exists())
      path
    else
      throw new RuntimeException(s"Failed to locate java executable from $jre")
  }

  protected def buildFullCommand: java.util.List[String] = {
    val bundledJre = getBundledJRE(intellijBaseDirectory)
    implicit val jre: JRE = bundledJre.getOrElse(detectLocalJRE)
    val javaExe = getJavaExecutable(jre)
    val args = buildJavaArgs
    (javaExe.toAbsolutePath.toString +: args).asJava
  }
  
  protected def buildJavaArgs: Seq[String]

  def run(): Int = {
    val processBuilder = {
      val builder = new JProcessBuilder()
      builder.command(buildFullCommand)
      if (discardOutput) {
        // Would have been nice to use `.redirectOutput(ProcessBuilder.Redirect.DISCARD)`,
        // but it is only available on JDK 9+.
        builder
          .redirectOutput(Files.createTempFile("idea-runner-out", ".log").toFile)
          .redirectError(Files.createTempFile("idea-runner-err", ".log").toFile)
      } else {
        builder.inheritIO()
      }
      builder
    }
    val process = processBuilder.start()
    if (blocking)
      process.waitFor()
    else 0
  }

}

object IntellijAwareRunner {
  def getBundledJRE(intellijBaseDirectory: Path): Option[JRE] = {
    val jbrHome = JbrInstaller.getJbrHome(intellijBaseDirectory)

    val jbrVersion = extractJBRVersion(jbrHome)
    jbrVersion.map(JRE(jbrHome, _))
  }

  private def extractJBRVersion(home: Path): Option[Int] = try {
    Using.resource(newInputStream(home / "release")) { stream =>
      val props = new Properties()
      props.load(stream)
      val versionValue = props.get("JAVA_VERSION").toString
      if (versionValue.startsWith("\"1."))
        Some(versionValue.substring(3, 4).toInt)
      else
        Some(versionValue.substring(1, versionValue.indexOf('.')).toInt)
    }
  } catch {
    case e: Exception =>
      log.warn(s"Failed to get JBR version: $e")
      None
  }
}