package org.jetbrains.sbtidea.tasks

import org.jetbrains.sbtidea.Keys.IdeaConfigBuildingOptions
import org.jetbrains.sbtidea.runIdea.{IntellijAwareRunner, IntellijVMOptions}
import org.jetbrains.sbtidea.tasks.IdeaConfigBuilder.{JUnit3JarName, pathPattern, pluginsPattern}
import org.jetbrains.sbtidea.{ClasspathStrategy, pathToPathExt, PluginLogger => log}
import sbt._

import java.io.File
import java.nio.file.{Path, Paths}
import java.util.regex.Pattern
import scala.annotation.tailrec
import scala.collection.mutable
import scala.math.Ordering.Implicits.infixOrderingOps

/**
  * @param pluginRoots contains only those plugins which are listed in the project IntelliJ plugin dependencies and their transitive dependencies
  */
class IdeaConfigBuilder(moduleName: String,
                        configName: String,
                        intellijVMOptions: IntellijVMOptions,
                        dataDir: File,
                        intellijBaseDir: File,
                        dotIdeaFolder: File,
                        pluginAssemblyDir: File,
                        ownProductDirs: Seq[File],
                        pluginRoots: Seq[File],
                        extraJUnitTemplateClasspath: Seq[File],
                        options: IdeaConfigBuildingOptions,
                        classpathStrategy: ClasspathStrategy = ClasspathStrategy.Since_203_5251) {
  private val runConfigDir = dotIdeaFolder / "runConfigurations"

  private val intellijPlatformJarsFolder: File = intellijBaseDir / "lib"

  private val IDEA_ROOT_KEY = "idea.installation.dir"

  def build(): Unit = {
    if (options.generateDefaultRunConfig)
      writeToFile(runConfigDir / s"$configName.xml", buildRunConfigurationXML())
    if (options.generateNoPCEConfiguration)
      writeToFile(runConfigDir / s"$configName-noPCE.xml", buildRunConfigurationXML(noPCE = true))
    if (options.generateJUnitTemplate)
      writeToFile(runConfigDir / "_template__of_JUnit.xml", buildJUnitTemplate)
  }

  private def writeToFile(file: File, content: =>String): Unit = {
    try {
      IO.write(file, content)
    }
    catch {
      case e: Throwable =>
        val message = s"can't generate $file: ${e.getMessage}"
        throw new RuntimeException(message, e)
    }
  }

  private def getExplicitIDEARoot:Option[Path] = sys.props.get(IDEA_ROOT_KEY).map(Paths.get(_))

  /**
    * Tries to locate IJ installation root. The one with the "lib", "bin", "plugins", etc. folders.
    * Implementation is wonky since it relies on folder naming a lot, which is prone to changes.
    * TODO: ask toolbox team how to do this properly
    */
  @tailrec
  private def scanForIDEARoot(current: Path): Option[Path] = {
    val isToolboxPluginsFolder  = current.getFileName != null && pluginsPattern.matcher(current.getFileName.toString).matches() && (current / "Scala" / "lib").exists
    val isIJRootFolder          = (current / "lib" / "idea.jar").exists
    if (isIJRootFolder) { // for non-toolbox installations
      Some(current)
    } else if (isToolboxPluginsFolder) {
      val ijBuild     = current.getFileName.toString.replace(".plugins", "")
      val maybeIJRoot = current.getParent / ijBuild
      if (maybeIJRoot.exists)
        Some(maybeIJRoot)
      else {
        log.warn(s"Found toolbox plugins folder, but no IJ root next to it ?! : $current")
        None
      }
    } else if (current.getParent == null) {
      None
    } else {
      scanForIDEARoot(current.getParent)
    }
  }

  /**
    * Attempts to detect jars of '''currently running''' IJ instance to pass ij-junit runtime jars to the
    * generated junit run configuration template.<br>
    * This is required because in order to get test progress and overall
    * communicate with the test framework IJ injects its own classes into your tests classpath and uses a custom junit
    * runner to start the tests, which is distributed with the IJ itself by adding them to the classpath dynamically
    * when starting the tests run configuration.<br>
    * And since we have to statically set the whole classpath in advance while generating the run configuration template
    * xmls, the required jars have to be found using MAGIC(heuristics). To do this we assume that during an sbt import
    * process sbt-launch.jar(which should appear on the java's cmdline) is the one we distribute with the Scala plugin
    * and thereby, resides somewhere close to the IJ core libraries.
    * @return
    */
  private def guessIJRuntimeJarsForJUnitTemplate(): Seq[Path] =
    getExplicitIDEARoot
      .orElse(getCurrentLaunchPath.flatMap(scanForIDEARoot)) match {
      case Some(ijRoot) =>
        log.info(s"Got IDEA installation root at: $ijRoot")
        Seq(
          ijRoot / "lib" / "idea_rt.jar",
          ijRoot / "plugins" / "junit" / "lib" / "junit5-rt.jar",
          ijRoot / "plugins" / "junit" / "lib" / "junit-rt.jar")
       case None =>
         log.error(s"Unable to detect IDEA installation root, JUnit template may fail")
         Seq.empty
      }

  private def getCurrentLaunchPath: Option[Path] = {
    /*
    (java.class.path,/home/miha/.local/share/JetBrains/Toolbox/apps/IDEA-C/ch-1/203.5419.21.plugins/Scala/launcher/sbt-launch.jar)
    /home/miha/.local/share/JetBrains/Toolbox/apps/IDEA-C/ch-1/203.5419.21.plugins/Scala/launcher/sbt-launch.jar early(addPluginSbtFile="""/tmp/idea1.sbt""") ; set ideaPort in Global := 33925 ; idea-shell
    /home/miha/.local/share/JetBrains/Toolbox/apps/IDEA-C/ch-1/203.5419.21/lib/idea_rt.jar
    /home/miha/.local/share/JetBrains/Toolbox/apps/IDEA-C/ch-1/203.5419.21/plugins/junit/lib/junit5-rt.jar
    /home/miha/.local/share/JetBrains/Toolbox/apps/IDEA-C/ch-1/203.5419.21/plugins/junit/lib/junit-rt.jar
    */

    val runCMD = sys.props.getOrElse("sun.java.command", "")
    val matcher = pathPattern.matcher(runCMD)
    if (matcher.find()) {
      val pathString = matcher.group(1)
      val currentPath = Paths.get(pathString)
      Some(currentPath)
    } else {
      log.warn(s"Can't get sbt-launch.jar location from cmdline: $runCMD")
      None
    }
  }

  private lazy val jreSettings: String = {
    val bundledJre = IntellijAwareRunner.getBundledJRE(intellijBaseDir.toPath)
    bundledJre match {
      case None       => ""
      case Some(jbr)  =>
        s"""    <option name="ALTERNATIVE_JRE_PATH" value="${jbr.root}" />
           |    <option name="ALTERNATIVE_JRE_PATH_ENABLED" value="true" />
           |    <shortenClasspath name="ARGS_FILE" />""".stripMargin
    }
  }

  private def mkEnv(env: Map[String, String]): String =  {
      val elems = env
        .map { case (k, v) => s"""<env name="$k" value="$v" />"""}
        .mkString("\n")
    if (elems.nonEmpty)
      s"""<envs>
         |     $elems
         |    </envs>
         |""".stripMargin
    else ""
  }


  private def buildRunConfigurationXML(noPCE: Boolean = false): String = {
    val vmOptions = if (noPCE) intellijVMOptions.copy(noPCE = true) else intellijVMOptions
    val env = mkEnv(options.ideaRunEnv)
    val name = if (noPCE) s"$configName-noPCE" else configName

    val classpathEntries = intellijPlatformJarsClasspath
    val classpathString = classpathEntries.mkString(File.pathSeparator)

    s"""<component name="ProjectRunConfigurationManager">
       |  <configuration default="false" name="$name" type="Application" factoryName="Application">
       |    $jreSettings
       |    <log_file alias="IJ LOG" path="$dataDir/system/log/idea.log" />
       |    <log_file alias="JPS LOG" path="$dataDir/system/log/build-log/build.log" />
       |    <option name="MAIN_CLASS_NAME" value="com.intellij.idea.Main" />
       |    <module name="$moduleName" />
       |    <option name="VM_PARAMETERS" value="${IntellijVMOptions.USE_PATH_CLASS_LOADER} -cp &quot;$classpathString&quot; ${vmOptions.asSeq(quoteValues = true).mkString(" ")}" />
       |    <RunnerSettings RunnerId="Debug">
       |      <option name="DEBUG_PORT" value="" />
       |      <option name="TRANSPORT" value="0" />
       |      <option name="LOCAL" value="true" />
       |    </RunnerSettings>
       |    <option name="PROGRAM_PARAMETERS" value="${options.programParams}" />
       |    <RunnerSettings RunnerId="Profile " />
       |    <RunnerSettings RunnerId="Run" />
       |    <ConfigurationWrapper RunnerId="Debug" />
       |    <ConfigurationWrapper RunnerId="Run" />
       |    $env
       |    <method v="2">
       |      <option name="Make" enabled="true" />
       |      <option name="BuildArtifacts" enabled="true">
       |        <artifact name="$moduleName" />
       |      </option>
       |    </method>
       |  </configuration>
       |</component>""".stripMargin

  }

  private def intellijPlatformJarsClasspath: Seq[String] = {
    //NOTE: see also filtering in org.jetbrains.sbtidea.Init.projectSettings
    intellijPlatformJarsFolder
      .listFiles((_, fileName: String) => {
        fileName.endsWith(".jar") && fileName != JUnit3JarName
      })
      .map(_.getPath)
      .toSeq :+
      (intellijPlatformJarsFolder / "ant" / "lib" / "ant.jar").getPath
  }

  private def pluginClasspathPattern(pluginPath: File): String =
    if (pluginPath.isDirectory)
      s"${pluginPath / "lib"}${File.separator}*"
    else
      pluginPath.toString

  private def buildJUnitTemplate: String = {
    val testVMOptions = intellijVMOptions.copy(test = true)
    val env = mkEnv(options.ideaTestEnv)
    val vmOptionsStr =
      if (classpathStrategy.version >= ClasspathStrategy.Since_203_5251.version) {
        val classPathEntries: mutable.Buffer[String] = new mutable.ArrayBuffer()

        //plugin jars must go first when using CLASSLOADER_KEY
        //example: ./target/plugin/Scala/lib/*
        classPathEntries += (pluginAssemblyDir / "lib").toString + File.separator + "*"

        classPathEntries ++= intellijPlatformJarsClasspath
        classPathEntries ++= pluginRoots.map(pluginClasspathPattern)
        classPathEntries ++= ownProductDirs.map(_.toString)

        //runtime jars from the *currently running* IJ to actually start the tests:
        //<sdkRoot>/lib/idea_rt.jar;
        //<sdkRoot>/plugins/junit/lib/junit5-rt.jar;
        //<sdkRoot>/plugins/junit/lib/junit-rt.jar
        val ijRuntimeJars = guessIJRuntimeJarsForJUnitTemplate()
        classPathEntries ++= ijRuntimeJars.map(_.toString)

        classPathEntries ++= extraJUnitTemplateClasspath.map(_.toString)

        val classpathStr = classPathEntries.mkString(File.pathSeparator)
        s"-cp &quot;$classpathStr&quot; ${testVMOptions.asSeq(quoteValues = true).mkString(" ")}"
      } else {
        testVMOptions.asSeq(quoteValues = true).mkString(" ")
      }
    val searchScope = if (options.testSearchScope.nonEmpty)
      s"""<option name="TEST_SEARCH_SCOPE">
        |      <value defaultName="${options.testSearchScope}" />
        |    </option>""".stripMargin
      else ""
    s"""<component name="ProjectRunConfigurationManager">
       |  <configuration default="true" type="JUnit" factoryName="JUnit">
       |    <useClassPathOnly />
       |    $jreSettings
       |    <option name="MAIN_CLASS_NAME" value="" />
       |    <option name="METHOD_NAME" value="" />
       |    <option name="TEST_OBJECT" value="class" />
       |    <module name="$moduleName" />
       |    <option name="VM_PARAMETERS" value="$vmOptionsStr" />
       |    <option name="WORKING_DIRECTORY" value="${options.workingDir}" />
       |    $searchScope
       |    <RunnerSettings RunnerId="Profile " />
       |    <RunnerSettings RunnerId="Run" />
       |    <ConfigurationWrapper RunnerId="Run" />
       |    $env
       |    <method v="2">
       |      <option name="Make" enabled="true" />
       |      <option name="BuildArtifacts" enabled="true">
       |        <artifact name="$moduleName" />
       |      </option>
       |    </method>
       |  </configuration>
       |</component>""".stripMargin
  }
}

object IdeaConfigBuilder {
  private val pathPattern = Pattern.compile("(^.+sbt-launch\\.jar).*$")
  private val pluginsPattern = Pattern.compile("^.+\\.plugins$")

  val JUnit3JarName = "junit.jar"
}