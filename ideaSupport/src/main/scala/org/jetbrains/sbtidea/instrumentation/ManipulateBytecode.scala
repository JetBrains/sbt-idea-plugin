package org.jetbrains.sbtidea.instrumentation

import org.jetbrains.sbtidea.Keys.instrumentThreadingAnnotations
import sbt.*
import sbt.Keys.*
import sbt.internal.inc.{Analysis, Stamps}
import xsbti.compile.CompileResult
import xsbti.compile.analysis.Stamp
import xsbti.{FileConverter, VirtualFileRef}

import java.nio.file.Path

object ManipulateBytecode {
  def manipulateBytecodeTask(config: Configuration): Def.Initialize[Task[CompileResult]] = Def.taskDyn {
    val doInstrument = instrumentThreadingAnnotations.value
    val currentResult = (config / manipulateBytecode).value
    if (doInstrument) {
      instrumentTask(config, currentResult)
    } else {
      Def.task(currentResult)
    }
  }

  private def instrumentTask(config: Configuration, currentResult: CompileResult): Def.Initialize[Task[CompileResult]] = Def.task {
    val previousResult = (config / previousCompile).value
    val converter = fileConverter.value

    val previousAnalysis = previousResult.analysis().asScala.collect { case a: Analysis => a }.getOrElse(Analysis.empty)
    val currentAnalysis = currentResult.analysis() match { case a: Analysis => a }

    val changed = changedClasses(currentAnalysis.stamps, previousAnalysis.stamps, converter)
    changed.foreach(ThreadingAnnotationInstrumenter.instrument)

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
