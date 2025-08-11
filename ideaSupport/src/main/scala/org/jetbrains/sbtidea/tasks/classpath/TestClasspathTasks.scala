package org.jetbrains.sbtidea.tasks.classpath

import org.jetbrains.sbtidea.packaging.PackagingKeys.packageOutputDir
import sbt.*
import sbt.Def.Classpath
import sbt.Keys.{exportedProductsNoTracking, externalDependencyClasspath}

import scala.collection.mutable

/**
 * Tasks which generate the classpath for running tests within sbt and in the IDE.
 */
object TestClasspathTasks {

  /**
   * When we run tests within sbt, the plugin is already compiled. Thus, we expand the packaged plugin jars
   * and put them in front of the common classpath.
   */
  def fullTestClasspathForSbt: Def.Initialize[Task[Classpath]] = Def.task {
    val outputDir = packageOutputDir.value

    val pathFinder = PathFinder.empty +++ // the new IJ plugin loading strategy in tests requires external plugins to be prepended to the classpath
      outputDir * globFilter("*.jar") +++
      outputDir / "lib" * globFilter("*.jar") +++
      outputDir / "lib" / "modules" ** globFilter("*.jar")

    pathFinder.classpath ++ commonTestClasspath.value
  }

  /**
   * We generate the JUnit test Run Configuration during project import. The plugin has not been compiled
   * and assembled at this point. In order to artificially include the assembled plugin on the classpath,
   * we use placeholder patterns.
   */
  def fullTestClasspathForJUnitTemplate: Def.Initialize[Task[Seq[String]]] = Def.task {
    val outputDir = packageOutputDir.value
    val pluginPlaceholderPatterns = PluginClasspathUtils.pluginClasspathPattern(outputDir)
    val commonClasspath = commonTestClasspath.value
    pluginPlaceholderPatterns ++ commonClasspath.map(_.data.getAbsolutePath)
  }

  /**
   * This is the common part of the classpath reused between tests which are running in sbt
   * and the JUnit test Run Configuration template which we use to generate a correct run configuration
   * when running tests in the IDE.
   */
  private def commonTestClasspath: Def.Initialize[Task[Classpath]] = Def.task {
    // `fullClasspath` is a (distinct) concatenation of `exportedProducts` and `dependencyClasspath`.
    // `exportedProducts` triggers compilation to produce its output, `exportedProductsNoTracking` doesn't.
    // We need to use `fullClasspath` during the IDEA sbt import process. However, we do not want to compile
    // the whole project in order to do so, particularly because compilation errors will fail the project import.
    // This leads to situations where we cannot import the project because it cannot be compiled, yet we need IDE
    // import to succeed such that we can get IDE support in order to fix the compilation.
    // Thus, we manually concatenate `exportedProductsNoTracking` and `dependencyClasspath` in order to construct
    // the `fullClasspath` ourselves, without triggering compilation.
    // Furthermore, `dependencyClasspath` is a (distinct) concatenation of `internalDependencyClasspath` and
    // `externalDependencyClasspath`.
    // `internalDependencyClasspath` also triggers compilation. But in this context, this is not necessary for us.
    // We are only looking for the `Test / classDirectory` (`<subproject>/target/scala-<version>/test-classes`) for
    // each dependency of the current sbt subproject.
    // `classDirectory` return a regular `File`, so we need to transform it into an `Attributed[File]` value.
    // We do this in `AttributedClasspathTasks.attributedClassDirectory` as it is a slightly more involved process.
    // `externalDependencyClasspath` on the other hand is safe to call without triggering compilation, and it returns
    // the list of external managed and unmanaged jar dependencies.
    val testExportedProducts = exportedProductsNoTracking.in(Test).value
    val testClassDirectories = AttributedClasspathTasks.attributedClassDirectory.all(ScopeFilter(inDependencies(ThisProject), inConfigurations(Test))).value
    val testExternalDependencyClasspath = externalDependencyClasspath.in(Test).value

    // Recreate the `fullClasspath` value by concatenating `exportedProductsNoTracking` and our custom
    // `dependencyClasspath` and calling `.distinct` on the resulting sequence.
    val fullClasspathValue = (testExportedProducts ++ testClassDirectories ++ testExternalDependencyClasspath).distinct

    val allExportedProducts = exportedProductsNoTracking.all(ScopeFilter(inDependencies(ThisProject), inConfigurations(Compile))).value.flatten
    (fullClasspathValue.to[mutable.LinkedHashSet] -- allExportedProducts.toSet).toSeq // exclude classes already in the artifact
  }
}
