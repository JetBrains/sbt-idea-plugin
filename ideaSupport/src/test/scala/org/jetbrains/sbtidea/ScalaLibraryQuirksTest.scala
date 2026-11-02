package org.jetbrains.sbtidea

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import sbt.{Keys as _, *}

class ScalaLibraryQuirksTest extends AnyFunSuite with Matchers {

  private val scalaLibraries = Seq(
    "org.scala-lang" % "scala-library" % "2.13.18",
    "org.scala-lang" %% "scala3-library" % "3.3.7",
    "org.scala-lang" % "scala3-library_3" % "3.3.7"
  )
  private val otherLibrary = "org.example" %% "application-library" % "1.0"

  test("marks Scala libraries as provided without changing other dependencies") {
    val testLibrary = otherLibrary % Test
    val dependencies = scalaLibraries ++ Seq(otherLibrary, testLibrary)

    Keys.makeScalaLibraryProvided(dependencies) shouldBe
      scalaLibraries.map(_ % Provided) ++ Seq(otherLibrary, testLibrary)
  }

  test("excludes Scala libraries from explicit packaging mappings") {
    val mappings = (scalaLibraries :+ otherLibrary).map { library =>
      library -> Some(s"lib/${library.name}.jar")
    }

    Keys.filterScalaLibrary(mappings) shouldBe Seq(mappings.last)
  }

  test("filters Scala libraries from attributed classpaths and preserves unmanaged entries") {
    val classpath = (scalaLibraries :+ otherLibrary).map { library =>
      Attributed.blank(file(s"lib/${library.name}.jar"))
        .put(sbt.Keys.moduleID.key, library)
    }
    val unmanaged = Attributed.blank(file("lib/unmanaged.jar"))

    Keys.filterScalaLibraryCp(classpath :+ unmanaged) shouldBe Seq(classpath.last, unmanaged)
  }
}
