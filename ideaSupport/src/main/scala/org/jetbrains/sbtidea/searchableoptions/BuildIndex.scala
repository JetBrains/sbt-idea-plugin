package org.jetbrains.sbtidea.searchableoptions

import org.jetbrains.sbtidea.Keys.{intellijBaseDirectory, intellijVMOptions}
import org.jetbrains.sbtidea.download.NioUtils
import org.jetbrains.sbtidea.packaging.*
import org.jetbrains.sbtidea.packaging.PackagingKeys.packageArtifact
import org.jetbrains.sbtidea.packaging.artifact.{DistBuilder, DumbIncrementalCache, IncrementalCache}
import org.jetbrains.sbtidea.runIdea.IdeaRunner
import org.jetbrains.sbtidea.{PluginLogger, SbtPluginLogger, pathToPathExt}
import sbt.*
import sbt.Keys.{streams, target}

import java.nio.file.{Files, Path}
import java.util.function.Predicate
import scala.collection.JavaConverters.*

object BuildIndex {

  private val IDX_DIR = "search"
  private type IndexElement = (Path, Path) // jar file -> options.xml

  def createTask: Def.Initialize[Task[Unit]] = Def.task {
    implicit val log: PluginLogger = new SbtPluginLogger(streams.value)

    val intellijBaseDir = intellijBaseDirectory.value
    val pluginRoot      = packageArtifact.value.toPath
    val indexOutputPath = target.value / "searchableOptions"
    val indexerCMD      = "traverseUI" :: indexOutputPath.getCanonicalPath :: "true" :: Nil
    val vmOptions       = intellijVMOptions.value

    log.info("Building searchable plugin options index...")
    val runner = new IdeaRunner(intellijBaseDir.toPath, vmOptions, blocking = true, programArguments = indexerCMD)
    runner.run()

    val tmp = Files.createTempDirectory("sbt-idea-searchable-options-building-")
    try {
      val indexRoots = getIndexFiles(pluginRoot, indexOutputPath.toPath)
      val indexedMappings = prepareMappings(indexRoots, tmp)

      if (indexRoots.isEmpty)
        log.error(s"No options search index built for plugin root: $pluginRoot")

      if (indexedMappings.isEmpty)
        log.error(s"No options search index packaged from given roots: $indexRoots")


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


  private def getIndexFiles(pluginOutputDir: Path, indexOutputDir: Path): Seq[IndexElement] = {
    if (!indexOutputDir.exists)
      return Nil

    val predicate = new Predicate[Path] { override def test(p: Path): Boolean = p.toString.endsWith("jar") }

    val allArtifactJars = Files.walk(pluginOutputDir)
      .filter(predicate)
      .iterator().asScala
      .map(path => path.getFileName.toString -> path)
      .toMap

    val indexesForPlugin: Seq[(Path, Path)] = indexOutputDir
      .list
      .filter(idx => allArtifactJars.contains(idx.getFileName.toString))
      .filter(idx => (idx / IDX_DIR).exists && (idx / IDX_DIR).isDir && (idx / IDX_DIR).list.nonEmpty)
      .foldLeft(Seq.empty[IndexElement]) { (acc, idx) =>
        acc :+ allArtifactJars(idx.getFileName.toString) -> (idx / IDX_DIR).list.head
      }

    indexesForPlugin
  }

  private def prepareMappings(indexes: Seq[IndexElement], tmp: Path): Seq[(Path, Mapping)] =
    indexes.zipWithIndex.map { case ((jar, indexXML), idx) =>
      // copy the xml file into a temporary directory that has the required target structure: search/searchableOptions.xml
      val source = tmp / s"index-${jar.getFileName}-$idx"
      val searchDir = source / "search"
      Files.createDirectories(searchDir)
      Files.copy(indexXML, searchDir / "searchableOptions.xml")

      jar ->
        Mapping(source.toFile,
          jar.toFile,
          MappingMetaData.EMPTY.copy(kind = MAPPING_KIND.MISC))
    }
}
