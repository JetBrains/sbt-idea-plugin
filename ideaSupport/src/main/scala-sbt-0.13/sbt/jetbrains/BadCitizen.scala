package sbt.jetbrains

import sbt._

object BadCitizen {
  def extractStamps(report: UpdateReport): Map[File, Long] = report.stamps
}