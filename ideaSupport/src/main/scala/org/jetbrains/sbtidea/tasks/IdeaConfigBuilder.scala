package org.jetbrains.sbtidea.tasks

import java.io.File

import org.jetbrains.sbtidea.runIdea.{IdeaRunner, IntellijVMOptions}
import org.jetbrains.sbtidea.pathToPathExt
import sbt._

class IdeaConfigBuilder(artifactName: String,
                        configName: String,
                        moduleName: String,
                        intellijVMOptions: IntellijVMOptions,
                        dataDir: File,
                        ideaBaseDir: File) {

  private val jreSettings: String = {
    val runner = new IdeaRunner((ideaBaseDir / "lib").toPath.list, intellijVMOptions,false)
    runner.getBundledJRE match {
      case None => ""
      case Some(jbr) =>
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

  def buildRunConfigurationXML: String = {
    s"""<component name="ProjectRunConfigurationManager">
       |  <configuration default="false" name="$configName" type="Application" factoryName="Application">
       |    $jreSettings
       |    <log_file alias="IJ LOG" path="$dataDir/system/log/idea.log" />
       |    <option name="MAIN_CLASS_NAME" value="com.intellij.idea.Main" />
       |    <module name="$moduleName" />
       |    <option name="VM_PARAMETERS" value="${intellijVMOptions.asSeq.mkString(" ")}" />
       |    <RunnerSettings RunnerId="Debug">
       |      <option name="DEBUG_PORT" value="" />
       |      <option name="TRANSPORT" value="0" />
       |      <option name="LOCAL" value="true" />
       |    </RunnerSettings>
       |    <RunnerSettings RunnerId="Profile " />
       |    <RunnerSettings RunnerId="Run" />
       |    <ConfigurationWrapper RunnerId="Debug" />
       |    <ConfigurationWrapper RunnerId="Run" />
       |    <method v="2">
       |      <option name="Make" enabled="true" />
       |      <option name="BuildArtifacts" enabled="true">
       |        <artifact name="$artifactName" />
       |      </option>
       |    </method>
       |  </configuration>
       |</component>""".stripMargin

  }

  def buildJUnitTemplate: String = {
    val testVMOptions = intellijVMOptions.copy(test = true)
    s"""<component name="ProjectRunConfigurationManager">
       |  <configuration default="true" type="JUnit" factoryName="JUnit">
       |    <useClassPathOnly />
       |    $jreSettings
       |    <option name="MAIN_CLASS_NAME" value="" />
       |    <option name="METHOD_NAME" value="" />
       |    <option name="TEST_OBJECT" value="class" />
       |    <option name="VM_PARAMETERS" value="${testVMOptions.asSeq.mkString(" ")}" />
       |    <option name="PARAMETERS" value="" />
       |    <option name="TEST_SEARCH_SCOPE">
       |      <value defaultName="moduleWithDependencies" />
       |    </option>
       |    <RunnerSettings RunnerId="Profile " />
       |    <RunnerSettings RunnerId="Run" />
       |    <ConfigurationWrapper RunnerId="Run" />
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
