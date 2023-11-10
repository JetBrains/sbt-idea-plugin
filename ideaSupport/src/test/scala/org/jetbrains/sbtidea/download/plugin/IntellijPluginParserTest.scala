package org.jetbrains.sbtidea.download.plugin

import org.jetbrains.sbtidea.IntellijPlugin
import org.jetbrains.sbtidea.IntellijPlugin.*
import org.jetbrains.sbtidea.Keys.String2Plugin
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import java.net.URL
import scala.language.postfixOps

final class IntellijPluginParserTest extends AnyFunSuite with Matchers {

  private def id(str: String) = str.toPlugin.asInstanceOf[Id]

  test("Id parser should parse simple id") {
    "org.jetbrains.scala".toPlugin shouldBe a [Id]
  }

  test("plugin with url should parse as Id with optional URL set") {
    val p = "org.example.plugin:http://foo.bar/a.zip".toPlugin.asInstanceOf[IntellijPlugin.IdWithDownloadUrl]
    p.id shouldBe "org.example.plugin"
    p.downloadUrl shouldBe new URL("http://foo.bar/a.zip")
  }

  test("id should parse with with mixed segments") {
    id("foo").id shouldBe "foo"
    id("foo:").id shouldBe "foo"
    id("foo::").id shouldBe "foo"
    id("foo::baz").id shouldBe "foo"
    id("foo:123:").id shouldBe "foo"
    id("foo:123:baz").id shouldBe "foo"
  }

  test("parser should not parse invalid strings") {
    assertThrows[RuntimeException]("::".toPlugin)
    assertThrows[RuntimeException](":123:foo".toPlugin)
    assertThrows[RuntimeException](":123:".toPlugin)
  }
}
