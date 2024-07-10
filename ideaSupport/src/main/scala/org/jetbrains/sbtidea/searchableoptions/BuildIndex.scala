package org.jetbrains.sbtidea.searchableoptions

import org.jetbrains.sbtidea.Keys.{productInfoExtraDataProvider, intellijBaseDirectory, intellijVMOptions}
import org.jetbrains.sbtidea.download.NioUtils
import org.jetbrains.sbtidea.download.plugin.LocalPluginRegistry
import org.jetbrains.sbtidea.packaging.*
import org.jetbrains.sbtidea.packaging.PackagingKeys.packageArtifact
import org.jetbrains.sbtidea.packaging.artifact.{DistBuilder, DumbIncrementalCache, IncrementalCache}
import org.jetbrains.sbtidea.runIdea.IdeaRunner
import org.jetbrains.sbtidea.{PathExt, PluginLogger, SbtPluginLogger}
import sbt.*
import sbt.Keys.{streams, target}

import java.nio.file.{Files, Path}
import java.util.function.Predicate
import scala.collection.JavaConverters.*

/**
 * Task to generate searchable options indices and insert them into the plugin jars
 * To generate the indices we run a headless idea with the `traverseUI` command, which will dump all indices
 * into <root>/target/searchableOptions.
 *
 * In the old version before 2024.2 there was one search index xml file per jar-file, which was dumped into
 * <root>/target/searchableOptions/<name-of-jar>/search/<name-of-jar>.searchableOptions.xml
 * and which we needed to copy to <jar-file>/search/searchableOptions.xml
 * for all jar files that belong to the plugin.
 *
 * Since 2024.2 there are json index files per plugin, which are dumped to
 * <root>/taget/serachableOptions/p-<plugin-id>-searchableOptions.json
 * and which we need to copy to <plugin-jar>/p-<plugin-id>-searchableOptions.json
 * plugin-jar is the jar archive that contains the meta-data for the plugin with plugin-id <plugin-id>.
 *
 * BuildIndex handles both cases simultaneously, so it should work in any version of intellij.
 */
object BuildIndex {
  private val IDX_DIR = "search"
  private trait IndexElement
  private object IndexElement {
    case class Old(jar: Path, xml: Path) extends IndexElement
    case class New(jar: Path, json: Path) extends IndexElement
  }

  def createTask: Def.Initialize[Task[Unit]] = Def.task {
    implicit val log: PluginLogger = new SbtPluginLogger(streams.value)

    val intellijBaseDir = intellijBaseDirectory.value
    val pluginRoot      = packageArtifact.value.toPath
    val indexOutputPath = target.value / "searchableOptions"
    val indexerCMD      = "traverseUI" :: indexOutputPath.getCanonicalPath :: "true" :: Nil
    val vmOptions       = intellijVMOptions.value.withOption("-Didea.l10n.keys=only")

    log.info("Building searchable plugin options index...")
    val runner = new IdeaRunner(
      intellijBaseDir.toPath,
      productInfoExtraDataProvider.value,
      vmOptions,
      blocking = true,
      programArguments = indexerCMD
    )
    runner.run()

    val tmp = Files.createTempDirectory("sbt-idea-searchable-options-building-")
    try {
      val indexRoots =
        getIndexFilesOld(pluginRoot, indexOutputPath.toPath) ++
          getIndexFilesNew(pluginRoot, indexOutputPath.toPath)
      val indexedMappings = prepareMappings(indexRoots, tmp)

      if (indexRoots.isEmpty)
        throw new MessageOnlyException(s"No options search index built for plugin root: $pluginRoot")

      if (indexedMappings.isEmpty)
        throw new MessageOnlyException(s"No options search index packaged from given roots: $indexRoots")

      indexedMappings.foreach { case (jar, mapping) =>
        val distBuilder = new DistBuilder(streams.value, target.value) {
          // Use dumb cache so that all files that we want to patch are marked as changed
          protected override lazy val incrementalCache: IncrementalCache = DumbIncrementalCache
        }
        distBuilder.patch(jar, Seq(mapping))
      }

      log.info(s"Successfully merged options index")
    } finally {
      NioUtils.delete(tmp)
    }
  }

  private def listArtifactJars(pluginOutputDir: Path): Map[String, Path] = {
    val predicate = new Predicate[Path] { override def test(p: Path): Boolean = p.toString.endsWith("jar") }
    Files.walk(pluginOutputDir)
      .filter(predicate)
      .iterator().asScala
      .map(path => path.getFileName.toString -> path)
      .toMap
  }

  private def getIndexFilesOld(pluginOutputDir: Path, indexOutputDir: Path): Seq[IndexElement] = {
    if (!indexOutputDir.exists)
      return Nil

    val allArtifactJars = listArtifactJars(pluginOutputDir)

    val indexesForPlugin: Seq[IndexElement] = indexOutputDir
      .list
      .filter(idx => allArtifactJars.contains(idx.getFileName.toString))
      .filter(idx => (idx / IDX_DIR).exists && (idx / IDX_DIR).isDir && (idx / IDX_DIR).list.nonEmpty)
      .map(idx => IndexElement.Old(allArtifactJars(idx.getFileName.toString), (idx / IDX_DIR).list.head))

    indexesForPlugin
  }

  private def getIndexFilesNew(pluginOutputDir: Path, indexOutputDir: Path): Seq[IndexElement] = {
    if (!indexOutputDir.exists)
      return Nil

    val allArtifactJars = listArtifactJars(pluginOutputDir)

    def getPluginId(jarPath: Path): Option[String] =
      LocalPluginRegistry.extractPluginMetaData(jarPath).toOption.map(_.id)

    for {
      jarPath <- allArtifactJars.values.toSeq
      pluginId <- getPluginId(jarPath)
      indexPath = indexOutputDir / s"p-$pluginId-searchableOptions.json"
      if Files.exists(indexPath)
    } yield IndexElement.New(jarPath, indexPath)
  }

  private def prepareMappings(indexes: Seq[IndexElement], tmp: Path)(implicit log: PluginLogger): Seq[(Path, Mapping)] =
    indexes.map {
      case IndexElement.Old(jar, indexXML) =>
        // copy the xml file into a temporary directory that has the required target structure: search/searchableOptions.xml
        val source = tmp / s"index-${jar.getFileName}-xml"
        val searchDir = source / "search"
        Files.createDirectories(searchDir)
        Files.copy(indexXML, searchDir / "searchableOptions.xml")

        log.info(s"SearchIndex Mapping: $indexXML -> ${jar / "search" / "searchableOptions.xml"}")

        jar ->
          Mapping(source.toFile,
            jar.toFile,
            MappingMetaData.EMPTY.copy(kind = MAPPING_KIND.MISC)
          )
      case IndexElement.New(jar, indexJson) =>
        val source = tmp / s"index-${jar.getFileName}-json"
        Files.createDirectories(source)
        Files.copy(indexJson, source / indexJson.getFileName.toString)

        log.info(s"SearchIndex Mapping: $indexJson -> ${jar / indexJson.getFileName.toString}")

        jar ->
          Mapping(source.toFile,
            jar.toFile,
            MappingMetaData.EMPTY.copy(kind = MAPPING_KIND.MISC)
          )
    }
}
