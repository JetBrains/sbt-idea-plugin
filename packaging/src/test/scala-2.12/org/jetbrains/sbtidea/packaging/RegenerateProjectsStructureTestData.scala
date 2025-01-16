package org.jetbrains.sbtidea.packaging

import io.circe.Json
import io.circe.syntax.EncoderOps
import org.jetbrains.sbtidea.PluginLogger
import org.jetbrains.sbtidea.packaging.LinearMappingsBuilderTest.{TestDataDescription, runWithSeparateProdTestSourcesEnabled}
import org.jetbrains.sbtidea.packaging.mappings.LinearMappingsBuilder
import org.jetbrains.sbtidea.packaging.structure.sbtImpl.SbtPackagedProjectNodeImpl
import org.jetbrains.sbtidea.packaging.testUtils.CirceEncodersDecoders.*
import org.jetbrains.sbtidea.packaging.testUtils.{JsonUtils, RevisionReference, TestDataDir}
import sbt.fileToRichFile

import java.io.{File, FileInputStream, ObjectInputStream}
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, StandardCopyOption, StandardOpenOption}
import scala.jdk.CollectionConverters.asScalaBufferConverter
import scala.util.chaining.scalaUtilChainingOps

object RegenerateProjectsStructureTestData {

  import RegenerateProjectsStructureTestData.IoUtils.*

  private val CurrentWorkingDir = new File(".").getCanonicalFile
  private val CurrentJavaHome = System.getProperty("java.home")
  private val CurrentPluginVersion = publishCurrentSbtIdeaPluginToLocalRepoAndGetVersions
  private val CurrentSbtVersion = getSbtVersionFromClasspath

  locally {
    val javaVersion = Runtime.version().feature()
    if (javaVersion >= 19) {
      throw new RuntimeException(s"Current Java version ($javaVersion) is not supported by older versions of sbt (see MinimumSbtVersion in build.sbt). Use earlier versions of Java.")
    }
  }

  /**
   * @return version of the locally-published plugin
   */
  //noinspection ScalaUnusedSymbol
  private def publishCurrentSbtIdeaPluginToLocalRepoAndGetVersions: String = {
    println("Publishing sbt-idea-plugin to local repository...")

    val process = new ProcessBuilder("sbt", "compile ; publishLocal ; show core / version")
      .directory(CurrentWorkingDir)
      .redirectErrorStream(true)
      .start()

    val outputLines = scala.io.Source.fromInputStream(process.getInputStream).getLines().map { line =>
      println(line)
      line
    }.toList

    process.waitFor()

    val exitCode = process.exitValue()
    if (exitCode != 0)
      throw new RuntimeException(s"Failed to execute sbt command to detect current sbt-idea-plugin version (exit code: $exitCode)")

    val printedVersion = outputLines
      .collect { case line if line.startsWith("[info]") => line.stripPrefix("[info]").trim }
      .lastOption
      .getOrElse {
        "Failed to retrieve both plugin and sbt versions from the sbt process output."
      }
    printedVersion
  }

  /**
   * Determines the version of SBT on the current classpath by reading it from the jar manifest.
   * Uses the jar containing `sbt.Keys` to retrieve the version.
   *
   * @return The SBT version or throws exception
   */
  def getSbtVersionFromClasspath: String = {
    val sbtLibJarFile = new File(sbt.Keys.getClass.getProtectionDomain.getCodeSource.getLocation.getPath)
    assert(sbtLibJarFile.exists, "Could not locate containing jar file for sbt.Keys")
    val jar = new java.util.jar.JarFile(sbtLibJarFile)
    val manifest = jar.getManifest
    val version = Option(manifest.getMainAttributes.getValue("Implementation-Version"))
    jar.close()
    version.get
  }

  def main(args: Array[String]): Unit = {
    println(s"#########")
    println(s"Regenerating projects structure test data")
    println(s"JVM home       : $CurrentJavaHome")
    println(s"Working dir    : ${CurrentWorkingDir.getAbsolutePath}")
    println(s"Plugin version : $CurrentPluginVersion")
    println(s"SBT version    : $CurrentSbtVersion")
    println(s"#########")

    //TODO: we need to iterate not just over the ReposRevisions.All
    // but over LinearMappingsBuilderTest test data wit hte parameters used inside
    LinearMappingsBuilderTest.TestDataDescriptions.foreach(regenerateTestData)
  }

  private def regenerateTestData(td: TestDataDescription): Unit = {
    if (td.useSeparateProductionTestSources)
      runWithSeparateProdTestSourcesEnabled {
        regenerateTestDataInner(td)
      }
    else {
      regenerateTestDataInner(td)
    }
  }

  private def regenerateTestDataInner(td: TestDataDescription): Unit = {
    val revisionRef: RevisionReference = td.revisionRef

    val revisionUrl = s"${revisionRef.repositoryUrl}/commit/${revisionRef.revision}"
    println(s"### Generating structure for: ${td.testDataFileName} $revisionUrl")

    // Regenerate structure


    // in: https://github.com/spotify/scio-idea-plugin
    // out: scio-idea-plugin
    val repoName = revisionRef.repositoryUrl.split('/').last
    val repoDir = CurrentWorkingDir / "tempProjects" / (repoName + "-" + revisionRef.revision.take(6))
    println("Dump structure START")
    val result = dumpStructure(
      td,
      baseTargetStructuresDir = TestDataDir,
      repoDir = repoDir,
      revisionDetails = revisionRef,
    )
    println("Dump structure END")

    val structureBinaryFile = new File(result.structureFilePath)
    // ATTENTION: to correctly deserialize the object we need to ensure that
    // same environment is used in current process and in the SBT process that generated the structure.
    // This environment includes:
    // 1. Same JVM version (some of the serialised classes come from the JVM)
    // 2. Same SBT version (to ensure that same classes have same serialization format)
    //    For example sbt.librarymanagement.CrossVersion is different in sbt 1.4 and 1.10
    val structureOriginal = readBinaryData[Seq[SbtPackagedProjectNodeImpl]](structureBinaryFile)
    // It was a temp file to get the object in memory, delete it
    structureBinaryFile.delete()

    val testDataPaths = MyTestDataPaths(
      buildDir = repoDir,
      userHome = new File(System.getProperty("user.home")),
      outputDir = new File(result.packageOutputDirPath)
    )

    val structureJsonStringOriginal = structureOriginal.asJson
    val structureJson = replaceAbsolutePathsWithMacroKeys(structureJsonStringOriginal, testDataPaths).noSpaces
    val structureJsonFileName = s"${td.testDataFileName}-structure.json"
    val structureJsonOutputFile = TestDataDir / structureJsonFileName
    writeStringToFile(structureJsonOutputFile, structureJson)

    // Regenerate mappings
    val structureWithMacroPaths = JsonUtils.decodeJson[Seq[SbtPackagedProjectNodeImpl]](structureJson, "Structure")
    val mappingsBuilder = new LinearMappingsBuilder(new File(PathMacroKeys.PluginOutput), PluginLogger)
    val mappings = mappingsBuilder.buildMappings(structureWithMacroPaths)

    val mappingsJson = mappings.asJson.noSpaces
    val mappingsJsonFileName = s"${td.testDataFileName}-mappings.json"
    val mappingsJsonOutputFile = TestDataDir / mappingsJsonFileName
    writeStringToFile(mappingsJsonOutputFile, mappingsJson)
  }

  private def replaceAbsolutePathsWithMacroKeys(json: Json, paths: MyTestDataPaths): Json = {
    def inner(json: Json): Json = json match {
      case jsObj if jsObj.isObject =>
        jsObj.mapObject(_.mapValues(inner))
      case jsArr if jsArr.isArray =>
        jsArr.mapArray(_.map(inner))
      case jsStr if jsStr.isString =>
        val value = jsStr.asString.get
        Json.fromString(replaceAbsolutePathsWithMacroKeys(value, paths))
      case other =>
        other
    }

    val newJson = inner(json)
    newJson
  }

  private def replaceAbsolutePathsWithMacroKeys(
    path: String,
    paths: MyTestDataPaths,
  ): String = {
    val projectPath = paths.buildDir.getPath
    val userHomePath = paths.userHome.getPath
    val pluginOutputPath = paths.pluginOutputDir.getPath

    // NOTE: we need to process it in this order because each next substitution is shorter than previous,
    // so moving it first could replace less text than needed
    if (path.contains(pluginOutputPath)) {
      path.replace(projectPath, PathMacroKeys.PluginOutput)
    }
    else if (path.contains(projectPath))
      path.replace(projectPath, PathMacroKeys.ProjectDir)
    else if (path.contains(userHomePath))
      path.replace(userHomePath, PathMacroKeys.UserHome)
    else
      path
  }

  object PathMacroKeys {
    val PluginOutput = "$PLUGIN_OUTPUT_DIR"
    val ProjectDir = "$PROJECT_DIR"
    val UserHome = "$USER_HOME"
  }

  private case class StructureDumpResult(
    structureFilePath: String,
    packageOutputDirPath: String,
  )

  /**
   * @return the generated structure file path
   * @note don't confuse with logic related to sbt-structure plugin
   *       it's completely independent thing, just some similar method name
   */
  private def dumpStructure(
    td: TestDataDescription,
    baseTargetStructuresDir: File,
    repoDir: File,
    revisionDetails: RevisionReference,
  ): StructureDumpResult = {
    // Clone and checkout the repository
    if (!repoDir.exists()) {
      val cloneProcess = new ProcessBuilder("git", "clone", revisionDetails.repositoryUrl, repoDir.getAbsolutePath)
        .inheritIO()
        .start()
      cloneProcess.waitFor()
    }

    // Stash or revert all changes (tracked and untracked) in the repository
    val resetProcess = new ProcessBuilder("git", "reset", "--hard")
      .directory(repoDir)
      .inheritIO()
      .start()
    resetProcess.waitFor()

    val cleanProcess = new ProcessBuilder("git", "clean", "-fd")
      .directory(repoDir)
      .inheritIO()
      .start()
    cleanProcess.waitFor()

    // Checkout the specified revision
    val checkoutProcess = new ProcessBuilder("git", "checkout", revisionDetails.revision)
      .directory(repoDir)
      .inheritIO()
      .start()
    checkoutProcess.waitFor()

    updateSbtIdeaPluginToVersion(repoDir, CurrentPluginVersion)

    // Copy `structureDumper.txt` to the repository plugins directory and rename to `structureDumper.sbt`
    val structureDumperFileName = "_structureDumper.sbt"
    Files.copy(
      new File(TestDataDir, structureDumperFileName).toPath,
      new File(repoDir, structureDumperFileName).toPath,
      StandardCopyOption.REPLACE_EXISTING
    )

    // Ensure that sbt version in the test repository equals to the sbt version in `build.sbt` in pluginCrossBuild / sbtVersion
    updateSbtVersion(repoDir, CurrentSbtVersion)

    // Run sbt process to call `dumpStructure` task
    baseTargetStructuresDir.mkdirs()

    val sbtProcess = new ProcessBuilder("sbt", s"dumpStructureToFile $baseTargetStructuresDir")
      .directory(repoDir)
      .redirectErrorStream(true)
      .tap { pb =>
        val env = pb.environment()
        // Ensure the sbt process uses the same JVM that is used in current app to ensure correct serialization/deserialization
        env.put("JAVA_HOME", CurrentJavaHome)

        if (td.useSeparateProductionTestSources) {
          env.put("JAVA_OPTS", s"-D${org.jetbrains.sbtidea.packaging.ProdTestSourcesKey}=true")
        }
      }
      .start()

    val outputLinesBuffer = scala.collection.mutable.ListBuffer[String]()
    val outputLines = scala.io.Source.fromInputStream(sbtProcess.getInputStream).getLines().map { line =>
      // We want collect the output to get the output structure file and print the output to the console
      println(line)
      outputLinesBuffer += line
      line
    }.toList
    sbtProcess.waitFor()

    // See sources of _structureDumper.sbt
    val StructureWrittenPrefix = "Structure written to:"
    val PackageOutputDirPrefix = "Package output directory:"

    def extractPathFromOutput(outputLines: List[String], prefix: String, errorMessagePart: String): String =
      outputLines.find(_.contains(prefix))
        .map(_.stripPrefix(prefix).trim)
        .getOrElse(throw new RuntimeException(s"Failed to detect the $errorMessagePart from process output"))

    val structurePath = extractPathFromOutput(outputLines, StructureWrittenPrefix, "output structure path")
    val packageOutputDirPath = extractPathFromOutput(outputLines, PackageOutputDirPrefix, "package output directory")

    StructureDumpResult(
      structurePath,
      packageOutputDirPath,
    )
  }

  private def updateSbtVersion(repoDir: File, newSbtVersion: String): Unit = {
    val sbtPropertiesFile = repoDir / "project" / "build.properties"
    assert(sbtPropertiesFile.exists())
    val sbtVersionInRepo = readLines(sbtPropertiesFile)
      .find(_.startsWith("sbt.version"))
      .map(_.split("=").apply(1).trim)
      .get

    val updatedContent = s"sbt.version=$newSbtVersion"
    writeStringToFile(sbtPropertiesFile, updatedContent)
    println(s"Updated sbt.version: $sbtVersionInRepo -> $newSbtVersion")
  }

  private def updateSbtIdeaPluginToVersion(repoDir: File, sbtIdePluginVersion: String): Path = {
    val pluginsSbtFile = repoDir / "project" / "plugins.sbt"
    val contentExisting = readLines(pluginsSbtFile)
    val contentExistingFiltered = contentExisting
      .filterNot(_.contains("""addSbtPlugin("org.jetbrains" % "sbt-idea-plugin""""))
      .mkString("\n")
    val contentUpdated =
      s"""$contentExistingFiltered
         |addSbtPlugin("org.jetbrains" % "sbt-idea-plugin" % "$sbtIdePluginVersion")
         |""".stripMargin
    writeStringToFile(pluginsSbtFile, contentUpdated)
  }

  private object IoUtils {
    def readLines(file: File): Iterable[String] =
      Files.readAllLines(file.toPath, StandardCharsets.UTF_8).asScala

    def writeStringToFile(file: File, content: String): Path = {
      Files.write(file.toPath, content.getBytes(StandardCharsets.UTF_8), StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE)
    }

    def readBinaryData[T](file: File): T = {
      val stream = new FileInputStream(file)
      val reader = new ObjectInputStream(stream)
      val data = reader.readObject().asInstanceOf[T]
      reader.close()
      data
    }
  }


  private case class MyTestDataPaths private(
    buildDir: File,
    userHome: File,
    pluginOutputDir: File
  )

  private object MyTestDataPaths {
    def apply(buildDir: File, userHome: File, outputDir: File): MyTestDataPaths = {
      new MyTestDataPaths(
        buildDir.getCanonicalFile,
        userHome.getCanonicalFile,
        outputDir.getCanonicalFile,
      )
    }
  }
}