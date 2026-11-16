package org.jetbrains.sbtidea.runtimeCleanup

import org.jetbrains.sbtidea.{CheckoutRuntimeDirectories, PluginLogger}
import org.jetbrains.sbtidea.download.cachesCleanup.CleanupUtils

import java.io.StringReader
import java.nio.channels.{FileChannel, OverlappingFileLockException}
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.StandardOpenOption.{CREATE, WRITE}
import java.nio.file.attribute.{BasicFileAttributes, FileTime}
import java.nio.file.*
import java.time.{Duration, Instant}
import java.util.Properties
import scala.collection.JavaConverters.*
import scala.concurrent.duration.FiniteDuration
import scala.util.Try
import scala.util.control.NonFatal

private[sbtidea] object RuntimeCleanup {
  val Roles: Set[String] = Set("test.system", "test.config", "index.system", "index.config")
  val TestRoles: Set[String] = Set("test.system", "test.config")
  val IndexRoles: Set[String] = Roles -- TestRoles
  final case class Policy(enabled: Boolean, retention: FiniteDuration, interval: FiniteDuration) {
    require(retention.length > 0, "intellijRuntimeCleanupRetention must be positive")
    require(interval.length > 0, "intellijRuntimeCleanupInterval must be positive")
  }
  final case class Record(checkout: Path, buildId: String, enabled: Boolean, retention: Duration, paths: Map[String, Path])
  final case class Report(directories: Int = 0, bytes: Long = 0L, failures: Int = 0)
}

/** Registry discovery is shallow. Only expired, owned directories are traversed, during deletion. */
private[sbtidea] final class RuntimeCleanup(
  pluginHome: Path,
  val policy: RuntimeCleanup.Policy,
  log: PluginLogger,
  now: () => Instant = () => Instant.now(),
  processes: () => Option[Set[Path]] = () => RuntimeProcesses.activePaths(),
  deleteFile: Path => Unit = path => Files.delete(path),
) {
  import RuntimeCleanup.*
  private val sourceHome = pluginHome.toAbsolutePath.normalize()
  private val home = pluginHome.toFile.getCanonicalFile.toPath
  private val runtime = home.resolve("runtime")
  private val control = home.resolve(".sbt-idea-plugin")
  val entries: Path = control.resolve("runtime-usage-v1")
  private val checkpoint = control.resolve("runtime-cleanup-v1.properties")
  private var nextCheck = Instant.MIN

  def marker(checkout: Path, buildId: String): Path = {
    CheckoutRuntimeDirectories.validateBuildId(buildId)
    val hash = CheckoutRuntimeDirectories(home, checkout.toFile).checkoutHash
    entries.resolve(s"$buildId--$hash.properties")
  }

  def refresh(checkout: Path, buildId: String, paths: Map[String, Path], replaceRoles: Set[String] = Roles): Unit = safely {
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

  /** Overrides can coincide with an old default. Withdraw that ownership even without SDK metadata. */
  def withdraw(paths: Set[Path]): Unit = safely {
    val excluded = paths.map(normalizeManaged)
    if (excluded.nonEmpty) records().foreach { file =>
      withLock(lockFor(file), blocking = true) {
        read(file).foreach { record =>
          val kept = record.paths.filterNot { case (_, path) => excluded(path) }
          if (kept != record.paths) {
            val modified = Files.getLastModifiedTime(file, NOFOLLOW_LINKS)
            if (kept.isEmpty) Files.delete(file)
            else { write(file, properties(record.copy(paths = kept))); Files.setLastModifiedTime(file, modified) }
          }
        }
      }
    }
  }

  def cleanupIfDue(): Report = synchronized {
    var report = Report()
    safely {
      val time = now()
      if (policy.enabled && !time.isBefore(nextCheck)) {
        // A cheap local deadline also prevents contending sbt processes retrying on every task.
        nextCheck = time.plusNanos(policy.interval.toNanos)
        withLock(control.resolve("runtime-cleanup.lock"), blocking = false) {
          val last = load(checkpoint).flatMap(p => Try(Instant.parse(p.getProperty("lastAttempt"))).toOption)
          if (last.exists(t => !t.isAfter(time) && Duration.between(t, time).compareTo(Duration.ofNanos(policy.interval.toNanos)) < 0)) {
            nextCheck = last.get.plusNanos(policy.interval.toNanos)
          } else {
            val state = new Properties
            state.setProperty("formatVersion", "1")
            state.setProperty("lastAttempt", time.toString)
            write(checkpoint, state)
            // Lazily inspect processes once, only if a candidate survives metadata and lock checks.
            lazy val active = processes().map(_.map(_.toFile.getCanonicalFile.toPath))
            records().foreach { file =>
              if (read(file).exists(record => expired(file, record, time))) {
                withLock(lockFor(file), blocking = false) {
                  read(file).filter(record => expired(file, record, time)).foreach { record =>
                    active match {
                      case Some(paths) if !record.paths.values.exists(path => paths.exists(activePath =>
                        activePath == path.toFile.getCanonicalFile.toPath || activePath.startsWith(path) || path.startsWith(activePath))) =>
                        record.paths.values.toSet.foreach { path: Path =>
                          if (Files.exists(path, NOFOLLOW_LINKS)) {
                            val removed = remove(path)
                            report = Report(report.directories + removed.directories, report.bytes + removed.bytes,
                              report.failures + removed.failures)
                            if (!Files.exists(path, NOFOLLOW_LINKS)) prune(path.getParent)
                          }
                        }
                        if (record.paths.values.forall(path => !Files.exists(path, NOFOLLOW_LINKS))) Files.deleteIfExists(file)
                      case _ => log.debug(s"Deferred IntelliJ runtime cleanup: active or uninspectable Java processes ($file)")
                    }
                  }
                }
              }
            }
            if (report.directories > 0 || report.bytes > 0)
              log.info(s"Removed ${report.directories} expired IntelliJ runtime directories (${CleanupUtils.formatSize(report.bytes)} of file data)")
            if (report.failures > 0) log.warn(s"IntelliJ runtime cleanup had ${report.failures} deletion failures; remaining data will be retried on a later pass")
          }
        }
      }
    }
    report
  }

  private def expired(file: Path, record: Record, time: Instant): Boolean = {
    val modified = Files.getLastModifiedTime(file, NOFOLLOW_LINKS).toInstant
    record.enabled && !modified.isAfter(time) && Duration.between(modified, time).compareTo(record.retention) > 0
  }

  private def records(): Seq[Path] = {
    if (!safeControl(entries) || !Files.isDirectory(entries, NOFOLLOW_LINKS)) Seq.empty
    else {
      val files = Files.list(entries)
      try files.iterator().asScala.filter(_.getFileName.toString.endsWith(".properties")).toVector
      finally files.close()
    }
  }

  private def read(file: Path): Option[Record] = load(file).flatMap { p => Try {
    require(p.getProperty("formatVersion") == "1" && p.getProperty("owner") == "sbt-idea-plugin")
    val checkout = Paths.get(p.getProperty("checkoutRoot"))
    require(checkout.isAbsolute)
    val buildId = CheckoutRuntimeDirectories.validateBuildId(p.getProperty("buildId"))
    require(file == marker(checkout, buildId))
    val enabled = p.getProperty("automaticCleanup") match {
      case "true" => true
      case "false" => false
      case _ => throw new IllegalArgumentException("Invalid automaticCleanup")
    }
    val nanos = (BigDecimal(p.getProperty("retentionSeconds")) * BigDecimal(1000000000L)).bigDecimal.longValueExact()
    require(nanos > 0)
    val known = Roles ++ Set("formatVersion", "owner", "checkoutRoot", "buildId", "automaticCleanup", "retentionSeconds")
    require(p.stringPropertyNames().asScala.forall(known))
    val paths = Roles.flatMap { role => Option(p.getProperty(role)).map { relative =>
      val path = Paths.get(relative)
      require(!path.isAbsolute && !path.iterator().asScala.exists(_.toString == ".."))
      val absolute = home.resolve(path).normalize()
      require(safeRuntimePath(role, absolute))
      role -> absolute
    }}.toMap
    require(paths.nonEmpty && paths.values.toSet.size == paths.size)
    Record(checkout, buildId, enabled, Duration.ofNanos(nanos), paths)
  }.toOption }

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

  private def load(file: Path): Option[Properties] = Try {
    require(safeControl(file) && Files.isRegularFile(file, NOFOLLOW_LINKS) && Files.size(file) <= 65536)
    val p = new Properties
    p.load(new StringReader(new String(Files.readAllBytes(file), UTF_8)))
    p
  }.toOption

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

  private def noSymlinks(path: Path): Boolean = {
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

  private def remove(root: Path): Report = {
    var bytes = 0L
    var failures = 0
    def failed(path: Path, ex: Throwable): Unit = { failures += 1; log.warn(s"Could not remove IntelliJ runtime data $path: ${ex.getMessage}") }
    def delete(path: Path, size: Long): Unit = try { deleteFile(path); bytes += size }
      catch { case NonFatal(ex) => failed(path, ex) }
    Files.walkFileTree(root, new SimpleFileVisitor[Path] {
      override def visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult = {
        delete(file, if (attrs.isRegularFile) attrs.size() else 0L)
        FileVisitResult.CONTINUE
      }
      override def visitFileFailed(file: Path, ex: java.io.IOException): FileVisitResult = {
        failed(file, ex); FileVisitResult.CONTINUE
      }
      override def postVisitDirectory(dir: Path, ex: java.io.IOException): FileVisitResult = {
        if (ex != null) failed(dir, ex) else delete(dir, 0L)
        FileVisitResult.CONTINUE
      }
    })
    Report(if (Files.exists(root, NOFOLLOW_LINKS)) 0 else 1, bytes, failures)
  }

  private def prune(start: Path): Unit = {
    var path = start
    var done = false
    while (!done && path != runtime && path.startsWith(runtime) && noSymlinks(path)) {
      try { Files.delete(path); path = path.getParent }
      catch { case _: DirectoryNotEmptyException | _: NoSuchFileException => done = true }
    }
  }

  private def safely(action: => Unit): Unit = try action
    catch { case NonFatal(ex) => log.warn(s"IntelliJ runtime maintenance skipped: ${ex.getMessage}") }
}
