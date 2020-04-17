package org.jetbrains.sbtidea.tasks

import java.io.File

import org.jetbrains.sbtidea.runIdea.{IdeaRunner, IntellijVMOptions}
import org.jetbrains.sbtidea.pathToPathExt
import sbt._

object IdeaConfigBuilder {
  def buildRunConfigurationXML(artifactName: String,
                               configName: String,
                               moduleName: String,
                               intellijVMOptions: IntellijVMOptions,
                               dataDir: File,
                               ideaBaseDir: File): String = {
    val runner = new IdeaRunner((ideaBaseDir / "lib").toPath.list, intellijVMOptions,false)
    val jbrSettings = runner.getBundledJRE match {
      case None => ""
      case Some(jbr) =>
        val shortenClasspath =
          if (jbr.version >= 9)
            "<shortenClasspath name=\"ARGS_FILE\" />"
          else
            "<shortenClasspath name=\"NONE\" />"
        s"""<option name="ALTERNATIVE_JRE_PATH" value="${jbr.root}" />
           |<option name="ALTERNATIVE_JRE_PATH_ENABLED" value="true" />
           |$shortenClasspath""".stripMargin
    }
    s"""<component name="ProjectRunConfigurationManager">
       |  <configuration default="false" name="$configName" type="Application" factoryName="Application">
       |    $jbrSettings
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
}
