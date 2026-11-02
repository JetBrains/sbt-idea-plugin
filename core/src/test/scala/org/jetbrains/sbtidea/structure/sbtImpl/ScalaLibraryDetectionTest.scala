package org.jetbrains.sbtidea.structure.sbtImpl

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import sbt.*

class ScalaLibraryDetectionTest extends AnyFunSuite with Matchers {

  private val scala2Library = "org.scala-lang" % "scala-library" % "2.13.18"
  private val scala3Library = "org.scala-lang" %% "scala3-library" % "3.3.7"
  private val resolvedScala3Library = "org.scala-lang" % "scala3-library_3" % "3.3.7"

  test("detects the Scala 3 library before cross-version suffixing") {
    detectMainScalaLibrary(Seq(scala3Library)) shouldBe Some(scala3Library)
  }

  test("prefers Scala 3 over Scala 2 before and after cross-version suffixing") {
    Seq(scala3Library, resolvedScala3Library).foreach { library =>
      withClue(s"${library.name}: ") {
        detectMainScalaLibrary(Seq(scala2Library, library)) shouldBe Some(library)
        detectMainScalaLibrary(Seq(library, scala2Library)) shouldBe Some(library)
      }
    }
  }

  test("continues to detect the Scala 2 library") {
    detectMainScalaLibrary(Seq(scala2Library)) shouldBe Some(scala2Library)
  }

  test("does not treat Scala compiler or module dependencies as the standard library") {
    val dependencies = Seq(
      "org.scala-lang" %% "scala3-compiler" % "3.3.7",
      "org.scala-lang" % "scala-reflect" % "2.13.18",
      "org.scala-lang.modules" %% "scala-xml" % "2.4.0"
    )
    detectMainScalaLibrary(dependencies) shouldBe None
    detectMainScalaLibrary(Seq.empty) shouldBe None
  }
}
