package org.jetbrains.sbtidea

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files

class CheckoutRuntimeDirectoriesTest extends AnyFunSuite with Matchers with TmpDirUtils {
  private val buildId = "IU-263.12345.67"

  test("identity is the first 10 hex characters of SHA-256 of the canonical UTF-8 build root") {
    // echo -n /checkout/with-spaces | shasum -a 256
    val root = new java.io.File("/checkout/with-spaces")
    CheckoutRuntimeDirectories(newTmpDir, root).checkoutHash shouldBe "a0f9d9b86d"
  }

  test("the same canonical root, including a symlink alias, has a stable identity") {
    val parent = newTmpDir
    val root = Files.createDirectory(parent.resolve("checkout with spaces"))
    val alias = Files.createSymbolicLink(parent.resolve("alias"), root)
    val home = parent.resolve("plugin home")
    val original = CheckoutRuntimeDirectories(home, root.toFile)
    CheckoutRuntimeDirectories(home, root.resolve(".").toFile).tests(buildId) shouldBe original.tests(buildId)
    CheckoutRuntimeDirectories(home, alias.toFile).checkoutHash shouldBe original.checkoutHash
    CheckoutRuntimeDirectories(home, Files.createDirectory(parent.resolve("other")).toFile).checkoutHash should not be original.checkoutHash
    Files.exists(home) shouldBe false // evaluating defaults has no filesystem side effects
  }

  test("preparation creates descriptive markers once and preserves existing README files") {
    val parent = newTmpDir
    val managed = CheckoutRuntimeDirectories(parent.resolve("plugin home"), parent.toFile)
    val dirs = managed.tests(buildId)
    managed.prepareTests(buildId, Some(dirs.system.toFile), Some(dirs.config.toFile))
    val readme = dirs.system.resolve("README.txt")
    val original = Files.readAllBytes(readme)
    val modified = Files.getLastModifiedTime(readme)
    new String(original, UTF_8) should include(parent.toFile.getCanonicalPath)
    new String(original, UTF_8) should include("system")
    new String(Files.readAllBytes(dirs.config.resolve("README.txt")), UTF_8) should include("config")
    managed.prepareTests(buildId, Some(dirs.system.toFile), Some(dirs.config.toFile))
    Files.readAllBytes(readme) shouldBe original
    Files.getLastModifiedTime(readme) shouldBe modified
    Files.write(readme, "unrelated content".getBytes(UTF_8))
    managed.prepareTests(buildId, Some(dirs.system.toFile), Some(dirs.config.toFile))
    new String(Files.readAllBytes(readme), UTF_8) shouldBe "unrelated content"
  }

  test("preparation leaves explicit overrides alone, independently for system and config") {
    val parent = newTmpDir
    val managed = CheckoutRuntimeDirectories(parent.resolve("home"), parent.toFile)
    val overridden = parent.resolve("custom system")
    managed.prepareTests(buildId, None, Some(managed.tests(buildId).config.toFile))
    Files.exists(overridden) shouldBe false
    Files.exists(managed.tests(buildId).config.resolve("README.txt")) shouldBe true
    val customConfig = Files.createDirectory(parent.resolve("custom config"))
    managed.prepareTests(buildId, None, None)
    Files.exists(customConfig.resolve("README.txt")) shouldBe false
  }

  test("isolated directories group full build, checkout and purpose without creating them") {
    val home = newTmpDir.resolve("plugin home")
    val resolver = CheckoutRuntimeDirectories(home, new java.io.File("/checkout/with-spaces"))
    val root = home.resolve(s"runtime/$buildId/a0f9d9b86d")
    Seq(
      resolver.tests(buildId) -> "tests",
      resolver.dev(buildId) -> "dev",
      resolver.devClient(buildId) -> "dev/client",
      resolver.buildIndex(buildId) -> "build-index",
    ).foreach { case (dirs, purpose) =>
      dirs.system shouldBe root.resolve(s"$purpose/system")
      dirs.config shouldBe root.resolve(s"$purpose/config")
      dirs.log shouldBe root.resolve(s"$purpose/system/log")
    }
    val runtimes = Seq(resolver.tests(buildId), resolver.dev(buildId), resolver.devClient(buildId),
      resolver.buildIndex(buildId), resolver.tests("IU-263.12345.68"), resolver.tests("IU-custom-build-2"))
    runtimes.map(_.system).distinct.size shouldBe runtimes.size
    runtimes.foreach(dirs => dirs.log shouldBe dirs.system.resolve("log"))
    Files.exists(home) shouldBe false
  }

  test("shared development paths ignore checkout identity but keep frontend separate") {
    val home = newTmpDir.resolve("home")
    val first = CheckoutRuntimeDirectories(home, new java.io.File("/first"))
    val second = CheckoutRuntimeDirectories(home, new java.io.File("/second"))
    first.sharedDev shouldBe second.sharedDev
    first.sharedDevClient shouldBe second.sharedDevClient
    first.sharedDev.system shouldBe home.resolve("runtime/system-shared")
    first.sharedDev.config shouldBe home.resolve("runtime/config-shared")
    first.sharedDevClient.system shouldBe home.resolve("runtime/system-client-shared")
    first.sharedDevClient.config shouldBe home.resolve("runtime/config-client-shared")
    Files.exists(home) shouldBe false
  }

  test("unsafe build identities fail before paths are selected") {
    val resolver = CheckoutRuntimeDirectories(newTmpDir, new java.io.File("/checkout"))
    Seq(null, "", ".", "..", "../build", "/absolute", "with spaces", "a/b", "a\\b").foreach { id =>
      an[IllegalArgumentException] should be thrownBy resolver.tests(id)
    }
  }
}
