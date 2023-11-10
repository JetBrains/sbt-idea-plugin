package org.jetbrains.sbtidea.download.idea

import org.jetbrains.sbtidea.Keys.*
import org.jetbrains.sbtidea.PluginLogger as log
import org.jetbrains.sbtidea.download.{BuildInfo, NotFoundHttpResponseCode}
import sbt.{MavenRepository, url}

import java.io.IOException
import java.net.SocketTimeoutException

object IntellijVersionUtils {
  private val LoggerName = this.getClass.getSimpleName.stripSuffix("$")

  case class IntellijArtifactLocationDescriptor(
    artifactVersion: String,
    repository: sbt.MavenRepository,
    url: java.net.URL,
  )

  /**
   * The method detects location of IntelliJ artifact with build number `platform.buildNumber`
   * in Maven-like intellij repository (see `Repositories`). The reason why it's needed is because
   * the same build number format can correspond to different artifact locations.<br>
   * (different repository and maven artifact version)<br>
   * (NOTE: IntelliJ build number and artifact version are different entities)
   *
   * Sometimes we can determine the location only based on the version format.<br>
   * But sometimes we need to check availability of artifact in the repository<br>
   * For example build number `222.2270` can only correspond to a Nightly version.<br>
   * Build number `222.2270.15` can correspond to a Release version or EAP version or EAP-Candidate version.
   *
   * This table shows possible options: {{{
   *   ________________________________________________________________________________________
   *  | Build Number                | Comments                 | Artifact Version                |  Repository
   *  | 231.5432                    | No public releases yet   | 231-SNAPSHOT                    |  nightly
   *  | 231.5432.12                 | EAP released             | 231.5432.12-EAP-SNAPSHOT        |  snapshots
   *  | 231.5432.17                 | EAP Candidate released   | 231.5432-EAP-CANDIDATE-SNAPSHOT |  snapshots
   *  | 231.5432.24                 | Release released         | 231.5432.24                     |  releases
   *  | 231.5432.12-EAP-SNAPSHOT    | build number is specific | 231.5432.12-EAP-SNAPSHOT        |  snapshots
   *  | LATEST-EAP-SNAPSHOT         | build number is specific | LATEST-EAP-SNAPSHOT             |  snapshots
   * }}}
   */
  def detectArtifactLocation(platform: BuildInfo, artifactSuffix: String): IntellijArtifactLocationDescriptor = {
    val intellijVersion = platform.buildNumber

    val coordinates = getCoordinates(platform.edition)

    val intellijVersionWithoutTail =
      if (intellijVersion.contains("."))
        intellijVersion.substring(0, intellijVersion.lastIndexOf('.'))
      else
        intellijVersion

    import BuildInfo.*

    val (artifactVersion, repositoryUrl) =
      if (intellijVersion == LATEST_EAP_SNAPSHOT)
        (intellijVersion, IntellijRepositories.Eap)
      else if (intellijVersion.endsWith(EAP_SNAPSHOT_SUFFIX))
        (intellijVersion, IntellijRepositories.Eap)
      else if (intellijVersion.endsWith(EAP_CANDIDATE_SNAPSHOT_SUFFIX))
        (intellijVersion, IntellijRepositories.Eap)
      else if (intellijVersion.endsWith(SNAPSHOT_SUFFIX))
        (intellijVersion, IntellijRepositories.Nightly)
      else if (intellijVersion.count(_ == '.') == 1) {
        //222.2270 -> 222-SNAPSHOT
        val intellijVersionNightly = intellijVersionWithoutTail + SNAPSHOT_SUFFIX
        (intellijVersionNightly, IntellijRepositories.Nightly)
      } else {
        //failed to determine kind of a build statically
        //try to dynamically determine which version is it from Internet

        //222.2270.15 -> 222.2270.15-EAP-SNAPSHOT
        val intellijVersionEap = intellijVersion + EAP_SNAPSHOT_SUFFIX
        //222.2270.15 -> 222.2270-EAP-CANDIDATE-SNAPSHOT
        val intellijVersionEapCandidate = intellijVersionWithoutTail + EAP_CANDIDATE_SNAPSHOT_SUFFIX

        if (isReleaseBuildAvailable(coordinates, intellijVersion, artifactSuffix))
          (intellijVersion, IntellijRepositories.Releases)
        else if (isEapBuildAvailable(coordinates, intellijVersionEap, artifactSuffix))
          (intellijVersionEap, IntellijRepositories.Eap)
        else if (isEapBuildAvailable(coordinates, intellijVersionEapCandidate, artifactSuffix))
          (intellijVersionEapCandidate, IntellijRepositories.Eap)
        else {
          val fallback = (intellijVersionEapCandidate, IntellijRepositories.Eap)
          log.warn(s"[$LoggerName] Cannot detect artifact location for version $intellijVersion, fallback to: $fallback")
          fallback
        }
      }

    val urlString = buildIntelliJArtifactUrl(repositoryUrl, coordinates, artifactVersion, artifactSuffix)
    IntellijArtifactLocationDescriptor(
      artifactVersion,
      repositoryUrl,
      url(urlString)
    )
  }

  private case class IntelliJProductCoordinates(groupPath: String, artifactId: String)

  private def getCoordinates(platform: IntelliJPlatform): IntelliJProductCoordinates = platform match {
    case IntelliJPlatform.IdeaCommunity => IntelliJProductCoordinates("com/jetbrains/intellij/idea", "ideaIC")
    case IntelliJPlatform.IdeaUltimate => IntelliJProductCoordinates("com/jetbrains/intellij/idea", "ideaIU")
    case IntelliJPlatform.PyCharmCommunity => IntelliJProductCoordinates("com/jetbrains/intellij/pycharm", "pycharmPC")
    case IntelliJPlatform.PyCharmProfessional => IntelliJProductCoordinates("com/jetbrains/intellij/pycharm", "pycharmPY")
    case IntelliJPlatform.CLion => IntelliJProductCoordinates("com/jetbrains/intellij/clion", "clion")
    case IntelliJPlatform.MPS => IntelliJProductCoordinates("com/jetbrains/mps", "mps")
  }

  private def isReleaseBuildAvailable(
    coordinates: IntelliJProductCoordinates,
    buildNumber: String,
    artifactSuffix: String
  ): Boolean = {
    val url = buildIntelliJArtifactUrl(IntellijRepositories.Releases, coordinates, buildNumber, artifactSuffix)
    isResourceFound(url)
  }

  private def isEapBuildAvailable(
    coordinates: IntelliJProductCoordinates,
    buildNumber: String,
    artifactSuffix: String
  ): Boolean = {
    val url = buildIntelliJArtifactUrl(IntellijRepositories.Eap, coordinates, buildNumber, artifactSuffix)
    isResourceFound(url)
  }

  private def buildIntelliJArtifactUrl(
    repository: MavenRepository,
    coordinates: IntelliJProductCoordinates,
    buildNumber: String,
    artifactSuffix: String
  ): String = {
    val IntelliJProductCoordinates(groupPath, artifactId) = coordinates
    s"${repository.root}/$groupPath/$artifactId/$buildNumber/$artifactId-$buildNumber$artifactSuffix"
  }

  private def isResourceFound(urlText: String): Boolean = {
    import java.net.{HttpURLConnection, URL}

    val url = new URL(urlText)
    try {
      val connection = url.openConnection().asInstanceOf[HttpURLConnection]
      connection.setRequestMethod("GET")
      connection.connect()
      val rc = connection.getResponseCode
      connection.disconnect()
      rc != NotFoundHttpResponseCode
    } catch {
      case _: IOException | _: SocketTimeoutException =>
        //no internet, for example
        false
    }
  }
}
