package org.jetbrains.sbt
package tasks

import java.net.URI
import java.nio.file.FileSystems.newFileSystem
import java.nio.file._
import java.nio.file.attribute.BasicFileAttributes
import java.util

import org.jetbrains.sbtidea.Keys.PackagingMethod
import org.jetbrains.sbtidea.Keys.PackagingMethod.{MergeIntoOther, MergeIntoParent, Skip, Standalone}
import sbt.Def.Classpath
import sbt.Keys.moduleID
import sbt.jetbrains.apiAdapter._
import sbt.{File, ModuleID, ProjectRef, _}

object PluginPackager {
  case class ModuleKey(id:ModuleID, attributes: Map[String,String]){
    override def equals(o: scala.Any): Boolean = o match {
      case ModuleKey(_id, _attributes) =>
        id.organization.equals(_id.organization) &&
          id.name.matches(_id.name) &&
          id.revision.matches(_id.revision) &&
          attributes == _attributes
      case _ => false
    }
    override def hashCode(): Int = id.organization.hashCode
  }
  case class ProjectData(thisProject: ProjectRef,
                         cp: Classpath, productDirs: Seq[File],
                         libMapping: Seq[(ModuleID, Option[String])],
                         additionalMappings: Seq[(File, File)],
                         packageMethod: PackagingMethod)

  def artifactMappings(rootProject: ProjectRef,
            outputDir: File,
            projectsData: Seq[ProjectData],
            buildDependencies: BuildDependencies): Map[File, File] = {

    def mkProjectData(projectData: ProjectData): ProjectData = {
      if (projectData.thisProject == rootProject && !projectData.packageMethod.isInstanceOf[Standalone]) {
        projectData.copy(packageMethod = Standalone())
      } else projectData
    }

    val projectMap = projectsData.iterator.map(x => x.thisProject -> mkProjectData(x) ).toMap
    val revProjectMap = projectsData.iterator.flatMap(x => buildDependencies.classpathRefs(x.thisProject).map(_ -> x.thisProject)).toMap


    def buildStructure(ref: ProjectRef): Map[File, File] = {
      def findParentToMerge(ref: ProjectRef): ProjectRef = projectMap.getOrElse(ref,
        throw new RuntimeException(s"Project $ref has no parent to merge into")) match {
          case ProjectData(p, _, _, _, _, _: Standalone) => p
          case _ => findParentToMerge(revProjectMap(ref))
      }

      var artifactMap = Map[File, File]()
      val ProjectData(_, cp, productDirs, libMapping, additionalMappings, method) = projectMap(ref)
      val mappings = new util.HashMap[ModuleKey, Option[String]]()
      libMapping.foreach(m => mappings.put(moduleKey(m._1), m._2))
      val resolvedLibs = for {
        jarFile <- cp
        moduleId <- jarFile.get(moduleID.key)
      } yield { (moduleKey(moduleId), jarFile.data) }

      val processedLibs = resolvedLibs.collect {
        case (mod, file) if !mappings.containsKey(mod)                 => file -> outputDir / mkRelativeLibPath(file)
        case (mod, file) if mappings.getOrDefault(mod, None).isDefined => file -> outputDir / mappings.get(mod).get
      }

      method match {
        case Skip() =>
        case MergeIntoParent() =>
          val parent = findParentToMerge(ref)
          val parentFile = mkProjectJarPath(parent)
            productDirs.foreach { artifactMap += _ -> outputDir / parentFile }
        case MergeIntoOther(projectRef) =>
          val otherFile = mkProjectJarPath(projectRef)
          productDirs.foreach { artifactMap += _ -> outputDir/ otherFile }
        case Standalone(targetFile) =>
          val file = targetFile.getOrElse(outputDir / mkProjectJarPath(ref))
          productDirs.foreach { artifactMap += _ -> file }
      }

      buildDependencies.classpathRefs(ref).map(buildStructure).foreach(artifactMap ++= _)

      artifactMap ++= processedLibs
      artifactMap ++= additionalMappings

      artifactMap
    }

    buildStructure(rootProject)
  }

  def packageArtifact(structure: Map[File, File]): Unit = structure.foreach { entry =>
    val (from, to) = entry
    if (from.isDirectory) {
      if (to.isDirectory) IO.copyDirectory(from, to)
      else                zip(from, to)
    } else {
      if (to.isDirectory) IO.copy(Seq(from -> to))
      else                zip(from, to)
    }
  }


  private def zip(input: File, output: File): Unit = {
    if (!output.exists()) output.getParentFile.mkdirs()
    val env = new util.HashMap[String, String]()
    env.put("create", String.valueOf(Files.notExists(output.toPath)))
    val jarFs     = newFileSystem(URI.create("jar:" + output.toPath.toUri), env)
    val inputFS   = if (input.getName.endsWith(".jar")) Some(newFileSystem(URI.create( "jar:" + input.toPath.toUri), env)) else None
    val inputPath = inputFS.map(_.getPath("/")).getOrElse(input.toPath)

    try {
      Files.walkFileTree(inputPath, new SimpleFileVisitor[Path]() {
        override def visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult = {
          val newFilePath = jarFs.getPath(inputPath.relativize(file).toString)
          if (newFilePath.getParent != null) Files.createDirectories(newFilePath.getParent)
          Files.copy(file, newFilePath, StandardCopyOption.REPLACE_EXISTING)
          FileVisitResult.CONTINUE
        }
      })
    } finally {
      inputFS.foreach(_.close())
      jarFs.close()
    }
  }


  /**
    * Extract only key-relevant parts of the ModuleId, so that mappings succeed even if they contain extra attributes
    */
  private def moduleKey(moduleId: ModuleID): ModuleKey =
    ModuleKey (
      moduleId.organization % moduleId.name % moduleId.revision,
      moduleId.extraAttributes
        .map { case (k,v) => k.stripPrefix("e:") -> v}
        .filter { case (k,_) => k == "scalaVersion" || k == "sbtVersion" }
    )

  private def mkProjectJarPath(ref: ProjectRef) = s"${ref.project}.jar"

  private def mkRelativeLibPath(lib: File) = s"lib/${lib.getName}"
}
