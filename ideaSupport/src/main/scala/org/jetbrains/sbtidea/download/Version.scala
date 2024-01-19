package org.jetbrains.sbtidea.download

case class Version(versionString: String)

object Version {
  implicit val ordering: Ordering[Version] = (x: Version, y: Version) => VersionComparatorUtil.compare(x.versionString, y.versionString)
}
