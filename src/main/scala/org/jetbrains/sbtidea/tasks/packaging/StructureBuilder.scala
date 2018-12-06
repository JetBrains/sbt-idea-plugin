package org.jetbrains.sbtidea.tasks.packaging

import org.jetbrains.sbtidea.Keys.PackagingMethod._
import sbt.Def.Classpath
import sbt.Keys.{TaskStreams, moduleID}
import sbt._
import sbt.jetbrains.ideaPlugin.apiAdapter._

import scala.collection.mutable

class StructureBuilder(private val streams: TaskStreams) {

  def artifactMappings(rootProject: ProjectRef,
                       outputDir: File,
                       projectsData: Seq[ProjectData],
                       buildDependencies: BuildDependencies): Mappings = {

    def mkProjectData(projectData: ProjectData): ProjectData = {
      if (projectData.thisProject == rootProject && !projectData.packageMethod.isInstanceOf[Standalone]) {
        projectData.copy(packageMethod = Standalone())
      } else projectData
    }

    val projectMap    = projectsData.iterator.map(x => x.thisProject -> mkProjectData(x)).toMap
    val revProjectMap = projectsData.flatMap(x => buildDependencies.classpathRefs(x.thisProject).map(_ -> x.thisProject))

    def findProjectRef(project: Project): Option[ProjectRef] = projectMap.find(_._1.project == project.id).map(_._1)

    def walk(ref: ProjectRef, queue: Seq[ProjectRef]): Seq[ProjectRef] = {
      val data = projectMap(ref)
      if (!queue.contains(ref)) {
        val newQueue = queue :+ ref
        val direct = buildDependencies.classpathRefs(ref).foldLeft(newQueue) { case (q, r) => walk(r, q) }
        val additional = data.additionalProjects.flatMap(findProjectRef).foldLeft(direct) { case (q, r) => walk(r, q) }
        additional
      } else { queue }
    }

    def buildStructure(ref: ProjectRef): Mappings = {
      val artifactMap = new mutable.TreeSet[Mapping]()

      def findParentToMerge(ref: ProjectRef): ProjectRef = projectMap.getOrElse(ref,
        throw new RuntimeException(s"Project $ref has no associated ProjectData")) match {
        case ProjectData(p, _, _, _, _, _, _, _, _, _: Standalone, _, _) => p
        case ProjectData(_, _, _, _, _, _, _, _, _, _: Skip, _, _)       => null
        case _ =>
          val xx = revProjectMap.filter(_._1 == ref).map(_._2).map(findParentToMerge).filter(_ != null)
          if (xx.size > 1) throw new RuntimeException(s"Multiple parents found for $ref: $xx")
          if (xx.isEmpty) throw new RuntimeException(s"No parents found for $ref")
          xx.head
      }

      val ProjectData(_,
                      cp,
                      definedDeps,
                      _,
                      assembleLibraries,
                      productDirs,
                      report,
                      libMapping,
                      additionalMappings,
                      method,
                      shadePatterns,
                      excludeFilter) = projectMap(ref)

      implicit val scalaVersion: ProjectScalaVersion = ProjectScalaVersion(definedDeps.find(_.name == "scala-library"))

      val resolver              = new TransitiveDeps(report, "compile")
      val mappings              = libMapping.map(x => x._1.key -> x._2).toMap
      val resolvedLibsNoEvicted = buildModuleIdMap(cp)
      val resolvedLibs          = updateWithEvictionMappings(resolvedLibsNoEvicted, resolver.evicted)
      val transitiveDeps        = definedDeps
        .filter(_.configurations.isEmpty)
        .map(_.key)
        .flatMap(resolver.collectTransitiveDeps)
      val processedLibs = transitiveDeps.map(m => m -> resolvedLibs.get(m))
        .map {
          case x@(mod, None) => streams.log.warn(s"couldn't resolve dependency jar: $mod"); x
          case other => other
        }.collect {
        case (mod, Some(file)) if !mappings.contains(mod)                 => file -> outputDir / mkRelativeLibPath(file)
        case (mod, Some(file)) if mappings.getOrElse(mod, None).isDefined => file -> outputDir / mappings(mod).get
      }

      val defaultMetaData = MappingMetaData(shadePatterns, excludeFilter, static = true, project = Some(ref.project), MAPPING_KIND.TARGET)

      val targetJar = method match {
        case Skip() => None
        case DepsOnly(targetPath) =>
          Some(outputDir / targetPath)
        case MergeIntoParent() =>
          val parent = findParentToMerge(ref)
          val parentFile = mkProjectJarPath(parent)
          productDirs.foreach { artifactMap += Mapping(_, outputDir / parentFile, defaultMetaData)}
          Some(outputDir / parentFile)
        case MergeIntoOther(project) =>
          val parent = findParentToMerge(findProjectRef(project)
            .getOrElse(throw new RuntimeException(s"Couldn't resolve project $project")))
          val otherFile = mkProjectJarPath(parent)
          productDirs.map { artifactMap += Mapping(_, outputDir / otherFile, defaultMetaData) } //_ -> outputDir/ otherFile }
          Some(outputDir/ otherFile)
        case Standalone("", isStatic) =>
          val file = outputDir / mkProjectJarPath(ref)
          productDirs.foreach { artifactMap += Mapping(_, file, defaultMetaData.copy(static = isStatic)) }
          Some(file)
        case Standalone(targetPath, isStatic) =>
          val file = outputDir / targetPath
          productDirs.foreach { artifactMap += Mapping(_, file, defaultMetaData.copy(static = isStatic))  }
          Some(file)
      }

      targetJar match {
        case Some(file) if assembleLibraries =>
          artifactMap ++= processedLibs.map { case (in, _) => Mapping(in, file, defaultMetaData.copy(kind = MAPPING_KIND.LIB_ASSEMBLY)) }
        case _ =>
          artifactMap ++= processedLibs.map { case (in, out) => Mapping(in, out, defaultMetaData.copy(kind = MAPPING_KIND.LIB)) }
      }

      artifactMap ++= additionalMappings
        .map { case (from, to) => Mapping(from, outputDir / fixPaths(to), defaultMetaData.copy(kind = MAPPING_KIND.MISC)) }

      artifactMap.toSeq
    }

    streams.log.info("traversing dependency graph")
    val queue       = walk(rootProject, Seq.empty).reverse
    streams.log.info(s"built processing queue: ${queue.map(_.project)}")
    streams.log.info(s"building mappings")
    val structures  = queue.map(buildStructure)
    val result      = new mutable.TreeSet[Mapping]()
    structures.foreach(result ++= _)
    streams.log.info(s"finished building structure: got ${result.size} mappings")

    result.toSeq
  }

  private def buildModuleIdMap(cp: Classpath)(implicit scalaVersion: ProjectScalaVersion): Map[ModuleKey, File] = (for {
    jarFile <- cp
    moduleId <- jarFile.get(moduleID.key)
  } yield { moduleId.key -> jarFile.data }).toMap

  private def updateWithEvictionMappings(cpNoEvicted: Map[ModuleKey, File], evicted: Seq[ModuleKey]): Map[ModuleKey, File] = {
    val evictionSubstitutes = evicted
      .map(ev => ev -> cpNoEvicted.find(entry => entry._1 ~== ev).map(_._2)
        .getOrElse(throw new RuntimeException(s"Can't resolve eviction for $ev")))
    cpNoEvicted ++ evictionSubstitutes
  }

  private def mkProjectJarPath(project: ProjectReference): String = s"lib/${extractName(project)}.jar"

  private def mkRelativeLibPath(lib: File) = s"lib/${lib.getName}"

  private def extractName(project: ProjectReference): String = {
    val str = project.toString
    val commaIdx = str.indexOf(',')
    str.substring(commaIdx+1, str.length-1)
  }

  private def fixPaths(str: String): String = System.getProperty("os.name") match {
    case os if os.startsWith("Windows") => str.replace('/', '\\')
    case _ => str.replace('\\', '/')
  }

}
