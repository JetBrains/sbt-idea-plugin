package org.jetbrains.sbtidea.tasks

object IdeaConfigBuilder {
  def buildRunConfigurationXML(name: String, javaOptions: Seq[String]): String = {
          s"""<component name="ProjectRunConfigurationManager">
         |  <configuration default="false" name="$name" type="Application" factoryName="Application">
         |    <option name="MAIN_CLASS_NAME" value="com.intellij.idea.Main" />
         |    <module name="$name" />
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
         |    </method>
         |  </configuration>
         |</component>""".stripMargin

  }
}
