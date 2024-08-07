package org.jetbrains.sbtidea.tasks

import coursier.Dependency
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import java.nio.file.{Path, Paths}
import scala.util.chaining.scalaUtilChainingOps

class ComputeJupiterRuntimeDependenciesTest extends AnyFunSuite with Matchers {

  import IdeaConfigBuilder.*

  private val basePath: Path = Paths.get("/", "tmp", ".cache")

  private def depString(deps: Seq[Dependency]): Seq[String] = deps.map { dep =>
    val (module, version) = dep.moduleVersion
    s"${module.orgName}:$version"
  }

  test("test classpath contains only jupiter-api") {
    // transitive dependency of junit-jupiter-engine
    val jupiterApi = basePath.resolve(Paths.get("org", "junit", "jupiter", "junit-jupiter-api", "5.9.3", "junit-jupiter-api-5.9.3.jar"))

    val testClasspath = Seq(jupiterApi)
    val dependencies = computeJupiterRuntimeDependencies(testClasspath) pipe depString

    dependencies should have(size(3))
    val Seq(first, second, third) = dependencies
    first should be("org.junit.jupiter:junit-jupiter-engine:5.9.3")
    second should be("org.junit.vintage:junit-vintage-engine:5.9.3")
    third should be("org.junit.platform:junit-platform-launcher:1.9.3")
  }

  test("classpath contains no relevant jars") {
    val dependencies = computeJupiterRuntimeDependencies(Seq.empty) pipe depString

    dependencies should have(size(3))
    val Seq(first, second, third) = dependencies
    first should be(s"org.junit.jupiter:junit-jupiter-engine:$fallbackJupiterVersion")
    second should be(s"org.junit.vintage:junit-vintage-engine:$fallbackJupiterVersion")
    third should be(s"org.junit.platform:junit-platform-launcher:$fallbackPlatformVersion")
  }

  test("classpath contains all relevant jars") {
    // transitive dependency of junit-jupiter-engine
    val jupiterApi = basePath.resolve(Paths.get("org", "junit", "jupiter", "junit-jupiter-api", "5.9.3", "junit-jupiter-api-5.9.3.jar"))
    val jupiterEngine = basePath.resolve(Paths.get("org", "junit", "jupiter", "junit-jupiter-engine", "5.9.3", "junit-jupiter-engine-5.9.3.jar"))
    val vintageEngine = basePath.resolve(Paths.get("org", "junit", "vintage", "junit-vintage-engine", "5.9.3", "junit-vintage-engine-5.9.3.jar"))
    val platformLauncher = basePath.resolve(Paths.get("org", "junit", "platform", "junit-platform-launcher", "1.9.3", "junit-platform-launcher-1.9.3.jar"))
    val testClasspath = Seq(jupiterApi, jupiterEngine, vintageEngine, platformLauncher)

    val dependencies = computeJupiterRuntimeDependencies(testClasspath) pipe depString

    dependencies should be(empty)
  }

  test("classpath contains jupiter engine only") {
    // transitive dependency of junit-jupiter-engine
    val jupiterApi = basePath.resolve(Paths.get("org", "junit", "jupiter", "junit-jupiter-api", "5.9.3", "junit-jupiter-api-5.9.3.jar"))
    val jupiterEngine = basePath.resolve(Paths.get("org", "junit", "jupiter", "junit-jupiter-engine", "5.9.3", "junit-jupiter-engine-5.9.3.jar"))
    val testClasspath = Seq(jupiterApi, jupiterEngine)

    val dependencies = computeJupiterRuntimeDependencies(testClasspath) pipe depString

    dependencies should have(size(2))
    val Seq(first, second) = dependencies
    first should be("org.junit.vintage:junit-vintage-engine:5.9.3")
    second should be("org.junit.platform:junit-platform-launcher:1.9.3")
  }

  test("classpath contains vintage engine only") {
    val vintageEngine = basePath.resolve(Paths.get("org", "junit", "vintage", "junit-vintage-engine", "5.9.3", "junit-vintage-engine-5.9.3.jar"))
    val testClasspath = Seq(vintageEngine)

    val dependencies = computeJupiterRuntimeDependencies(testClasspath) pipe depString

    dependencies should have(size(2))
    val Seq(first, second) = dependencies
    first should be("org.junit.jupiter:junit-jupiter-engine:5.9.3")
    second should be("org.junit.platform:junit-platform-launcher:1.9.3")
  }

  test("classpath contains jupiter engine and vintage engine") {
    // transitive dependency of junit-jupiter-engine
    val jupiterApi = basePath.resolve(Paths.get("org", "junit", "jupiter", "junit-jupiter-api", "5.9.3", "junit-jupiter-api-5.9.3.jar"))
    val jupiterEngine = basePath.resolve(Paths.get("org", "junit", "jupiter", "junit-jupiter-engine", "5.9.3", "junit-jupiter-engine-5.9.3.jar"))
    val vintageEngine = basePath.resolve(Paths.get("org", "junit", "vintage", "junit-vintage-engine", "5.9.3", "junit-vintage-engine-5.9.3.jar"))
    val testClasspath = Seq(jupiterApi, jupiterEngine, vintageEngine)

    val dependencies = computeJupiterRuntimeDependencies(testClasspath) pipe depString

    dependencies should have(size(1))
    dependencies.head should be("org.junit.platform:junit-platform-launcher:1.9.3")
  }

  test("classpath contains only platform launcher") {
    // platform launcher version 1.9.3
    val platformLauncher = basePath.resolve(Paths.get("org", "junit", "platform", "junit-platform-launcher", "1.9.3", "junit-platform-launcher-1.9.3.jar"))
    val testClasspath = Seq(platformLauncher)

    val dependencies = computeJupiterRuntimeDependencies(testClasspath) pipe depString

    dependencies should have(size(2))
    val Seq(first, second) = dependencies
    // We do not do "reverse" discovery of the jupiter version (from platform to jupiter, example 1.9.3 -> 5.9.3).
    // Instead we fall back to the fallback version.
    first should be(s"org.junit.jupiter:junit-jupiter-engine:$fallbackJupiterVersion")
    second should be(s"org.junit.vintage:junit-vintage-engine:$fallbackJupiterVersion")
  }

  test("classpath contains fake jars with the same artifact name") {
    val fakeJupiterApi = basePath.resolve(Paths.get("fake", "junit", "jupiter", "junit-jupiter-api", "5.9.3", "junit-jupiter-api-5.9.3.jar"))
    val fakeJupiterEngine = basePath.resolve(Paths.get("fake", "junit", "jupiter", "junit-jupiter-engine", "5.9.3", "junit-jupiter-engine-5.9.3.jar"))
    val fakeVintageEngine = basePath.resolve(Paths.get("fake", "junit", "vintage", "junit-vintage-engine", "5.9.3", "junit-vintage-engine-5.9.3.jar"))
    val fakePlatformLauncher = basePath.resolve(Paths.get("fake", "junit", "platform", "junit-platform-launcher", "1.9.3", "junit-platform-launcher-1.9.3.jar"))
    val testClasspath = Seq(fakeJupiterApi, fakeJupiterEngine, fakeVintageEngine, fakePlatformLauncher)

    val dependencies = computeJupiterRuntimeDependencies(testClasspath) pipe depString

    dependencies should have(size(3))
    val Seq(first, second, third) = dependencies
    first should be(s"org.junit.jupiter:junit-jupiter-engine:$fallbackJupiterVersion")
    second should be(s"org.junit.vintage:junit-vintage-engine:$fallbackJupiterVersion")
    third should be(s"org.junit.platform:junit-platform-launcher:$fallbackPlatformVersion")
  }
}
