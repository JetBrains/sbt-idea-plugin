package org.jetbrains.sbtidea.download

case class Version(versionString: String)

object Version {
  implicit val ordering: Ordering[Version] = new Ordering[Version] {
    override def compare(x: Version, y: Version): Int =
      VersionComparatorUtil.compare(x.versionString, y.versionString)
  }
}
