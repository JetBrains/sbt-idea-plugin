package org.jetbrains.sbtidea.runtimeCleanup

import org.jetbrains.sbtidea.{CheckoutRuntimeDirectories, PluginLogger}

import java.nio.file.Path
import java.time.Instant
import scala.concurrent.duration.*

/** One settings-generation context. Task-completion callbacks never evaluate sbt tasks. */
private[sbtidea] final class RuntimeMaintenance(
  home: Path,
  resolver: CheckoutRuntimeDirectories,
  policy: RuntimeCleanup.Policy,
  // A setting dependency on the identity task itself invalidates cached context after `set`.
  val identityGeneration: AnyRef,
  log: PluginLogger,
  now: () => Instant = () => Instant.now(),
  processes: () => Option[Set[Path]] = () => RuntimeProcesses.activePaths(),
) {
  private val cleanup = new RuntimeCleanup(home, policy, log, now, processes)
  private var context: Option[(String, Map[String, Path])] = None
  private var excluded = Set.empty[Path]
  private var nextTouch = Instant.MIN

  def register(buildId: String, paths: Map[String, Path], roles: Set[String]): Unit = synchronized {
    val retained = context.filter(_._1 == buildId).map(_._2).getOrElse(Map.empty) -- roles
    val managed = paths.filterNot { case (_, path) => excluded(path.toFile.getCanonicalFile.toPath) }
    val selected = retained ++ managed
    cleanup.refresh(resolver.buildRoot.toPath, buildId, managed, roles)
    context = Some(buildId -> selected)
    nextTouch = now().plusSeconds(1.hour.toSeconds)
  }

  def withdraw(paths: Set[Path]): Unit = synchronized {
    excluded ++= paths.map(_.toFile.getCanonicalFile.toPath)
    cleanup.withdraw(paths)
    context = context.map { case (id, selected) => id -> selected.filterNot { case (_, path) => excluded(path.toFile.getCanonicalFile.toPath) } }
  }

  def invalidate(): Unit = synchronized { context = None }

  def showCleanupReport(): Unit = synchronized {
    RuntimeCleanupDiagnostics.render(cleanup.inspect(), resolver.buildRoot.toPath, context.map(_._1))
      .foreach(line => log.info(line))
  }

  def afterBatch(): Unit = synchronized {
    context.foreach { case (buildId, paths) =>
      if (!now().isBefore(nextTouch)) {
        cleanup.refresh(resolver.buildRoot.toPath, buildId, paths, paths.keySet)
        nextTouch = now().plusSeconds(1.hour.toSeconds)
      }
      cleanup.cleanupIfDue()
    }
  }
}
