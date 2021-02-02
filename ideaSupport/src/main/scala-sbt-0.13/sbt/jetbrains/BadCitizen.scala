package sbt.jetbrains

import sbt._

// access private[sbt] members
object BadCitizen {
  def extractStamps(report: UpdateReport): Map[File, Long] = report.stamps
}