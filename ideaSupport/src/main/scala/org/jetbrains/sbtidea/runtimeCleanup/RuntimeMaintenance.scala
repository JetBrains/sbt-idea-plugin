package org.jetbrains.sbtidea.runtimeCleanup

import org.jetbrains.sbtidea.{CheckoutRuntimeDirectories, PluginLogger}

import java.nio.file.Path
import java.time.Instant
import scala.concurrent.duration.*
import scala.util.control.NonFatal

/** One settings-generation context. Task-completion callbacks never evaluate sbt tasks. */
private[sbtidea] final class RuntimeMaintenance(
  home: Path,
  resolver: CheckoutRuntimeDirectories,
  policy: RuntimeCleanup.Policy,
  // Task-value dependencies invalidate context when identity or any directory override changes.
  val identityGeneration: AnyRef,
  log: PluginLogger,
  now: () => Instant = () => Instant.now(),
  processes: () => Option[Set[Path]] = () => RuntimeProcesses.activePaths(),
) {
  private val cleanup = new RuntimeCleanup(home, policy, log, now, processes)
  private var context: Option[(String, Map[String, Path])] = None
  private var excluded = Set.empty[Path]
  private var ready = false
  private var nextTouch = Instant.MIN

  def overridesReady: Boolean = synchronized { ready }

  /** Reconcile every project's explicit paths before registration or automatic deletion. */
  def initializeOverrides(paths: Set[Path]): Unit = synchronized {
    ready = false
    try {
      excluded ++= paths.map(_.toFile.getCanonicalFile.toPath)
      context = context.map { case (id, selected) => id -> selected.filterNot { case (_, path) => isExcluded(path) } }
      ready = cleanup.withdraw(excluded)
      if (!ready) log.warn("IntelliJ runtime cleanup deferred: explicit directory ownership could not be withdrawn")
    } catch {
      case NonFatal(ex) => log.warn(s"IntelliJ runtime cleanup deferred: ${ex.getMessage}")
    }
  }

  private def isExcluded(path: Path): Boolean =
    excluded.exists(RuntimeCleanup.overlaps(_, path.toFile.getCanonicalFile.toPath))

  def register(buildId: String, paths: Map[String, Path], roles: Set[String]): Unit = synchronized {
    if (!ready) return
    val retained = context.filter(_._1 == buildId).map(_._2).getOrElse(Map.empty) -- roles
    val managed = paths.filterNot { case (_, path) => isExcluded(path) }
    val selected = retained ++ managed
    cleanup.refresh(resolver.buildRoot.toPath, buildId, managed, roles)
    context = Some(buildId -> selected)
    nextTouch = now().plusSeconds(1.hour.toSeconds)
  }

  def invalidate(): Unit = synchronized { context = None }

  def showCleanupReport(): Unit = synchronized {
    RuntimeCleanupDiagnostics.render(cleanup.inspect(), resolver.buildRoot.toPath, context.map(_._1))
      .foreach(line => log.info(line))
  }

  def afterBatch(): Unit = synchronized {
    if (ready) context.foreach { case (buildId, paths) =>
      if (!now().isBefore(nextTouch)) {
        cleanup.refresh(resolver.buildRoot.toPath, buildId, paths, paths.keySet)
        nextTouch = now().plusSeconds(1.hour.toSeconds)
      }
      cleanup.cleanupIfDue()
    }
  }
}
