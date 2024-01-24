package org.jetbrains.sbtidea.packaging.artifact

import org.jetbrains.sbtidea.packaging.ExcludeFilter
import sbt.Keys.TaskStreams

import java.net.URI
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import java.util
import java.util.Collections
import scala.util.Using
import scala.util.Using.Releasable
import scala.util.control.NonFatal

trait JarPackager {
  def copySingleJar(from: Path): Unit
  def mergeIntoOne(source: Seq[Path]): Unit
}

class SimplePackager(protected val myOutput: Path,
                     private val shader: ClassShader,
                     private val excludeFilter: ExcludeFilter,
                     private val incrementalCache: IncrementalCache)(implicit private val streams: TaskStreams) extends JarPackager {

  private val myOutputExists = Files.exists(myOutput)

  private var counter = 0

  protected def outputExists(path: Path): Boolean = myOutputExists

  override def copySingleJar(from: Path): Unit = {
    if (!myOutputExists || fileChanged(from)) {
      if (!Files.exists(myOutput.getParent))
        Files.createDirectories(myOutput.getParent)
      Files.copy(from, myOutput, StandardCopyOption.REPLACE_EXISTING)
    }
  }

  override def mergeIntoOne(sources: Seq[Path]): Unit = {
    val fs = try createOutputFS(myOutput) catch {
      case NonFatal(ex) =>
        //note: the exception doesn't contain hint about the input
        throw new RuntimeException(s"Can't create input file system for path: $myOutput (${ex.getClass.getSimpleName} -${ex.getMessage})", ex)
    }
    usingFileSystem(fs) { fs =>
      sources.foreach(processSingleSource(_, fs))
    }
    if (counter > 0)
      streams.log.info(s"Wrote $counter files to $myOutput")
    counter = 0
  }

  protected def fileChanged(in: Path): Boolean = {
    incrementalCache.fileChanged(in)
  }

  protected def createInputFS(input: Path): FileSystem = {
    if (input.toString.contains("jar!")) {
      val Array(jarPath, _) = input.toString.split("!")
      FileSystems.newFileSystem(URI.create("jar:"+Paths.get(jarPath)), Collections.emptyMap[String, Any]())
    } else if (input.toString.endsWith("jar")) {
      FileSystems.newFileSystem(URI.create("jar:" + input.toUri), Collections.emptyMap[String, Any]())
    } else {
      input.getFileSystem
    }
  }

  protected def createOutputFS(output: Path): FileSystem = {
    val env = new util.HashMap[String, String]()
    env.put("create", String.valueOf(Files.notExists(output)))
    if (output.toString.contains("jar!")) {
      val Array(jarPath, _) = output.toString.split("!")
      FileSystems.newFileSystem(URI.create("jar:"+Paths.get(jarPath).toUri), env)
    } else if (output.toString.endsWith("jar")) {
      FileSystems.newFileSystem(URI.create("jar:" + output.toUri), env)
    } else {
      throw new RuntimeException(s"Output is not a jar file: $output")
    }
  }
  protected def createInput(input: Path, inputFS: FileSystem): Path = {
    if (input.toString.contains("jar!")) {
      val Array(_, location) = input.toString.split("!")
      inputFS.getPath(location)
    } else if (input.toString.endsWith("jar")) {
      inputFS.getPath(inputFS.getSeparator)
    } else {
      input
    }
  }

  protected def createOutput(srcPath: Path, output: Path, outputFS: FileSystem): Path = {
    if (output.toString.contains("jar!")) {
      val Array(_, newRoot) = output.toString.split("!")
      if (newRoot.endsWith(outputFS.getSeparator))
        outputFS.getPath(newRoot).resolve(srcPath.toString)  // copy file to dir
      else
        outputFS.getPath(newRoot)                            // copy file to file
    } else if (output.toString.endsWith("jar")) {
      outputFS.getPath(srcPath.toString)
    } else {
      throw new RuntimeException(s"Output is not a jar file: $output")
    }
  }

  private def processSingleSource(src: Path, outputFS: FileSystem): Unit = {
    if (!src.toString.contains("jar!") && !Files.exists(src))
      return

    val fs = try createInputFS(src) catch {
      case NonFatal(ex) =>
        //note: the exception doesn't contain hint about the input
        throw new RuntimeException(s"Can't create input file system for path: $src (${ex.getClass.getSimpleName} -${ex.getMessage})", ex)
    }
    usingFileSystem(fs) { fs =>
      val inputRoot = createInput(src, fs)
      walkEntry(inputRoot, outputFS) { (from, to) =>
        if (to.getParent != null)
          Files.createDirectories(to.getParent)
        shader.applyShading(from, to) {
          Files.copy(from, to, StandardCopyOption.REPLACE_EXISTING)
        }
      }
    }
  }

  private def usingFileSystem[R <: FileSystem : Releasable, A](fs: R)(body: R => A): A = {
    //default file system doesn't support "close" method and otherwise throw "UnsupportedOperationException"
    if (fs == FileSystems.getDefault) {
      body(fs)
    }
    else {
      Using.resource(fs)(body)
    }
  }

  private def walkEntry(root: Path, outputFS: FileSystem)(processor: (Path, Path) => Unit): Unit = {
    val visitor = new SimpleFileVisitor[Path]() {
      override def visitFile(p: Path, basicFileAttributes: BasicFileAttributes): FileVisitResult = {
        val perhapsRelativePath = Option(root.relativize(p))
          .filter(_.toString.nonEmpty)
          .getOrElse(Paths.get(p.getFileName.toString)) // copying single file - nothing to relativize against
        val newPathInJar = createOutput(perhapsRelativePath, myOutput, outputFS)
        if (!excludeFilter(perhapsRelativePath) && (!outputExists(newPathInJar) || fileChanged(p))) {
          processor(p, newPathInJar)
          counter += 1
        }
        FileVisitResult.CONTINUE
      }
    }
    Files.walkFileTree(root, visitor)
  }

}

class ZipPackager(myOutput: Path)(implicit private val streams: TaskStreams)
  extends SimplePackager(myOutput, new NoOpClassShader, ExcludeFilter.AllPass, DumbIncrementalCache) {

  override protected def createOutputFS(output: Path): FileSystem = {
    val env = new util.HashMap[String, String]()
    env.put("create", String.valueOf(Files.notExists(output)))
    FileSystems.newFileSystem(URI.create("jar:" + output.toUri), env)
  }

  override protected def createOutput(srcPath: Path, output: Path, outputFS: FileSystem): Path =
    outputFS.getPath(srcPath.toString)

  override protected def createInputFS(input: Path): FileSystem = input.getFileSystem

  override protected def createInput(input: Path, inputFS: FileSystem): Path = input

}