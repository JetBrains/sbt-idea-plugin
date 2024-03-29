package org.jetbrains.sbtidea

import org.jetbrains.sbtidea.IntelliJPlatform.IdeaCommunity
import org.jetbrains.sbtidea.runIdea.IntellijVMOptions
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import java.nio.file.{Files, Paths}

class IdeaVmOptionsTest extends AnyFunSuite with Matchers {

  private val vmOpts = IntellijVMOptions(
    IdeaCommunity,
    Paths.get("foo bar"),
    Paths.get("bar baz"),
    Paths.get("test non-existent intellijDirectory")
  )
  private val Q = "&quot;"

  private val mustBeQuoted = Seq(
    "plugin.path",
    "idea.system.path",
    "idea.config.path"
  )

  test("paths are quoted in xml version") {
    val asSeq = vmOpts.asSeq(quoteValues = true)
    val quotedKeys = asSeq.filter(s => mustBeQuoted.exists(s.contains(_)))
    quotedKeys.size shouldBe mustBeQuoted.size
    quotedKeys.foreach { option =>
      option should endWith regex s"=$Q.+$Q$$"
    }
  }

  test("paths are NOT quoted in SBT version") {
    val asSeq = vmOpts.asSeq(quoteValues = false)
    val quotedKeys = asSeq.filter(s => mustBeQuoted.exists(s.contains(_)))
    quotedKeys.size shouldBe mustBeQuoted.size
    quotedKeys.foreach { option =>
      option shouldNot endWith regex s"=$Q.+$Q$$"
    }
  }
}
