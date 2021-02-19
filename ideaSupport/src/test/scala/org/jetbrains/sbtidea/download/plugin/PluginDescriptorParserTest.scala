package org.jetbrains.sbtidea.download.plugin

import org.jetbrains.sbtidea.ConsoleLogger
import org.jetbrains.sbtidea.download.idea.IdeaMock
import org.scalatest.{FunSuite, Matchers}
import PluginDescriptor.{Dependency, load}

class PluginDescriptorParserTest extends FunSuite with Matchers with IdeaMock with PluginMock with ConsoleLogger {

  private val pluginDescriptor = PluginDescriptor("id", "vendor", "name", "1.0", "111", "222",
    Seq(Dependency("foo", optional = true), Dependency("bar", optional = true)))

  test("parses simple plugin") {
    val descriptor = pluginDescriptor.copy(dependsOn = Nil)
    load(descriptor.toXMLStr) shouldBe descriptor
  }

  test("parses plugin with optional dependencies") {
    val descriptor = pluginDescriptor
    load(descriptor.toXMLStr) shouldBe descriptor
  }

  test("parses plugin with required dependencies") {
    val descriptor = pluginDescriptor
      .copy(dependsOn = Seq(Dependency("foo", optional = false), Dependency("bar", optional = false)))
    load(descriptor.toXMLStr) shouldBe descriptor
  }


  test("parses plugin with mixed dependencies") {
    val descriptor = pluginDescriptor
      .copy(dependsOn = Seq(Dependency("foo", optional = false), Dependency("bar", optional = true)))
    load(descriptor.toXMLStr) shouldBe descriptor
  }

  test("parses plugin absent until-build") {
    val descriptor = pluginDescriptor.copy(untilBuild = "")
    load(descriptor.toXMLStr) shouldBe descriptor
  }

}
