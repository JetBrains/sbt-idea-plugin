package org.jetbrains.sbtidea

import org.jetbrains.sbtidea.IntellijTestRuntimeDirectories.RuntimeDirectories

import java.io.File
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{FileAlreadyExistsException, Files, Path, StandardOpenOption}
import java.security.MessageDigest

/** Pure path resolver: constructing or inspecting directories never creates them or reads an SDK. */
private[sbtidea] final class CheckoutRuntimeDirectories private (
  ideaHome: Path,
  val buildRoot: File,
  val checkoutHash: String,
) {
  private val runtimeRoot = ideaHome.resolve("runtime")

  def tests(buildId: String): RuntimeDirectories = forPurpose(buildId, "tests")
  def dev(buildId: String): RuntimeDirectories = forPurpose(buildId, "dev")
  def devClient(buildId: String): RuntimeDirectories =
    directoriesIn(runtimeDirectory(buildId, "dev").resolve("client"))
  def buildIndex(buildId: String): RuntimeDirectories = forPurpose(buildId, "build-index")

  def sharedDev: RuntimeDirectories = shared("")
  def sharedDevClient: RuntimeDirectories = shared("-client")

  private def shared(suffix: String): RuntimeDirectories = {
    val system = runtimeRoot.resolve(s"system$suffix-shared")
    RuntimeDirectories(system, runtimeRoot.resolve(s"config$suffix-shared"), system.resolve("log"))
  }

  private def forPurpose(buildId: String, purpose: String): RuntimeDirectories =
    directoriesIn(runtimeDirectory(buildId, purpose))

  private def runtimeDirectory(buildId: String, purpose: String): Path = {
    CheckoutRuntimeDirectories.validateBuildId(buildId)
    runtimeRoot.resolve(buildId).resolve(checkoutHash).resolve(purpose)
  }

  private def directoriesIn(root: Path): RuntimeDirectories =
    RuntimeDirectories(root.resolve("system"), root.resolve("config"), root.resolve("system/log"))

  /** Selected default directories only; explicit overrides have no managed-directory provenance. */
  def prepareTests(buildId: String, system: Option[File], config: Option[File]): Unit = {
    val directories = tests(buildId)
    system.foreach(file => createReadme(file.toPath, directories.system, buildId, "system (including test logs)"))
    config.foreach(file => createReadme(file.toPath, directories.config, buildId, "config"))
  }

  private def createReadme(selected: Path, managed: Path, buildId: String, purpose: String): Unit = {
    if (selected.toAbsolutePath.normalize() == managed.toAbsolutePath.normalize()) {
      Files.createDirectories(selected)
      val content =
        s"""IntelliJ Platform test $purpose directory managed by sbt-idea-plugin.
           |Source checkout: $buildRoot
           |Target SDK runtime build ID: $buildId
           |
           |Tests for this checkout and build share these directories, including sbt and IDE tests.
           |Concurrent test JVMs in the same checkout are unsupported by default.
           |""".stripMargin
      try Files.write(selected.resolve("README.txt"), content.getBytes(UTF_8), StandardOpenOption.CREATE_NEW)
      catch {
        // Preserve existing files, including unrelated README files and symlinks.
        case _: FileAlreadyExistsException => ()
      }
    }
  }
}

private[sbtidea] object CheckoutRuntimeDirectories {
  private val BuildId = "[A-Za-z0-9][A-Za-z0-9._-]*".r

  def validateBuildId(buildId: String): String = {
    require(buildId != null && BuildId.pattern.matcher(buildId).matches(),
      "IntelliJ runtime build ID must be a single path component starting with a letter or digit " +
        "and containing only letters, digits, '.', '_', or '-': " + buildId)
    buildId
  }

  def sharedDevDefault: Boolean = sys.props.get("sbtidea.dev.sharedDirectories") match {
    case None => false
    case Some(value) => value.trim.toLowerCase(java.util.Locale.ROOT) match {
      case "true" => true
      case "false" => false
      case _ => throw new IllegalArgumentException("sbtidea.dev.sharedDirectories must be true or false: " + value)
    }
  }
  /**
   * Ten hex characters (40 bits) keep directory names short with a small collision probability.
   * Assuming uniformly distributed SHA-256 prefixes, the birthday bound for n distinct checkouts
   * sharing one plugin home is n * (n - 1) / (2 * 2^40). For 100 checkouts this is about 4.5e-9,
   * or one chance in 222 million that any two checkout identities collide.
   */
  private val CheckoutHashHexLength = 10

  def apply(ideaHome: Path, buildRoot: File): CheckoutRuntimeDirectories = {
    val canonicalRoot = buildRoot.getCanonicalFile
    val digest = MessageDigest.getInstance("SHA-256").digest(canonicalRoot.getPath.getBytes(UTF_8))
    val checkoutHash = digest.map(byte => f"${byte & 0xff}%02x").mkString.take(CheckoutHashHexLength)
    new CheckoutRuntimeDirectories(ideaHome, canonicalRoot, checkoutHash)
  }
}
