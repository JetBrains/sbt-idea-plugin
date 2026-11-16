package org.jetbrains.sbtidea.runtimeCleanup

import java.nio.file.Path
import java.time.{Duration, Instant}

private[sbtidea] object RuntimeCleanupDiagnostics {
  import RuntimeCleanupAssessment.*
  import RuntimeUsageRegistry.Usage

  final case class ManagedPath(role: String, path: Path, existence: String)
  final case class Inspection(usage: Usage, age: Age, protection: Option[Protection], paths: Seq[ManagedPath])
  final case class Entry(file: Path, result: Either[String, Inspection])
  final case class Snapshot(home: Path, policy: RuntimeCleanup.Policy, observedAt: Instant,
                            lastAttempt: Either[String, Option[Instant]], eligibleAt: Instant,
                            entries: Seq[Entry], registryProblem: Option[String])

  def render(snapshot: Snapshot, checkout: Path, context: Option[String]): Seq[String] = {
    val header = Seq(
      s"Plugin home: ${snapshot.home}",
      s"Observed at (UTC): ${snapshot.observedAt}",
      s"Current checkout: $checkout",
      s"Cached runtime context: ${context.getOrElse("unavailable; this session cannot initiate maintenance until context is registered")}",
      s"Automatic cleanup initiated by this build: ${if (snapshot.policy.enabled) "enabled" else "disabled"}",
      s"This build's defaults: retention ${duration(Duration.ofNanos(snapshot.policy.retention.toNanos))}; interval ${duration(Duration.ofNanos(snapshot.policy.interval.toNanos))}",
      "Last cleanup attempt: " + snapshot.lastAttempt.fold(reason => s"unavailable ($reason)", _.fold("not recorded")(_.toString)),
      "Next pass eligible: " + (if (!snapshot.policy.enabled) "disabled by this build"
        else if (context.isEmpty) "unavailable until runtime context is registered"
        else if (snapshot.eligibleAt.isAfter(snapshot.observedAt)) s"${snapshot.eligibleAt} (subject to locks and process checks)"
        else "now (subject to locks and process checks)"),
    )
    val entries = snapshot.entries.flatMap { entry =>
      "" +: (entry.result match {
        case Left(reason) => Seq(s"Skipped record ${entry.file.getFileName}: $reason")
        case Right(inspection) =>
          val usage = inspection.usage
          val record = usage.record
          Seq(
            s"${record.buildId} / ${entry.file.getFileName.toString.stripSuffix(".properties").stripPrefix(record.buildId + "--")}",
            s"  Checkout: ${record.checkout}",
            s"  Last sbt use (UTC): ${usage.lastUsed}",
            s"  Recorded retention: ${duration(record.retention)}",
            s"  Automatic removal: ${if (record.enabled) "enabled" else "disabled"}",
            s"  Assessment: ${assessment(inspection)}",
          ) ++ RuntimeCleanup.Roles.toSeq.sorted.map { role =>
            inspection.paths.find(_.role == role) match {
              case Some(path) => s"  $role: ${snapshot.home.relativize(path.path)} [${path.existence}]"
              case None => s"  $role: not registered"
            }
          }
      })
    }
    header ++ snapshot.registryProblem.toSeq.map(reason => s"Registry unavailable: $reason") ++
      (if (snapshot.entries.isEmpty && snapshot.registryProblem.isEmpty) Seq("No registered runtimes.") else entries) ++ Seq(
      "",
      "Snapshot only: lock contention and previous skip decisions are not recorded here; process checks are best effort.",
      "Development data, explicit overrides and unregistered legacy locations are not inventoried. No size scan is performed.",
      "The report itself is read-only. Normal sbt startup/completion hooks may refresh usage or run automatic cleanup.",
    )
  }

  private def assessment(inspection: Inspection): String = inspection.age match {
    case OptedOut => "automatic removal disabled by record"
    case FutureDated => "future-dated last-use timestamp; skipped"
    case Fresh(after) => s"fresh; eligible after $after"
    case Expired(_) => inspection.protection match {
      case Some(NoMatchingProcess) => "expired; no matching Java process observed"
      case Some(MatchingProcess) => "expired; matching Java process observed; deferred"
      case _ => "expired; Java process information unavailable; deferred"
    }
  }

  private def duration(value: Duration): String = {
    val seconds = value.getSeconds
    if (value.getNano != 0) value.toString
    else {
      val (count, unit) =
        if (seconds % 86400 == 0) (seconds / 86400, "day")
        else if (seconds % 3600 == 0) (seconds / 3600, "hour")
        else if (seconds % 60 == 0) (seconds / 60, "minute")
        else (seconds, "second")
      s"$count $unit${if (count == 1) "" else "s"}"
    }
  }
}
