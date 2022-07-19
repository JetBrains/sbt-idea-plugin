package org.jetbrains.sbtidea.searchableoptions

import org.jetbrains.sbtidea.Keys.{intellijBaseDirectory, intellijVMOptions}
import org.jetbrains.sbtidea.packaging.PackagingKeys.packageArtifact
import org.jetbrains.sbtidea.packaging._
import org.jetbrains.sbtidea.packaging.artifact.DistBuilder
import org.jetbrains.sbtidea.runIdea.IdeaRunner
import org.jetbrains.sbtidea.{PluginLogger, SbtPluginLogger, pathToPathExt}
import sbt.Keys.{streams, target}
import sbt._

import java.nio.file.{Files, Path}
import java.util.function.Predicate
import scala.collection.JavaConverters._

object BuildIndex {

  private val IDX_DIR = "search"
  type IndexElement = (Path, Path) // jar file -> options.xml

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

    val indexRoots          = getIndexFiles(pluginRoot, indexOutputPath.toPath)
    val indexedMappings     = prepareMappings(indexRoots)

    if (indexRoots.isEmpty)
      log.error(s"No options search index built for plugin root: $pluginRoot")

    if (indexedMappings.isEmpty)
      log.error(s"No options search index packaged from given roots: $indexRoots")

    indexedMappings.foreach { case (jar, mapping) =>
      new DistBuilder(streams.value, target.value).patch(jar, Seq(mapping))
    }

    log.info(s"Successfully merged options index")
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

  private def prepareMappings(indexes: Seq[IndexElement]): Seq[(Path, Mapping)] =
    indexes.map { case (jar, indexXML) =>
      jar ->
        Mapping(indexXML.toFile,
          new File( s"$jar!/search/searchableOptions.xml"),
          MappingMetaData.EMPTY.copy(kind = MAPPING_KIND.MISC))
    }



}
