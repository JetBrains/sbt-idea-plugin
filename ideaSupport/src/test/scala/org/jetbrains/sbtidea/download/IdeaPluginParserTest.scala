package org.jetbrains.sbtidea.download

import org.jetbrains.sbtidea.Keys.IdeaPlugin._
import org.jetbrains.sbtidea.Keys._
import org.scalatest.{FunSuite, Matchers}

import scala.language.postfixOps

class IdeaPluginParserTest extends FunSuite with Matchers {

  private def id(str: String) = str.toPlugin.asInstanceOf[Id]

  test("Id parser should parse simple id") {
    "org.jetbrains.scala".toPlugin shouldBe a [Id]
  }

  test("plugin url should parse as Url") {
    "https://foo.bar/a.zip".toPlugin shouldBe an [Url]
    "http://foo.bar/a.zip".toPlugin shouldBe an [Url]
    "plugin:http://foo.bar/a.zip".toPlugin shouldBe an [Url]
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
