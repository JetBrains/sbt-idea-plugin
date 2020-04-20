package org.jetbrains.sbtidea.tasks

import java.io.File

import org.jetbrains.sbtidea.Keys.IdeaConfigBuildingOptions
import org.jetbrains.sbtidea.runIdea.{IdeaRunner, IntellijVMOptions}
import org.jetbrains.sbtidea.{pathToPathExt, PluginLogger => log}
import sbt._

class IdeaConfigBuilder(artifactName: String,
                        configName: String,
                        moduleName: String,
                        intellijVMOptions: IntellijVMOptions,
                        dataDir: File,
                        ideaBaseDir: File,
                        dotIdeaFolder: File,
                        options: IdeaConfigBuildingOptions) {

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
       |    <option name="VM_PARAMETERS" value="${vmOptions.asSeq.mkString(" ")}" />
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
       |        <artifact name="$artifactName" />
       |      </option>
       |    </method>
       |  </configuration>
       |</component>""".stripMargin

  }

  private def buildJUnitTemplate: String = {
    val testVMOptions = intellijVMOptions.copy(test = true)
    val env = mkEnv(options.ideaTestEnv)
    val module = if (options.testModuleName.nonEmpty) options.testModuleName else artifactName
    val searchScope = if (options.testSearchScope.nonEmpty)
      s"""<option name="TEST_SEARCH_SCOPE">
        |      <value defaultName="${options.testSearchScope}" />
        |    </option>""".stripMargin
      else ""
    s"""<component name="ProjectRunConfigurationManager">
       |  <configuration default="true" type="JUnit" factoryName="JUnit">
       |    <useClassPathOnly />
       |    $jreSettings
       |    <module name="$module" />
       |    <option name="MAIN_CLASS_NAME" value="" />
       |    <option name="METHOD_NAME" value="" />
       |    <option name="TEST_OBJECT" value="class" />
       |    <option name="VM_PARAMETERS" value="${testVMOptions.asSeq.mkString(" ")}" />
       |    <option name="WORKING_DIRECTORY" value="${options.workingDir}" />
       |    $searchScope
       |    <RunnerSettings RunnerId="Profile " />
       |    <RunnerSettings RunnerId="Run" />
       |    <ConfigurationWrapper RunnerId="Run" />
       |    $env
       |    <method v="2">
       |      <option name="Make" enabled="true" />
       |      <option name="BuildArtifacts" enabled="true">
       |        <artifact name="$artifactName" />
       |      </option>
       |    </method>
       |  </configuration>
       |</component>""".stripMargin
  }
}
