package org.jetbrains.sbtidea.tasks

import java.io.File

object IdeaConfigBuilder {
  def buildRunConfigurationXML(artifactName: String, configName: String, moduleName: String, javaOptions: Seq[String], dataDir: File): String = {
          s"""<component name="ProjectRunConfigurationManager">
         |  <configuration default="false" name="$configName" type="Application" factoryName="Application">
         |    <log_file alias="IJ LOG" path="$dataDir/system/log/idea.log" />
         |    <option name="MAIN_CLASS_NAME" value="com.intellij.idea.Main" />
         |    <module name="$moduleName" />
         |    <option name="VM_PARAMETERS" value="${javaOptions.mkString(" ")}" />
         |    <shortenClasspath name="CLASSPATH_FILE" />
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
