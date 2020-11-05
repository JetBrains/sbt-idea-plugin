package org.jetbrains.sbtidea.tasks

import java.io.File
import java.nio.file.{Path, Paths}
import java.util.regex.Pattern

import org.jetbrains.sbtidea.Keys.{CLASSLOADER_KEY, IdeaConfigBuildingOptions}
import org.jetbrains.sbtidea.runIdea.{IdeaRunner, IntellijVMOptions}
import org.jetbrains.sbtidea.tasks.IdeaConfigBuilder.{pathPattern, pluginsPattern}
import org.jetbrains.sbtidea.{pathToPathExt, PluginLogger => log}
import sbt._

import scala.annotation.tailrec


class IdeaConfigBuilder(moduleName: String,
                        configName: String,
                        intellijVMOptions: IntellijVMOptions,
                        dataDir: File,
                        ideaBaseDir: File,
                        dotIdeaFolder: File,
                        pluginAssemblyDir: File,
                        ownProductDirs: Seq[File],
                        intellijJars: Seq[File],
                        pluginIds: Seq[String],
                        options: IdeaConfigBuildingOptions,
                        newClasspathStrategy: Boolean = true) {

  private val runConfigDir = dotIdeaFolder / "runConfigurations"

  def build(): Unit = {
    if (options.generateDefaultRunConfig)
      writeToFile(runConfigDir / s"$configName.xml", buildRunConfigurationXML())
    if (options.generateNoPCEConfiguration)
      writeToFile(runConfigDir / s"$configName-noPCE.xml", buildRunConfigurationXML(noPCE = true))
    if (options.generateJUnitTemplate)
      writeToFile(runConfigDir / "_template__of_JUnit.xml", buildJUnitTemplate)
  }

  private def writeToFile(file: File, content: =>String): Unit = {
    try   { IO.write(file, content) }
    catch { case e: Throwable => log.error(s"can't generate $file: $e")}
  }

  /**
    * Tries to locate IJ installation root. The one with the "lib", "bin", "plugins", etc. folders.
    * Implementation is wonky since it relies on folder naming a lot, which is prone to changes.
    * TODO: ask toolbox team how to do this properly
    */
  @tailrec
  private def findIdeaRoot(current: Path): Option[Path] = {
    val isToolboxPluginsFolder  = pluginsPattern.matcher(current.getFileName.toString).matches() && (current / "Scala" / "lib").exists
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
      findIdeaRoot(current.getParent)
    }
  }

  /**
    * Attempts to detect jars of *currently running* IJ instance to pass ij-junit runtime jars to the
    * generated junit run configuration template. This is required because in order to get test progress and overall
    * communicate with the test framework IJ injects its own classes into your tests classpath and uses a custom junit
    * runner to start the tests, which is distributed with the IJ itself by adding them to the classpath dynamically
    * when starting the tests run configuration.
    * And since we have to statically set the whole classpath in advance while generating the run configuration template
    * xmls, the required jars have to be found using MAGIC(heuristics). To do this we assume that during an sbt import
    * process sbt-launch.jar(which should appear on the java's cmdline) is the one we distribute with the Scala plugin
    * and thereby, resides somewhere close to the IJ core libraries.
    * @return
    */
  private def guessIJRuntimeJars(): Seq[Path] = {
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
      findIdeaRoot(currentPath) match {
        case Some(ijRoot) => Seq(
          ijRoot / "lib" / "idea_rt.jar",
          ijRoot / "plugins" / "junit" / "lib" / "junit5-rt.jar",
          ijRoot / "plugins" / "junit" / "lib" / "junit-rt.jar")
        case None =>
          log.warn(s"Failed to detect IJ root from cmdline: $runCMD")
          Seq.empty
      }
    } else {
      log.warn(s"Can't get sbt-launch.jar location from cmdline, no IJ runtime jars will be detected: $runCMD")
      Seq.empty
    }
  }

  private lazy val jreSettings: String = {
    val runner = new IdeaRunner((ideaBaseDir / "lib").toPath.list, intellijVMOptions,false)
    runner.getBundledJRE match {
      case None       => ""
      case Some(jbr)  =>
        val shortenClasspath =
          if (jbr.version >= 9)
            "    <shortenClasspath name=\"ARGS_FILE\" />"
          else
            "    <shortenClasspath name=\"NONE\" />"
        s"""<option name="ALTERNATIVE_JRE_PATH" value="${jbr.root}" />
           |    <option name="ALTERNATIVE_JRE_PATH_ENABLED" value="true" />
           |$shortenClasspath""".stripMargin
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
    s"""<component name="ProjectRunConfigurationManager">
       |  <configuration default="false" name="$name" type="Application" factoryName="Application">
       |    $jreSettings
       |    <log_file alias="IJ LOG" path="$dataDir/system/log/idea.log" />
       |    <option name="MAIN_CLASS_NAME" value="com.intellij.idea.Main" />
       |    <module name="$moduleName" />
       |    <option name="VM_PARAMETERS" value="-cp ${intellijJars.mkString(File.pathSeparator)} ${vmOptions.asSeq.mkString(" ")}" />
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

  private def buildJUnitTemplate: String = {
    val testVMOptions = intellijVMOptions.copy(test = true)
    val env = mkEnv(options.ideaTestEnv)
    val vmOptionsStr =
      if (newClasspathStrategy) {
        val ijRuntimeJars = guessIJRuntimeJars()
        val classpathStr =
          (pluginAssemblyDir / "lib").toString + File.separator + "*" + // plugin jars must go first when using CLASSLOADER_KEY
            File.pathSeparator + (intellijJars ++ ownProductDirs).mkString(File.pathSeparator) +
            File.pathSeparator + ijRuntimeJars.mkString(File.pathSeparator) // runtime jars from the *currently running* IJ to actually start the tests
        s"-cp $classpathStr ${testVMOptions.asSeq.mkString(" ")} -D$CLASSLOADER_KEY=${pluginIds.mkString(",")}"
      } else {
        testVMOptions.asSeq.mkString(" ")
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
}