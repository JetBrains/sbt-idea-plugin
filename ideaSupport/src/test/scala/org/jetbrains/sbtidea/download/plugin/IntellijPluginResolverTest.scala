package org.jetbrains.sbtidea.download.plugin

import org.jetbrains.sbtidea.*
import org.jetbrains.sbtidea.CapturingLogger.*
import org.jetbrains.sbtidea.Keys.String2Plugin

import scala.language.implicitConversions

class IntellijPluginResolverTest extends IntellijPluginResolverTestBase {

  test("plugin resolver doesn't resolve fake plugin") {
    val fakePlugin = "org.myFake.plugin:0.999:trunk".toPlugin
    val messages = captureLog(new PluginResolver(resolveSettings = fakePlugin.resolveSettings).resolve(fakePlugin) shouldBe empty)
    messages should contain ("[error] Failed to resolve PluginDependency(org.myFake.plugin): java.lang.RuntimeException: TestPluginRepoApi error for getRemotePluginXmlDescriptor")
  }

  test("transitive plugin dependencies are resolved") {
    val res = new PluginResolver(resolveSettings = pluginC.plugin.resolveSettings).resolve(pluginC)
    inside(res) {
      case RemotePluginArtifact(c, _) :: LocalPlugin(a, _, _) :: RemotePluginArtifact(b, _) :: Nil =>
        c shouldBe descriptor2Plugin(pluginC)
        a shouldBe descriptor2Plugin(pluginA)
        b shouldBe descriptor2Plugin(pluginB)
    }
  }

  test("cyclic dependencies are detected") {
    val resolver = new PluginResolver(resolveSettings = pluginE.plugin.resolveSettings)
    val (messages, result) = captureLogAndValue(resolver.resolve(pluginE))
    messages shouldBe Seq("[warn] Circular plugin dependency detected: PluginDependency(org.E) already processed")
    result.size shouldBe 3
  }

  test("plugin exclude rules work") {
    val newResolveSettings = IntellijPlugin.Settings(excludedIds = Set(pluginA.id))
    val res = new PluginResolver(resolveSettings = newResolveSettings).resolve(pluginC)
    inside(res) {
      case RemotePluginArtifact(c, _) :: RemotePluginArtifact(b, _) :: Nil =>
        c shouldBe descriptor2Plugin(pluginC)
//        a shouldBe descriptor2Plugin(pluginA)
        b shouldBe descriptor2Plugin(pluginB)
    }
  }

  test("disable transitive resolution") {
    val newResolveSettings = IntellijPlugin.Settings(transitive = false)
    val res = new PluginResolver(resolveSettings = newResolveSettings).resolve(pluginC)
    inside(res) {
      case RemotePluginArtifact(c, _) :: Nil => c shouldBe descriptor2Plugin(pluginC)
    }
  }

  test("disable optional plugins") {
    val newResolveSettings = IntellijPlugin.Settings(optionalDeps = false)
    val res = new PluginResolver(resolveSettings = newResolveSettings).resolve(pluginC)
    inside(res) {
      case RemotePluginArtifact(c, _) :: RemotePluginArtifact(b, _) :: Nil =>
        c shouldBe descriptor2Plugin(pluginC)
        //        a shouldBe descriptor2Plugin(pluginA)
        b shouldBe descriptor2Plugin(pluginB)
    }
  }

}
