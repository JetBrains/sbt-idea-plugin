package org.jetbrains.sbtidea.runIdea

import java.io.File
import java.nio.file.{Files, Path, Paths}
import java.util.Properties

import org.jetbrains.sbtidea.PluginLogger
import org.jetbrains.sbtidea.packaging.artifact

import scala.collection.JavaConverters._

class IdeaRunner(ideaClasspath: Seq[Path],
                 pluginRoot: Path,
                 vmOptions: IntellijVMOptions,
                 blocking: Boolean,
                 programArguments: Seq[String] = Seq.empty)(implicit log: PluginLogger) {

  def run(): Unit = {
    val processBuilder = new ProcessBuilder()
    val process = processBuilder
      .command(buildCommand)
      .inheritIO()
      .start()
    if (blocking)
      process.waitFor()
  }

  private def getBundledJRE: Option[JRE] = {
    val validJars = Set("idea.jar", "platform-api.jar", "platform-impl.jar", "openapi.jar")
    val ideaJar =
      ideaClasspath
        .find(validJars contains _.getFileName.toString)
        .getOrElse(throw new RuntimeException(s"Can't find any of the $validJars in classpath"))
    val maybeJre = ideaJar.getParent.getParent.resolve("jbr")
    if (!maybeJre.toFile.exists())
      return None
    val version = {
      val props = new Properties()
      try { artifact.using(Files.newInputStream(maybeJre.resolve("release"))) { stream =>
        props.load(stream)
        val versionValue = props.get("JAVA_VERSION").toString
        if (versionValue.startsWith("\"1."))
          versionValue.substring(3, 4).toInt
        else
          versionValue.substring(1, versionValue.indexOf('.')).toInt
      }} catch {
        case e: Exception =>
          log.warn(s"Failed to get JBR version: $e")
          return None
      }
    }
    Some(JRE(maybeJre, version))
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
        Paths.get(System.getProperties.getProperty("java.home"), "bin", "java.exe")
      else
        Paths.get(System.getProperties.getProperty("java.home"), "bin", "java")
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
      classPath) ++ (vmOptions.asSeq :+ IntellijVMOptions.IDEA_MAIN) ++ programArguments).asJava
  }

  private def buildCPString: String = ideaClasspath.mkString(File.pathSeparator)
}
