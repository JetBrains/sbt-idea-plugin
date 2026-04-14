package org.jetbrains.sbtidea.moduleDescriptors

import org.jetbrains.sbtidea.models.XmlDecoder

import java.nio.file.Path
import java.util.jar.JarFile
import scala.jdk.CollectionConverters.enumerationAsScalaIteratorConverter
import scala.util.Using
import scala.xml.Elem

/**
 * The class represents the structure of XML descriptors inside `modules/module-descriptors.jar` file in IntelliJ installation.
 *
 * Similar entity from Gradle plugin:<br>
 * https://github.com/JetBrains/intellij-platform-gradle-plugin/blob/12b993e2a56a66c6fdde72deb0bebb02a1635622/src/main/kotlin/org/jetbrains/intellij/platform/gradle/models/ModuleDescriptor.kt#L11
 */
final case class ModuleDescriptor(
  name: String,
  namespace: Option[String] = None,
  visibility: Option[String] = None,
  dependencies: Seq[Dependency] = Seq.empty,
  resources: Option[Resources] = None,
) {
  lazy val path: Option[String] = resources.map(_.resourceRoot.path.stripPrefix("../"))
}

final case class Dependency(name: String)

final case class Resources(resourceRoot: ResourceRoot)

final case class ResourceRoot(path: String)

object ModuleDescriptor extends XmlDecoder[ModuleDescriptor] {
  override def decode(xml: Elem): ModuleDescriptor = {
    val name = xml \@ "name"
    val namespace = (xml \\ "@namespace").headOption.map(_.text)
    val visibility = (xml \\ "@visibility").headOption.map(_.text)
    val dependencies = (xml \\ "dependencies" \\ "module").map(d => Dependency(d \@ "name"))
    val resources = (xml \\ "resources").headOption.map { resourcesXml =>
      val resourceRoot = resourcesXml \\ "resource-root" \@ "path"
      Resources(ResourceRoot(resourceRoot))
    }
    ModuleDescriptor(name, namespace, visibility, dependencies, resources)
  }

  def parseDescriptors(moduleDescriptorsJarFile: Path): Seq[ModuleDescriptor] =
    Using.resource(new JarFile(moduleDescriptorsJarFile.toFile)) { jarFile =>
      val entryList = jarFile.entries().asScala.toList

      val xmlEntries = entryList.filter(_.getName.endsWith(".xml"))
      val descriptors = xmlEntries
        .map(jarFile.getInputStream)
        .map(ModuleDescriptor.decode)
      descriptors
    }
}
