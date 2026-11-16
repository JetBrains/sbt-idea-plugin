package org.jetbrains.sbtidea.runtimeCleanup

import org.jetbrains.sbtidea.PluginLogger
import org.jetbrains.sbtidea.download.cachesCleanup.CleanupUtils

import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.*
import java.time.{Duration, Instant}
import scala.concurrent.duration.FiniteDuration
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
  import RuntimeCleanupAssessment.*
  import RuntimeUsageRegistry.Usage
  private val registry = new RuntimeUsageRegistry(pluginHome, log, now)
  val entries: Path = registry.entries
  private var nextCheck = Instant.MIN

  def marker(checkout: Path, buildId: String): Path = registry.marker(checkout, buildId)

  def refresh(checkout: Path, buildId: String, paths: Map[String, Path], replaceRoles: Set[String] = Roles): Unit =
    registry.refresh(checkout, buildId, paths, policy, replaceRoles)

  def withdraw(paths: Set[Path]): Unit = registry.withdraw(paths)

  def cleanupIfDue(): Report = synchronized {
    var report = Report()
    safely {
      val time = now()
      if (policy.enabled && !time.isBefore(nextCheck)) {
        // A cheap local deadline also prevents contending sbt processes retrying on every task.
        nextCheck = time.plusNanos(policy.interval.toNanos)
        registry.withCleanupLock {
          val deadline = persistedDeadline(registry.lastAttempt, time, Duration.ofNanos(policy.interval.toNanos))
          if (deadline.isAfter(time)) {
            nextCheck = deadline
          } else {
            registry.recordAttempt(time)
            // Lazily inspect processes once, only if a candidate survives metadata and lock checks.
            lazy val active = activePaths()
            registry.records().foreach { file =>
              if (registry.readUsage(file).toOption.exists(usage => expired(usage, time))) {
                registry.withRecordLock(file) {
                  registry.readUsage(file).toOption.filter(usage => expired(usage, time)).foreach { usage =>
                    val record = usage.record
                    protection(record, active) match {
                      case NoMatchingProcess =>
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

  private def expired(usage: Usage, time: Instant): Boolean = age(usage.record, usage.lastUsed, time).eligible

  /** Observes registry metadata and a single lazy process snapshot; never touches locks or deadlines. */
  def inspect(): RuntimeCleanupDiagnostics.Snapshot = synchronized {
    import RuntimeCleanupDiagnostics.*
    val time = now()
    val lastAttempt = registry.inspectLastAttempt
    val persisted = persistedDeadline(lastAttempt.toOption.flatten, time, Duration.ofNanos(policy.interval.toNanos))
    val eligibleAt = if (nextCheck.isAfter(persisted)) nextCheck else persisted
    lazy val active = activePaths()
    val records = registry.listRecords()
    val entries = records.getOrElse(Seq.empty).map { file =>
      Entry(file, registry.readUsage(file).map { usage =>
        val assessment = age(usage.record, usage.lastUsed, time)
        val protectionStatus = if (assessment.eligible) Some(protection(usage.record, active)) else None
        val paths = usage.record.paths.toSeq.sortBy(_._1).map { case (role, path) =>
          val existence = if (Files.exists(path, NOFOLLOW_LINKS)) "present"
            else if (Files.notExists(path, NOFOLLOW_LINKS)) "absent" else "unavailable"
          ManagedPath(role, path, existence)
        }
        Inspection(usage, assessment, protectionStatus, paths)
      })
    }
    Snapshot(registry.home, policy, time, lastAttempt, eligibleAt, entries, records.left.toOption)
  }

  private def activePaths(): Option[Set[Path]] =
    try processes().map(_.map(_.toFile.getCanonicalFile.toPath))
    catch { case NonFatal(_) => None }

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
    while (!done && path != registry.runtime && path.startsWith(registry.runtime) && registry.noSymlinks(path)) {
      try { Files.delete(path); path = path.getParent }
      catch { case _: DirectoryNotEmptyException | _: NoSuchFileException => done = true }
    }
  }

  private def safely(action: => Unit): Unit = try action
    catch { case NonFatal(ex) => log.warn(s"IntelliJ runtime maintenance skipped: ${ex.getMessage}") }
}
