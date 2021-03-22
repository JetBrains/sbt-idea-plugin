package org.jetbrains.sbtidea.runIdea

import java.io.File
import java.nio.file.Files.newInputStream
import java.nio.file.{Path, Paths}
import java.util.{Locale, Properties}
import java.lang.{ProcessBuilder => JProcessBuilder}

import org.jetbrains.sbtidea.PluginLogger
import org.jetbrains.sbtidea.download.jbr.JbrInstaller
import org.jetbrains.sbtidea.packaging.artifact
import org.jetbrains.sbtidea.{PluginLogger => log}
import org.jetbrains.sbtidea.pathToPathExt
import sbt._

import scala.collection.JavaConverters._

class IdeaRunner(ideaClasspath: Seq[Path],
                 vmOptions: IntellijVMOptions,
                 blocking: Boolean,
                 programArguments: Seq[String] = Seq.empty) {

  def run(): Unit = {
    val processBuilder = new JProcessBuilder()
    val process = processBuilder
      .command(buildCommand)
      .inheritIO()
      .start()
    if (blocking)
      process.waitFor()
  }

  private def extractJBRVersion(home: Path): Option[Int] = try {
    artifact.using(newInputStream(home / "release")) { stream =>
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

  def getBundledJRE: Option[JRE] = {
    val validJars = Set("idea.jar", "platform-api.jar", "platform-impl.jar", "openapi.jar")

    val ideaJar =
      ideaClasspath
        .find(validJars contains _.getFileName.toString)
        .getOrElse(throw new RuntimeException(s"Can't find any of the $validJars in classpath"))

    val maybeJre = sys.props.get("os.name").map {
      case name if name.toLowerCase(Locale.ENGLISH).startsWith("mac") =>
        ideaJar.getParent.getParent / JbrInstaller.JBR_DIR_NAME / "Contents" / "Home"
      case _ =>
        ideaJar.getParent.getParent / "jbr"
    }

    maybeJre.flatMap { root =>
      extractJBRVersion(root)
        .map(JRE(root, _))
    }
  }

  private def detectLocalJRE: JRE = {
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
    case ex: Exception =>
      // before Java 9 system property 'java.specification.version'
      // is of the form '1.major', so return the int after '1.'
      val versionString = System.getProperty("java.specification.version")
      versionString.substring(2).toInt
  }

  private def getJavaExecutable(jre: JRE): Path = {
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

  private def buildCommand: java.util.List[String] = {
    implicit val jre: JRE = getBundledJRE.getOrElse(detectLocalJRE)
    val javaExe = getJavaExecutable(jre)
    val classPath = buildCPString
    (List(
      javaExe.toAbsolutePath.toString,
      "-cp",
      classPath) ++ (vmOptions.asSeq().filter(_.nonEmpty) :+ IntellijVMOptions.IDEA_MAIN) ++ programArguments).asJava
  }

  private def buildCPString: String = ideaClasspath.mkString(File.pathSeparator)
}
