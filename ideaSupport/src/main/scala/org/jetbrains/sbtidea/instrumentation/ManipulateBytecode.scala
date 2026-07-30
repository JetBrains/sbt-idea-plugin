package org.jetbrains.sbtidea.instrumentation

import org.jetbrains.sbtidea.Keys.{instrumentNotNullAnnotations, instrumentThreadingAnnotations, notNullAnnotations}
import sbt.*
import sbt.Keys.*
import sbt.internal.inc.{Analysis, Stamps}
import xsbti.compile.CompileResult
import xsbti.compile.analysis.Stamp
import xsbti.{FileConverter, VirtualFileRef}

import java.net.URLClassLoader
import java.nio.file.Path
import scala.util.control.NonFatal

object ManipulateBytecode {
  def manipulateBytecodeTask(config: Configuration): Def.Initialize[Task[CompileResult]] = Def.taskDyn {
    val instrumentThreading = instrumentThreadingAnnotations.value
    val instrumentNotNull = instrumentNotNullAnnotations.value
    val currentResult = (config / manipulateBytecode).value
    if (instrumentThreading || instrumentNotNull) {
      instrumentTask(config, currentResult, instrumentThreading, instrumentNotNull)
    } else {
      Def.task(currentResult)
    }
  }

  private def instrumentTask(
    config: Configuration,
    currentResult: CompileResult,
    instrumentThreading: Boolean,
    instrumentNotNull: Boolean
  ): Def.Initialize[Task[CompileResult]] = Def.task {
    val previousResult = (config / previousCompile).value
    val converter = fileConverter.value
    val annotations = notNullAnnotations.value
    // fullClasspath cannot be used here: it depends on the compile task, which would create a cycle with manipulateBytecode
    val classpath = (config / classDirectory).value +: (config / dependencyClasspath).value.map(_.data)

    val previousAnalysis = previousResult.analysis().asScala.collect { case a: Analysis => a }.getOrElse(Analysis.empty)
    val currentAnalysis = currentResult.analysis() match { case a: Analysis => a }

    val changed = changedClasses(currentAnalysis.stamps, previousAnalysis.stamps, converter)
    if (instrumentThreading) {
      changed.foreach(ThreadingAnnotationInstrumenter.instrument)
    }
    if (instrumentNotNull) {
      val classpathLoader = new URLClassLoader(classpath.map(_.toURI.toURL).toArray)
      try {
        changed.foreach { classFile =>
          try {
            NotNullInstrumenter.instrument(classFile, annotations, classpathLoader)
          } catch {
            case NonFatal(e) =>
              throw new MessageOnlyException(s"Failed to instrument @NotNull assertions into $classFile: ${e.getMessage}")
          }
        }
      } finally {
        classpathLoader.close()
      }
    }

    val stamper = Stamps.timeWrapBinaryStamps(converter)

    val newStamps = changed.foldLeft(currentAnalysis.stamps) { case (stamps, cls) =>
      val vf = converter.toVirtualFile(cls)
      val s = stamper.product(vf)
      stamps.markProduct(vf, s)
    }

    val newAnalysis = currentAnalysis.copy(stamps = newStamps)
    currentResult.withAnalysis(newAnalysis)
  }

  /**
   * Computes the paths of `.class` files that have changed in the last compilation cycle.
   *
   * @note Adapted from
   *       https://github.com/scalacenter/scala-debug-adapter/blob/0760f6feea0973d8b2dca922adf1ff2c1bfb7d2e/modules/sbt-plugin/src/main/scala/ch/epfl/scala/debugadapter/sbtplugin/DebugAdapterPlugin.scala#L133-L160.
   */
  private def changedClasses(currentStamps: Stamps, previousStamps: Stamps, converter: FileConverter): Seq[Path] = {
    def changed(current: Stamp, previous: Option[Stamp]): Boolean =
      previous match {
        case Some(previous) =>
          (current.getHash.asScala, previous.getHash.asScala) match {
            case (Some(curr), Some(prev)) => curr != prev
            case (Some(_), None) => true
            case (None, _) => false
          }
        case None => true
      }

    object ClassFile {
      def unapply(vf: VirtualFileRef): Option[Path] =
        Option(converter.toPath(vf)).filter(_.toString.endsWith(".class"))
    }

    val previousProducts = previousStamps.products
    currentStamps.products.collect {
      case (vf @ ClassFile(path), stamp) if changed(stamp, previousProducts.get(vf)) => path
    }.toSeq
  }
}
