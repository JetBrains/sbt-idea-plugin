package org.jetbrains.sbtidea.runtimeCleanup

import org.jetbrains.sbtidea.{CheckoutRuntimeDirectories, PluginLogger}

import java.io.StringReader
import java.nio.channels.{FileChannel, OverlappingFileLockException}
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.StandardOpenOption.{CREATE, WRITE}
import java.nio.file.attribute.FileTime
import java.nio.file.*
import java.time.{Duration, Instant}
import java.util.Properties
import scala.collection.JavaConverters.*
import scala.util.Try
import scala.util.control.NonFatal

private[sbtidea] object RuntimeUsageRegistry {
  final case class Usage(record: RuntimeCleanup.Record, lastUsed: Instant)
}

/** Versioned ownership metadata, validation and stable filesystem locks. */
private[sbtidea] final class RuntimeUsageRegistry(pluginHome: Path, log: PluginLogger, now: () => Instant) {
  import RuntimeCleanup.*
  import RuntimeUsageRegistry.Usage
  private val sourceHome = pluginHome.toAbsolutePath.normalize()
  val home = pluginHome.toFile.getCanonicalFile.toPath
  val runtime = home.resolve("runtime")
  private val control = home.resolve(".sbt-idea-plugin")
  val entries: Path = control.resolve("runtime-usage-v1")
  private val checkpoint = control.resolve("runtime-cleanup-v1.properties")

  def marker(checkout: Path, buildId: String): Path = {
    CheckoutRuntimeDirectories.validateBuildId(buildId)
    val hash = CheckoutRuntimeDirectories(home, checkout.toFile).checkoutHash
    entries.resolve(s"$buildId--$hash.properties")
  }

  def refresh(checkout: Path, buildId: String, paths: Map[String, Path], policy: Policy, replaceRoles: Set[String] = Roles): Unit = safely {
    require(paths.keySet.subsetOf(replaceRoles) && replaceRoles.subsetOf(Roles), "Unknown runtime directory role")
    val file = marker(checkout, buildId)
    withLock(lockFor(file), blocking = true) {
      val existing = read(file)
      // Never overwrite an unknown format, unrelated file, or malformed ownership record.
      if (!Files.exists(file, NOFOLLOW_LINKS) || existing.nonEmpty) {
        val retained = existing.toSeq.flatMap(_.paths).toMap -- replaceRoles
        val record = Record(checkout.toFile.getCanonicalFile.toPath, buildId, policy.enabled,
          Duration.ofNanos(policy.retention.toNanos), retained ++ paths.map { case (role, path) => role -> normalizeManaged(path) })
        require(record.paths.forall { case (role, path) => safeRuntimePath(role, path) }, "Unsafe managed runtime path")
        if (record.paths.nonEmpty) {
          if (!existing.contains(record)) write(file, properties(record))
          Files.setLastModifiedTime(file, FileTime.from(now()))
        } else Files.deleteIfExists(file)
      }
    }
  }

  /** Preserve whole overlapping directories, even when an override selects only a nested child. */
  def withdraw(paths: Set[Path]): Boolean = try {
    val excluded = paths.map(_.toFile.getCanonicalFile.toPath)
    if (excluded.isEmpty) true
    else listRecords().fold(reason => throw new java.io.IOException(reason), identity).map { file =>
      var completed = false
      withLock(lockFor(file), blocking = true) {
        read(file).foreach { record =>
          val kept = record.paths.filterNot { case (_, path) => excluded.exists(overlaps(_, path)) }
          if (kept != record.paths) {
            val modified = Files.getLastModifiedTime(file, NOFOLLOW_LINKS)
            if (kept.isEmpty) Files.delete(file)
            else { write(file, properties(record.copy(paths = kept))); Files.setLastModifiedTime(file, modified) }
          }
        }
        completed = true
      }
      completed
    }.forall(identity)
  } catch {
    case NonFatal(ex) =>
      log.warn(s"IntelliJ runtime ownership withdrawal skipped: ${ex.getMessage}")
      false
  }

  def records(): Seq[Path] = listRecords().getOrElse(Seq.empty)

  /** Read-only discovery retains failures for diagnostics; cleanup skips unavailable registries. */
  def listRecords(): Either[String, Seq[Path]] = attempt {
    require(safeControl(entries), "registry path contains a symbolic link")
    if (Files.notExists(entries, NOFOLLOW_LINKS)) Seq.empty
    else {
      require(Files.isDirectory(entries, NOFOLLOW_LINKS), "registry is not an accessible directory")
      val files = Files.list(entries)
      try files.iterator().asScala.filter(_.getFileName.toString.endsWith(".properties")).toVector.sortBy(_.getFileName.toString)
      finally files.close()
    }
  }

  def read(file: Path): Option[Record] = readUsage(file).toOption.map(_.record)

  def readUsage(file: Path): Either[String, Usage] = load(file).flatMap { p => attempt {
    def field(name: String): String = Option(p.getProperty(name)).getOrElse(
      throw new IllegalArgumentException(s"missing $name"))
    require(field("formatVersion") == "1", "unsupported formatVersion")
    require(field("owner") == "sbt-idea-plugin", "unrecognized owner")
    val checkout = Paths.get(field("checkoutRoot"))
    require(checkout.isAbsolute, "checkoutRoot must be absolute")
    val buildId = CheckoutRuntimeDirectories.validateBuildId(field("buildId"))
    require(file == marker(checkout, buildId), "record filename does not match checkout/build identity")
    val enabled = p.getProperty("automaticCleanup") match {
      case "true" => true
      case "false" => false
      case _ => throw new IllegalArgumentException("Invalid automaticCleanup")
    }
    val nanos = Try((BigDecimal(field("retentionSeconds")) * BigDecimal(1000000000L)).bigDecimal.longValueExact())
      .getOrElse(throw new IllegalArgumentException("invalid retentionSeconds"))
    require(nanos > 0, "retentionSeconds must be positive")
    val known = Roles ++ Set("formatVersion", "owner", "checkoutRoot", "buildId", "automaticCleanup", "retentionSeconds")
    val unknown = p.stringPropertyNames().asScala.filterNot(known).toSeq.sorted
    require(unknown.isEmpty, s"unknown fields: ${unknown.mkString(", ")}")
    val paths = Roles.toSeq.sorted.flatMap { role => Option(p.getProperty(role)).map { relative =>
      val path = Paths.get(relative)
      require(!path.isAbsolute && !path.iterator().asScala.exists(_.toString == ".."), s"$role must be a relative path without '..'")
      val absolute = home.resolve(path).normalize()
      require(safeRuntimePath(role, absolute), s"unsafe $role path (outside managed runtime directories, symbolic link, or not a directory)")
      role -> absolute
    }}.toMap
    require(paths.nonEmpty, "no managed paths registered")
    require(paths.values.toSet.size == paths.size, "multiple roles use the same path")
    Usage(
      Record(checkout.toFile.getCanonicalFile.toPath, buildId, enabled, Duration.ofNanos(nanos), paths),
      Files.getLastModifiedTime(file, NOFOLLOW_LINKS).toInstant)
  }}

  private def properties(record: Record): Properties = {
    val p = new Properties
    p.setProperty("formatVersion", "1")
    p.setProperty("owner", "sbt-idea-plugin")
    p.setProperty("checkoutRoot", record.checkout.toString)
    p.setProperty("buildId", record.buildId)
    p.setProperty("automaticCleanup", record.enabled.toString)
    p.setProperty("retentionSeconds", (BigDecimal(record.retention.toNanos) / BigDecimal(1000000000L)).bigDecimal.stripTrailingZeros().toPlainString)
    record.paths.foreach { case (role, path) => p.setProperty(role, home.relativize(path).toString) }
    p
  }

  private def load(file: Path): Either[String, Properties] = attempt {
    require(safeControl(file), "metadata path contains a symbolic link or is outside the registry")
    require(Files.isRegularFile(file, NOFOLLOW_LINKS), "metadata is missing or is not an accessible regular file")
    require(Files.size(file) <= 65536, "metadata exceeds the 64 KiB limit")
    val p = new Properties
    p.load(new StringReader(new String(Files.readAllBytes(file), UTF_8)))
    p
  }

  private def write(file: Path, properties: Properties): Unit = {
    require(safeControl(file), "Unsafe runtime registry path")
    Files.createDirectories(file.getParent)
    val temporary = Files.createTempFile(file.getParent, ".runtime-", ".tmp")
    try {
      val writer = Files.newBufferedWriter(temporary, UTF_8)
      try properties.store(writer, "sbt-idea-plugin runtime cleanup metadata") finally writer.close()
      try Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
      catch { case _: AtomicMoveNotSupportedException => Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING) }
    } finally Files.deleteIfExists(temporary)
  }

  private def safeControl(path: Path): Boolean = path.startsWith(control) && noSymlinks(path)

  // Canonicalize the configured home (e.g. macOS /var -> /private/var), without following
  // symlinks inside a runtime: those must remain ineligible rather than redirecting deletion.
  private def normalizeManaged(path: Path): Path = {
    val absolute = path.toAbsolutePath.normalize()
    if (absolute.startsWith(sourceHome)) home.resolve(sourceHome.relativize(absolute)) else absolute
  }

  private def safeRuntimePath(role: String, path: Path): Boolean = {
    val parts = if (path.startsWith(runtime)) runtime.relativize(path).iterator().asScala.map(_.toString).toSeq else Seq.empty
    Roles(role) && parts.size >= 3 && parts.last == role.split('.').last &&
      !parts.exists(part => part == "dev" || part == "client" || part.endsWith("-shared")) && noSymlinks(path) &&
      (!Files.exists(path, NOFOLLOW_LINKS) || Files.isDirectory(path, NOFOLLOW_LINKS))
  }

  def noSymlinks(path: Path): Boolean = {
    path.startsWith(home) && path == path.toAbsolutePath.normalize() && {
      var current = home
      home.relativize(path).iterator().asScala.forall { part => current = current.resolve(part); !Files.isSymbolicLink(current) }
    }
  }

  private def lockFor(file: Path): Path = file.resolveSibling(file.getFileName.toString.stripSuffix(".properties") + ".lock")

  private def withLock(file: Path, blocking: Boolean)(action: => Unit): Unit = {
    require(safeControl(file), "Unsafe runtime registry lock")
    Files.createDirectories(file.getParent)
    val channel = FileChannel.open(file, CREATE, WRITE)
    try {
      val lock = try { if (blocking) channel.lock() else channel.tryLock() }
      catch { case _: OverlappingFileLockException => null }
      if (lock != null) try action finally lock.release()
    } finally channel.close()
  }

  def withCleanupLock(action: => Unit): Unit =
    withLock(control.resolve("runtime-cleanup.lock"), blocking = false)(action)

  def withRecordLock(file: Path)(action: => Unit): Unit =
    withLock(lockFor(file), blocking = false)(action)

  def lastAttempt: Option[Instant] = inspectLastAttempt.toOption.flatten

  def inspectLastAttempt: Either[String, Option[Instant]] = {
    if (safeControl(checkpoint) && Files.notExists(checkpoint, NOFOLLOW_LINKS)) Right(None)
    else load(checkpoint).flatMap { p => attempt { Some(Instant.parse(p.getProperty("lastAttempt"))) } }
  }

  def recordAttempt(time: Instant): Unit = {
    val state = new Properties
    state.setProperty("formatVersion", "1")
    state.setProperty("lastAttempt", time.toString)
    write(checkpoint, state)
  }

  private def safely(action: => Unit): Unit = try action
    catch { case NonFatal(ex) => log.warn(s"IntelliJ runtime maintenance skipped: ${ex.getMessage}") }

  private def attempt[A](action: => A): Either[String, A] = try Right(action)
    catch { case NonFatal(ex) => Left(Option(ex.getMessage).getOrElse(ex.getClass.getSimpleName)) }
}
