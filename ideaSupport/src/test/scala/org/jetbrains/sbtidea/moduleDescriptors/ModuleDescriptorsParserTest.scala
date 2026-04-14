package org.jetbrains.sbtidea.moduleDescriptors

import org.scalatest.funsuite.AnyFunSuiteLike
import org.scalatest.matchers.should.Matchers.*

import java.nio.file.Paths

class ModuleDescriptorsParserTest extends AnyFunSuiteLike {
  test("parse module descriptors") {
    val path = Paths.get(this.getClass.getClassLoader.getResource("org/jetbrains/sbtidea/moduleDescriptors/module-descriptors.jar").toURI)
    val moduleDescriptors = ModuleDescriptor.parseDescriptors(path)

    moduleDescriptors should have length 4

    val expectedDescriptors = Seq(
      ModuleDescriptor(
        name = "empty",
        namespace = Some("jetbrains"),
        visibility = None,
        dependencies = Seq.empty,
        resources = None,
      ),
      ModuleDescriptor(
        name = "intellij.java.psi",
        namespace = Some("jetbrains"),
        visibility = Some("public"),
        dependencies = Seq(
          Dependency("intellij.platform.core"),
          Dependency("intellij.platform.util"),
          Dependency("intellij.platform.util.multiplatform"),
          Dependency("intellij.platform.util.base"),
          Dependency("intellij.java.frontback.psi"),
          Dependency("kotlin-stdlib"),
          Dependency("intellij.java.syntax"),
          Dependency("intellij.libraries.fastutil"),
          Dependency("intellij.libraries.kotlinx.collections.immutable"),
        ),
        resources = Some(Resources(ResourceRoot("../plugins/java/lib/java-impl.jar"))),
      ),
      ModuleDescriptor(
        name = "intellij.libraries.jackson.annotations",
        namespace = Some("jetbrains"),
        visibility = Some("public"),
        dependencies = Seq.empty,
        resources = Some(Resources(ResourceRoot("../lib/intellij.libraries.jackson.annotations.jar"))),
      ),
      ModuleDescriptor(
        name = "intellij.properties",
        namespace = None,
        visibility = None,
        dependencies = Seq(
          Dependency("intellij.platform.core"),
          Dependency("intellij.platform.analysis"),
          Dependency("intellij.platform.editor.ui"),
          Dependency("intellij.platform.ide"),
          Dependency("intellij.platform.projectModel"),
          Dependency("intellij.platform.util"),
          Dependency("lib.kotlin-stdlib"),
          Dependency("intellij.platform.codeStyle"),
          Dependency("intellij.properties.psi"),
          Dependency("intellij.platform.core.impl"),
          Dependency("intellij.platform.core.ui"),
          Dependency("intellij.platform.codeStyle.impl"),
          Dependency("intellij.platform.lang.impl"),
          Dependency("intellij.platform.ide.impl"),
          Dependency("intellij.platform.refactoring"),
          Dependency("intellij.platform.lang"),
        ),
        resources = Some(Resources(ResourceRoot("../plugins/properties/lib/properties.jar"))),
      ),
    )

    moduleDescriptors should contain theSameElementsAs expectedDescriptors
  }
}
