package org.jetbrains.sbtidea.runtimeCleanup

import java.nio.file.Path
import java.time.{Duration, Instant}

/** Pure decisions shared by inspection and deletion. Filesystem/process snapshots are inputs. */
private[sbtidea] object RuntimeCleanupAssessment {
  sealed trait Age { def eligible: Boolean = false }
  case object OptedOut extends Age
  case object FutureDated extends Age
  final case class Fresh(eligibleAfter: Instant) extends Age
  final case class Expired(eligibleAfter: Instant) extends Age { override def eligible: Boolean = true }

  sealed trait Protection
  case object NoMatchingProcess extends Protection
  case object MatchingProcess extends Protection
  case object ProcessInformationUnavailable extends Protection

  def age(record: RuntimeCleanup.Record, lastUsed: Instant, time: Instant): Age = {
    if (!record.enabled) OptedOut
    else if (lastUsed.isAfter(time)) FutureDated
    else if (Duration.between(lastUsed, time).compareTo(record.retention) > 0) Expired(lastUsed.plus(record.retention))
    else Fresh(lastUsed.plus(record.retention))
  }

  // Both registered paths and observed process paths have canonical plugin-home prefixes.
  def protection(record: RuntimeCleanup.Record, active: Option[Set[Path]]): Protection = active match {
    case None => ProcessInformationUnavailable
    case Some(paths) if record.paths.values.exists(path => paths.exists(activePath =>
      activePath == path || activePath.startsWith(path) || path.startsWith(activePath))) => MatchingProcess
    case _ => NoMatchingProcess
  }

  def persistedDeadline(lastAttempt: Option[Instant], time: Instant, interval: Duration): Instant =
    lastAttempt.filter(t => !t.isAfter(time) && Duration.between(t, time).compareTo(interval) < 0)
      .map(_.plus(interval)).getOrElse(time)
}
