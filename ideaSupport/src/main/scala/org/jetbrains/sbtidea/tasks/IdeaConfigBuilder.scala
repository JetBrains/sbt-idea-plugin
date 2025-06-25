package org.jetbrains.sbtidea.tasks

import coursier.{Dependency, Fetch, Module, ModuleName, Organization}
import org.jetbrains.sbtidea.IdeaConfigBuildingOptions.AdditionalRunConfigData
import org.jetbrains.sbtidea.Keys.IdeaConfigBuildingOptions
import org.jetbrains.sbtidea.packaging.hasProdTestSeparationEnabled
import org.jetbrains.sbtidea.productInfo.ProductInfoExtraDataProvider
import org.jetbrains.sbtidea.runIdea.{IntellijAwareRunner, IntellijVMOptions}
import org.jetbrains.sbtidea.tasks.IdeaConfigBuilder.{computeJupiterRuntimeDependencies, pathPattern, pluginsPattern}
import org.jetbrains.sbtidea.tasks.classpath.PluginClasspathUtils
import org.jetbrains.sbtidea.{PathExt, PluginLogger as log}
import sbt.*

import java.io.File
import java.nio.file.{Path, Paths}
import java.util.regex.Pattern
import scala.annotation.tailrec

/**
 * The class is responsible to create all run configurations for plugin development.
 * That includes:
 *  - main configuration to run development instance of IDE
 *  - extra configurations to run development instance of IDE with additional parameters (VM options, arguments, etc.)
 *  - configuration template for JUnit tests
 *
 * @param testPluginRoots contains only those plugins which are listed in the project IntelliJ plugin dependencies and their transitive dependencies
 * @param dataDir         base directory of IntelliJ Platform config and system directories for this plugin
 */
class IdeaConfigBuilder(
  projectName: String,
  intellijVMOptions: IntellijVMOptions,
  dataDir: File,
  intellijBaseDir: File,
  productInfoExtraDataProvider: ProductInfoExtraDataProvider,
  dotIdeaFolder: File,
  pluginAssemblyDir: File,
  ownProductDirs: Seq[File],
  testPluginRoots: Seq[File],
  extraJUnitTemplateClasspath: Seq[File],
  options: IdeaConfigBuildingOptions
) {
  private val runConfigDir = dotIdeaFolder / "runConfigurations"

  private val IDEA_ROOT_KEY = "idea.installation.dir"

  private val artifactName = projectName

  def build(): Unit = {
    if (options.generateDefaultRunConfig) {
      val configurationName = artifactName
      val content = buildRunConfigurationXML(
        configurationName,
        intellijVMOptions,
        options.programParams,
        options.ideaRunEnv,
      )
      writeToFile(runConfigDir / s"$configurationName.xml", content)
    }

    // Generate a similar run configuration, but without "Build" "before launch" step
    // Note there is no need to build the project with an explicit "Build" "before launch" step.
    // Build artifact should invoke it transitively.
    // Let's dogfood this run configuration for some time and if we don't find any issues:
    // make it the only and default behavior and delete the ` addBuildProjectBeforeLaunchStep ` parameter
    if (options.generateDefaultRunConfig) {
      val configurationName = artifactName + " (no explicit build step)"
      val contentNoBuild = buildRunConfigurationXML(
        configurationName,
        intellijVMOptions,
        options.programParams,
        options.ideaRunEnv,
        addBuildProjectBeforeLaunchStep = false
      )
      writeToFile(runConfigDir / s"$configurationName.xml", contentNoBuild)
    }

    options.additionalRunConfigs.foreach { data: AdditionalRunConfigData =>
      val configurationName = artifactName + data.configurationNameSuffix
      val content = buildRunConfigurationXML(
        configurationName,
        intellijVMOptions.withOptions(data.extraVmOptions),
        options.programParams,
        options.ideaRunEnv
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

    // TODO: We should take all these VM options from "additional VM properties" from product-info.json (SCL-23540)
    //  We should replace APP_PACKAGE with proper path
    val modulesDescriptorsJar = intellijBaseDir / "modules" / "module-descriptors.jar"
    val vmOptions = intellijVMOptions.withOptions(Seq(
      s"-Dintellij.platform.runtime.repository.path=&quot;${modulesDescriptorsJar.getCanonicalPath}&quot;",
      "--add-opens=java.desktop/com.sun.java.swing=ALL-UNNAMED",
      "--add-opens=java.management/sun.management=ALL-UNNAMED"
    ))

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

    Right(buildRunConfigurationXML(configurationName, vmOptions, programParams, envVars))
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

  private def getExplicitIDEARoot: Option[Path] = sys.props.get(IDEA_ROOT_KEY).map(Paths.get(_))

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

  private def resolveJUnitJupiterRuntime(testClasspath: Seq[Path]): Seq[Path] = {
    val toResolve = computeJupiterRuntimeDependencies(testClasspath)
    Fetch()
      .withDependencies(toResolve)
      .run()
      .map(_.toPath)
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
    vmOptions: IntellijVMOptions,
    programParams: String,
    envVars: Map[String, String],
    addBuildProjectBeforeLaunchStep: Boolean = true
  ): String = {
    val envVarsSection = createEnvironmentVariablesSection(envVars)
    val vmOptionsStr = buildRunVmOptionsString(vmOptions)
    val moduleName = generateModuleName(sourceSetModuleSuffix =  "main")

    val buildProjectStepText = if (addBuildProjectBeforeLaunchStep) {
      //No need to build the project, build artifact will invoke it transitively
      """<option name="Make" enabled="true" />"""
    }
    else {
      ""
    }

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

  private def buildRunVmOptionsString(vmOptions: IntellijVMOptions): String = {
    val bootClasspathJarPaths = productInfoExtraDataProvider.bootClasspathJars.map(_.toString)
    val bootClasspathString = bootClasspathJarPaths.mkString(File.pathSeparator)
    s"""${IntellijVMOptions.USE_PATH_CLASS_LOADER} -cp &quot;$bootClasspathString&quot; ${vmOptions.asSeq(quoteValues = true).mkString(" ")}"""
  }

  private def buildTestClasspath: Seq[String] = {
    val classPathEntries = Seq.newBuilder[String]

    //plugin jars must go first when using CLASSLOADER_KEY
    //example: ./target/plugin/Scala/lib/*
    classPathEntries += (pluginAssemblyDir / "lib").toString + File.separator + "*"
    classPathEntries += (pluginAssemblyDir / "lib" / "modules").toString + File.separator + "*"

    classPathEntries ++= productInfoExtraDataProvider.bootClasspathJars.map(_.toString)
    classPathEntries ++= productInfoExtraDataProvider.productModulesJars.map(_.toString)
    classPathEntries ++= productInfoExtraDataProvider.testFrameworkJars.map(_.toString)
    classPathEntries ++= testPluginRoots.flatMap(PluginClasspathUtils.getPluginClasspathPattern)
    classPathEntries ++= ownProductDirs.map(_.toString)

    //runtime jars from the *currently running* IJ to actually start the tests:
    //<sdkRoot>/lib/idea_rt.jar;
    //<sdkRoot>/plugins/junit/lib/junit5-rt.jar;
    //<sdkRoot>/plugins/junit/lib/junit-rt.jar
    val ijRuntimeJars = guessIJRuntimeJarsForJUnitTemplate()
    classPathEntries ++= ijRuntimeJars.map(_.toString)
    val junitJupiterRuntimeJars = resolveJUnitJupiterRuntime(extraJUnitTemplateClasspath.map(_.toPath))
    classPathEntries ++= junitJupiterRuntimeJars.map(_.toString)

    classPathEntries ++= extraJUnitTemplateClasspath.map(_.toString)
    classPathEntries.result()
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
    val testVMOptions = intellijVMOptions.copy(test = true)
    val classPathEntries = buildTestClasspath
    val classpathStr = escapeBackslash(classPathEntries.mkString(File.pathSeparator))
    val quotedClasspathStr = "\"" + classpathStr + "\""
    (Seq("-cp", quotedClasspathStr) ++ testVMOptions.asSeqQuotedNoEscapeXml.map(escapeBackslash)).mkString(System.lineSeparator())
  }
}

object IdeaConfigBuilder {
  private val pathPattern = Pattern.compile("(^.+sbt-launch\\.jar).*$")
  private val pluginsPattern = Pattern.compile("^.+\\.plugins$")

  private[tasks] val fallbackJupiterVersion = "5.10.3"
  private[tasks] val fallbackPlatformVersion = "1.10.3"

  private[tasks] def computeJupiterRuntimeDependencies(testClasspath: Seq[Path]): Seq[Dependency] = {
    def findJar(org: String, artifact: String): Option[(Path, String)] =
      testClasspath.find { path =>
        // org.junit.jupiter -> org/junit/jupiter or org\junit\jupiter
        val orgPath = org.replace('.', File.separatorChar)
        // Checks that the jar absolute path contains both the expected organization name and artifact name.
        // This is done to filter out hypothetical jars that have the same name but a different organization.
        path.toString.contains(orgPath) &&
          path.getFileName.toString.matches(s"$artifact-(.*)\\.jar")
      }.map(path => (path, artifact))

    // Detect the version of the jupiter artifacts already on the classpath. If none, fall back to a hardcoded version.
    val jupiterVersion = findJar("org.junit.jupiter", "junit-jupiter-api")
      .orElse(findJar("org.junit.jupiter", "junit-jupiter-engine"))
      .orElse(findJar("org.junit.vintage", "junit-vintage-engine"))
      .flatMap { case (jar, artifact) =>
        val versionRegex = s"$artifact-(.*)\\.jar".r

        jar.getFileName.toString match {
          case versionRegex(version) => Some(version)
          case _ => None
        }
      }.getOrElse(fallbackJupiterVersion)

    val jupiterVersionRegex = "5\\.(.*)".r

    // Use the corresponding jupiter platform launcher version for the given jupiter version, e.g. 5.9.3 -> 1.9.3.
    val platformVersion = jupiterVersion match {
      case jupiterVersionRegex(rest) => s"1.$rest"
      case _ => fallbackPlatformVersion
    }

    val dependencies = Seq.newBuilder[Dependency]

    // Resolve the junit-jupiter-engine jar if not already on the test classpath.
    findJar("org.junit.jupiter", "junit-jupiter-engine") match {
      case Some(_) =>
      case None =>
        dependencies += Dependency(
          Module(Organization("org.junit.jupiter"), ModuleName("junit-jupiter-engine")),
          jupiterVersion
        )
    }

    // Resolve the junit-vintage-engine jar if not already on the classpath.
    findJar("org.junit.vintage", "junit-vintage-engine") match {
      case Some(_) =>
      case None =>
        dependencies += Dependency(
          Module(Organization("org.junit.vintage"), ModuleName("junit-vintage-engine")),
          jupiterVersion
        )
    }

    // Resolve the junit-platform-launcher jar if not already on the classpath.
    findJar("org.junit.platform", "junit-platform-launcher") match {
      case Some(_) =>
      case None =>
        dependencies += Dependency(
          Module(Organization("org.junit.platform"), ModuleName("junit-platform-launcher")),
          platformVersion
        )
    }

    dependencies.result()
  }
}
