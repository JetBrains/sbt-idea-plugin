package org.jetbrains.sbtidea.runIdea

import org.jetbrains.sbtidea.download.jbr.JbrInstaller
import org.jetbrains.sbtidea.packaging.artifact
import org.jetbrains.sbtidea.{pathToPathExt, PluginLogger => log}
import sbt._

import java.lang.{ProcessBuilder => JProcessBuilder}
import java.nio.file.Files.newInputStream
import java.nio.file.{Path, Paths}
import java.util.{Locale, Properties}
import scala.collection.JavaConverters._

abstract class IntellijAwareRunner(ideaClasspath: Seq[Path], blocking: Boolean) {

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
    val validJars = Set("app.jar", "idea.jar", "platform-api.jar", "platform-impl.jar", "openapi.jar")

    //e.g. ~/.ScalaPluginIU/sdk/221.4165.146/lib/app.jar
    val someJarInIdeaInstallationLibFolder: Path =
      ideaClasspath
        .find(p => validJars.contains(p.getFileName.toString))
        .getOrElse(throw new RuntimeException(s"Can't find any of the $validJars in idea classpath:\n${ideaClasspath.mkString("\n")}"))

    val libFolder = someJarInIdeaInstallationLibFolder.getParent
    val maybeJre = sys.props.get("os.name").map {
      case name if name.toLowerCase(Locale.ENGLISH).startsWith("mac") =>
        libFolder.getParent / JbrInstaller.JBR_DIR_NAME / "Contents" / "Home"
      case _ =>
        libFolder.getParent / "jbr"
    }

    maybeJre.flatMap { root =>
      extractJBRVersion(root)
        .map(JRE(root, _))
    }
  }

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
    case ex: Exception =>
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
    implicit val jre: JRE = getBundledJRE.getOrElse(detectLocalJRE)
    val javaExe = getJavaExecutable(jre)
    val args = buildJavaArgs
    (javaExe.toAbsolutePath.toString +: args).asJava
  }
  
  protected def buildJavaArgs: Seq[String]

  def run(): Int = {
    val processBuilder = new JProcessBuilder()
    val process = processBuilder
      .command(buildFullCommand)
      .inheritIO()
      .start()
    if (blocking)
      process.waitFor()
    else 0
  }

}