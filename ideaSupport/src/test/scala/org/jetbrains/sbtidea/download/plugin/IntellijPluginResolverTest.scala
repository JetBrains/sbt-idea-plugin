package org.jetbrains.sbtidea.download.plugin

import org.jetbrains.sbtidea.CapturingLogger._
import org.jetbrains.sbtidea.Keys._
import org.jetbrains.sbtidea.pathToPathExt
import sbt._

import scala.language.implicitConversions

class IntellijPluginResolverTest extends IntellijPluginResolverTestBase {

  test("plugin resolver doesn't resolve fake plugin") {
    val fakePlugin = "org.myFake.plugin:0.999:trunk".toPlugin
    val messages = captureLog(new PluginResolver().resolve(fakePlugin) shouldBe empty)
    messages should contain ("Failed to resolve PluginDependency(org.myFake.plugin): null")
  }

  test("transitive plugin dependencies are resolved") {
    val res = new PluginResolver().resolve(pluginC)
    inside(res) {
      case RemotePluginArtifact(c, _) :: LocalPlugin(a, _, _) :: RemotePluginArtifact(b, _) :: Nil =>
        c shouldBe descriptor2Plugin(pluginC)
        a shouldBe descriptor2Plugin(pluginA)
        b shouldBe descriptor2Plugin(pluginB)
    }
  }

  test("cyclic dependencies are detected") {
    val resolver = new PluginResolver()
    val (messages, result) = captureLogAndValue(resolver.resolve(pluginE))
    messages shouldBe Seq("Circular plugin dependency detected: PluginDependency(org.E) already processed")
    result.size shouldBe 3
  }

}
