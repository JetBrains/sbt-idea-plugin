package org.jetbrains.sbtidea.tasks

import org.jetbrains.sbtidea.IdeaConfigBuildingOptions.AdditionalRunConfigData
import org.jetbrains.sbtidea.Keys.IdeaConfigBuildingOptions
import org.jetbrains.sbtidea.PluginLogger as log
import org.jetbrains.sbtidea.packaging.hasProdTestSeparationEnabled
import org.jetbrains.sbtidea.productInfo.ProductInfoExtraDataProvider
import org.jetbrains.sbtidea.runIdea.IntellijVMOptionsBuilder.VmOptions
import org.jetbrains.sbtidea.runIdea.{CustomIntellijVMOptions, IntellijAwareRunner, IntellijVMOptions, IntellijVMOptionsBuilder}
import sbt.*

import java.io.File
import scala.annotation.nowarn

/**
 * The class is responsible to create all Run Configurations required for the plugin development.
 * That includes:
 *  - main configuration to run development instance of IDE
 *  - extra configurations to run development instance of IDE with additional parameters (VM options, arguments, etc.)
 *  - configuration template for JUnit tests
 *
 * For the similar logic made for SBT commands see [[org.jetbrains.sbtidea.runIdea.IdeaRunner]]
 *
 * @param testPluginRoots contains only those plugins which are listed in the project IntelliJ plugin dependencies and their transitive dependencies
 * @param dataDir         base directory of IntelliJ Platform config and system directories for this plugin
 * @param ownProductDirs  TODO: add description of the parameter
 */
class IdeaConfigBuilder(
  projectName: String,
  dataDir: File,
  intellijBaseDir: File,
  dotIdeaFolder: File,
  ownProductDirs: Seq[File],
  testPluginRoots: Seq[File],
  testClasspath: Seq[String],

  intellijVMOptions: CustomIntellijVMOptions,
  @nowarn("cat=deprecation")
  legacyIntellijVMOptions: IntellijVMOptions,
  intellijVMOptionsBuilder: IntellijVMOptionsBuilder,
  useNewVmOptions: Boolean,

  productInfoExtraDataProvider: ProductInfoExtraDataProvider,
  options: IdeaConfigBuildingOptions,
) {
  private val runConfigDir = dotIdeaFolder / "runConfigurations"

  private val artifactName = projectName

  private val vmOptions: VmOptions = if (useNewVmOptions)
    VmOptions.New(intellijVMOptions)
  else
    VmOptions.Old(legacyIntellijVMOptions)

  def build(): Unit = {
    if (options.generateDefaultRunConfig) {
      val configurationName = artifactName
      val content = buildRunConfigurationXML(
        configurationName = configurationName,
        vmOptions = vmOptions,
        programParams = options.programParams,
        envVars = options.ideaRunEnv,
      )
      writeToFile(runConfigDir / s"$configurationName.xml", content)

      if (useNewVmOptions) {
        // Generate run configuration with the old versions for convenience
        // If there are any issues with the new version, we could quickly try the old configuration during
        // local development without changing value of `org.jetbrains.sbtidea.Keys.useNewVmOptions` and reloading
        // TODO: drop this after some time (in ~1 month?) if there are no issues identified in the default run configuration
        val configurationName = artifactName + " (old VM options)"
        val content = buildRunConfigurationXML(
          configurationName = configurationName,
          vmOptions = VmOptions.Old(legacyIntellijVMOptions),
          programParams = options.programParams,
          envVars = options.ideaRunEnv,
        )
        writeToFile(runConfigDir / s"$configurationName.xml", content)
      }
    }

    // Generate a similar run configuration, but without "Build" "before launch" step
    // Note there is no need to build the project with an explicit "Build" "before launch" step.
    // Build artifact should invoke it transitively.
    // Let's dogfood this run configuration for some time and if we don't find any issues:
    // make it the only and default behavior and delete the ` addBuildProjectBeforeLaunchStep ` parameter
    // TODO: remove the extra Run Configuration and just make this behavior the default one
    if (options.generateDefaultRunConfig) {
      val configurationName = artifactName + " (no explicit build step)"
      val contentNoBuild = buildRunConfigurationXML(
        configurationName = configurationName,
        vmOptions = vmOptions,
        programParams = options.programParams,
        envVars = options.ideaRunEnv,
        addBuildProjectBeforeLaunchStep = false
      )
      writeToFile(runConfigDir / s"$configurationName.xml", contentNoBuild)
    }

    options.additionalRunConfigs.foreach { data: AdditionalRunConfigData =>
      val configurationName = artifactName + data.configurationNameSuffix
      val content = buildRunConfigurationXML(
        configurationName = configurationName,
        vmOptions = vmOptions.withExtraOptions(data.extraVmOptions),
        programParams = options.programParams,
        envVars = options.ideaRunEnv
      )
      writeToFile(runConfigDir / s"$configurationName.xml", content)
    }

    if (options.generateRunConfigForSplitMode) {
      val configurationName = artifactName + " (split mode)"
      buildSplitModeRunConfigurationXml(configurationName) match {
        case Right(content) =>
          writeToFile(runConfigDir / s"$configurationName.xml", content)
        case Left(error) =>
          log.warn(s"Can't generate run configuration for split mode: $error")
      }
    }

    if (options.generateJUnitTemplate)
      writeToFile(runConfigDir / "_template__of_JUnit.xml", buildJUnitTemplate)
  }

  // NOTE: most client-specific parameters are hardcoded
  // (like client debug port, client system/config paths, client properties location)
  // We might consider parametrizing them once this configuration generation is +- stable
  private def buildSplitModeRunConfigurationXml(configurationName: String): Either[String, String] = {
    val programParams = s"${options.programParams} splitMode"

    val bundledJre = IntellijAwareRunner.getBundledJRE(intellijBaseDir.toPath).getOrElse {
      return Left(s"Can't detect bundled JRE path in $intellijBaseDir required for IDE client")
    }

    // NOTE: writing client properties to the run configuration directory just for simplicity,
    // to keep related configurations close to each other
    val ideClientPropertiesFile = runConfigDir / "idea_client.properties"
    val envVars = options.ideaRunEnv ++ Map(
      "JETBRAINS_CLIENT_JDK" -> s"${bundledJre.root}",
      "JETBRAINS_CLIENT_PROPERTIES" -> s"$ideClientPropertiesFile"
    )

    //TODO: also configure location for logs & plugins
    val clientSystemPath = s"$dataDir/system_client"
    val clientConfigPath = s"""$dataDir/config_client"""
    val clientDebugPort = "7777"
    writeToFile(ideClientPropertiesFile,
      s"""-Didea.system.path=$clientSystemPath
         |-Didea.config.path=$clientConfigPath
         |-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:$clientDebugPort
         |""".stripMargin
    )

    Right(buildRunConfigurationXML(
      configurationName = configurationName,
      vmOptions = vmOptions,
      programParams = programParams,
      envVars = envVars
    ))
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

  private def createEnvironmentVariablesSection(env: Map[String, String]): String = {
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

  private def buildRunConfigurationXML(
    configurationName: String,
    vmOptions: VmOptions,
    programParams: String,
    envVars: Map[String, String],
    addBuildProjectBeforeLaunchStep: Boolean = true,
  ): String = {
    val envVarsSection = createEnvironmentVariablesSection(envVars)
    val vmOptionsStr = buildRunVmOptionsString(vmOptions)
    val moduleName = generateModuleName(sourceSetModuleSuffix =  "main")

    //No need to build the project, build artifact will invoke it transitively
    val buildProjectStepText = if (addBuildProjectBeforeLaunchStep)
      """<option name="Make" enabled="true" />"""
    else
      ""

    s"""<component name="ProjectRunConfigurationManager">
       |  <configuration default="false" name="$configurationName" type="Application" factoryName="Application">
       |    $jreSettings
       |    <log_file alias="IJ LOG" path="$dataDir/system/log/idea.log" />
       |    <log_file alias="JPS LOG" path="$dataDir/system/log/build-log/build.log" />
       |    <option name="MAIN_CLASS_NAME" value="com.intellij.idea.Main" />
       |    <module name="$moduleName" />
       |    <option name="VM_PARAMETERS" value="$vmOptionsStr" />
       |    <RunnerSettings RunnerId="Debug">
       |      <option name="DEBUG_PORT" value="" />
       |      <option name="TRANSPORT" value="0" />
       |      <option name="LOCAL" value="true" />
       |    </RunnerSettings>
       |    <option name="PROGRAM_PARAMETERS" value="$programParams" />
       |    <RunnerSettings RunnerId="Profile " />
       |    <RunnerSettings RunnerId="Run" />
       |    <ConfigurationWrapper RunnerId="Debug" />
       |    <ConfigurationWrapper RunnerId="Run" />
       |    $envVarsSection
       |    <method v="2">
       |      $buildProjectStepText
       |      <option name="BuildArtifacts" enabled="true">
       |        <artifact name="$artifactName" />
       |      </option>
       |    </method>
       |  </configuration>
       |</component>""".stripMargin
  }

  private def buildRunVmOptionsString(vmOptions: VmOptions): String = {
    val bootClasspathJarPaths = productInfoExtraDataProvider.bootClasspathJars.map(_.toString)
    val bootClasspathString = bootClasspathJarPaths.mkString(File.pathSeparator)
    val vmOptionsSeq = intellijVMOptionsBuilder.build(
      vmOptions = vmOptions,
      quoteValues = true,
      forTests = false,
    )
    s"""${IntellijVMOptions.USE_PATH_CLASS_LOADER} -cp &quot;$bootClasspathString&quot; ${vmOptionsSeq.mkString(" ")}"""
  }

  private def generateModuleName(sourceSetModuleSuffix: String): String =
    if (hasProdTestSeparationEnabled) s"$projectName.$sourceSetModuleSuffix"
    else projectName

  private def buildJUnitTemplate: String = {
    val env = createEnvironmentVariablesSection(options.ideaTestEnv)
    val vmOptionsStr = buildTestVmOptionsString

    val argFilePath = runConfigDir / "junit_template_argfile.txt"
    writeToFile(argFilePath, vmOptionsStr)

    val vmOptionsStrArgfile = s"@${argFilePath.getAbsolutePath}"

    val moduleName = generateModuleName(sourceSetModuleSuffix = "test")

    val searchScope = if (options.testSearchScope.nonEmpty)
      s"""<option name="TEST_SEARCH_SCOPE">
         |      <value defaultName="${options.testSearchScope}" />
         |    </option>""".stripMargin
    else ""

    // The VM_PARAMETERS option must contain a fake -cp option (with an empty string as a classpath),
    // otherwise the platform will enforce the module classpath on our tests
    // and completely disregard our classpath vm options.
    // We then "override" this fake -cp argument with the -cp argument
    // written to the argfile and we pass the whole argfile to java using the @argfile syntax.
    s"""<component name="ProjectRunConfigurationManager">
       |  <configuration default="true" type="JUnit" factoryName="JUnit">
       |    <useClassPathOnly />
       |    $jreSettings
       |    <option name="MAIN_CLASS_NAME" value="" />
       |    <option name="METHOD_NAME" value="" />
       |    <option name="TEST_OBJECT" value="class" />
       |    <module name="$moduleName" />
       |    <option name="VM_PARAMETERS" value="-cp &quot;&quot; $vmOptionsStrArgfile" />
       |    <option name="WORKING_DIRECTORY" value="${options.workingDir}" />
       |    $searchScope
       |    <RunnerSettings RunnerId="Profile " />
       |    <RunnerSettings RunnerId="Run" />
       |    <ConfigurationWrapper RunnerId="Run" />
       |    $env
       |    <method v="2">
       |      <!-- We need to explicitly build the project, build artifact doesn't compile the tests -->
       |      <option name="Make" enabled="true" />
       |      <option name="BuildArtifacts" enabled="true">
       |        <artifact name="$artifactName" />
       |      </option>
       |    </method>
       |  </configuration>
       |</component>""".stripMargin
  }

  private def escapeBackslash(s: String): String = s.replace("\\", "\\\\")

  private def buildTestVmOptionsString: String = {
    val classpathStr = escapeBackslash(testClasspath.mkString(File.pathSeparator))
    val quotedClasspathStr = "\"" + classpathStr + "\""
    val vmOptionsEscaped = intellijVMOptionsBuilder.buildQuotedNoEscapeXml(
      vmOptions = vmOptions,
      forTests = true,
    ).map(escapeBackslash)
    (Seq("-cp", quotedClasspathStr) ++ vmOptionsEscaped).mkString(System.lineSeparator())
  }
}