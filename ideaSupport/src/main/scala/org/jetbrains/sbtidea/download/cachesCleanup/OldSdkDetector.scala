package org.jetbrains.sbtidea.download.cachesCleanup

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import scala.concurrent.duration.{DurationInt, FiniteDuration}

private object OldSdkDetector {
  private val NumberOfActivelyDevelopedMajorVersions = 2
  private val NumberOfLatestMinorVersionsToKeepLonger = 2

  private val KeepActivelyDevelopedReleasesThreshold = 14.days
  private val KeepPreviousReleasesThreshold = 30.days
  private val KeepLongerLatestMinorVersionsThreshold = 60.days

  /**
   * Detects potentially unused IntelliJ IDE SDKs cached locally and suggests them for removal to free up disk space.
   *
   * =Definitions=
   *  - "Old SDK": A cached IntelliJ SDK directory no longer needed for active local development, stored in the SDK root (e.g., `~/.ScalaPluginIC/sdk`)
   *  - "Latest release": The most recent stable version of IntelliJ
   *  - "Next release": The current Early Access Program (EAP) version of IntelliJ being tested
   *  - "Previous releases": All releases before the "Latest release". No active development is done there
   *
   * For example, suppose today is 30 May 2025. Then:
   *   - "Latest release" ~ 2025.1
   *   - "Next release" ~ 2025.2
   *   - "Previous release" ~ 2024.3 and older
   *
   * =Old SDK Detection Logic=
   * We use the creation date of the SDK directory as a simple heuristic. It should provide a good balance
   * between avoiding disk space waste, average time spent on downloading SDK locally and the complexity of the logic.<br>
   * Sdk is considered old in these cases:
   *  - For the latest and next release: SDK directory was created more than 2 weeks ago
   *  - For the previous releases: SDK directory was created more than 1 months ago
   *  - For all versions (latest, next and previous releases) keep at least 1 latest version for 2 months.
   *
   * =Notes=
   *  - Older SDK directories (e.g., for IntelliJ 243.* in May 2025) are rarely needed during local development, except for critical bug fixes
   *  - SDKs for actively developed versions (e.g., 251.* and 252.*) are frequently updated, and intermediate versions do not need to be retained
   *  - The 2 most recent major versions in SDKs root are considered "actively developed".
   *    The true way to know which version is considered "Latest release" we would need to access the Internet
   *    or introduce some logic based on the current date. But we decided not to do it for simplicity of the logic.
   *
   *  - Note, sometimes the development in the actively developed branches can take longer than 2 weeks (usually when working on bigger tickets).
   *    It would be annoying if the version is automatically removed in this case. Thus, we ensure that at least 1 latest version is kept per actively developed version.
   *
   *  - Sometimes there might be some edge cases when an SDK is detected as "old" and removed when not expected.
   *    This can bring some inconveniences.
   *    However, he SDK will be re-downloaded automatically, which ensures that local development is not blocked.
   */
  def detectOldSdks(
    sdkInfos: Seq[IntellijSdkDirInfo]
  ): Seq[IntellijSdkDirInfo] = {
    val sdksByMajorVersion = sdkInfos.groupBy(_.majorVersion)

    val sortedMajorVersions = sdksByMajorVersion.keys.toSeq.sorted.reverse
    val activelyDevelopedMajorVersions = sortedMajorVersions.take(NumberOfActivelyDevelopedMajorVersions).toSet
    val oldSdks = sortedMajorVersions.flatMap { majorVersion =>
      getOldSdksForVersion(majorVersion, sdksByMajorVersion(majorVersion), activelyDevelopedMajorVersions)
    }

    oldSdks
  }

  private def getOldSdksForVersion(
    majorVersion: Int,
    sdksForVersion: Seq[IntellijSdkDirInfo],
    activelyDevelopedMajorVersions: Set[Int]
  ): Seq[IntellijSdkDirInfo] = {
    val sortedSdks = sdksForVersion.sortBy(_.fullVersion).reverse
    val isActivelyDeveloped = activelyDevelopedMajorVersions.contains(majorVersion)

    val (latestMinorVersions, olderMinorVersions) = sortedSdks.splitAt(NumberOfLatestMinorVersionsToKeepLonger)

    val threshold = if (isActivelyDeveloped)
      KeepActivelyDevelopedReleasesThreshold
    else
      KeepPreviousReleasesThreshold

    val oldVersionsFromLatest = latestMinorVersions.filter(olderThan(_, KeepLongerLatestMinorVersionsThreshold))
    val oldVersionsFromOlder = olderMinorVersions.filter(olderThan(_, threshold))

    oldVersionsFromLatest ++ oldVersionsFromOlder
  }

  private def olderThan(sdk: IntellijSdkDirInfo, duration: FiniteDuration): Boolean =
    isOlderThan(sdk.dirInfo.creationDate, duration)
}