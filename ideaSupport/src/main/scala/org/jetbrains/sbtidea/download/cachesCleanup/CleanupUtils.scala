package org.jetbrains.sbtidea.download.cachesCleanup

import org.jetbrains.annotations.TestOnly

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

object CleanupUtils {

  val DateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd MMM yy")
  @volatile
  private var MockTodayDate: Option[LocalDate] = None

  @TestOnly
  def setMockTodayDate(date: LocalDate): Unit = {
    MockTodayDate = Some(date)
  }

  def buildPresentableList[T](items: Seq[T], presenter: T => String): String = {
    val ListSeparator = "• "
    items.map(presenter).map(ListSeparator + _).mkString("\n").indented(2)
  }

  def cleanupRelevantTime(): LocalDate = MockTodayDate.getOrElse(LocalDate.now())

  /**
   * Formats a given date into a human-readable string indicating how long ago it was
   * (e.g., "2 days ago", "1 week ago", "more than 1 month ago").
   *
   * @param date the date to format, represented as a `LocalDate`
   * @return a string indicating how long ago the given date occurred (e.g., "today", "3 days ago", "more than 2 months ago")
   */
  def formatAgo(date: LocalDate): String = {
    val now = cleanupRelevantTime()

    val daysAgo = ChronoUnit.DAYS.between(date, now)
    val weeksAgo = daysAgo / 7
    val monthsAgo = ChronoUnit.MONTHS.between(date, now)

    if (daysAgo <= 0)
      "today"
    else if (daysAgo < 7)
      s"${pluralizeDate(daysAgo, "day")} ago"
    else if (monthsAgo <= 0)
      s"more than ${pluralizeDate(weeksAgo, "week")} ago"
    else
      s"more than ${pluralizeDate(monthsAgo, "month")} ago"
  }

  private def pluralizeDate(count: Long, singular: String): String =
    s"$count $singular${if (count == 1) "" else "s"}"

  /**
   * Formats a size in bytes to a human-readable string
   *
   * @param bytes the size in bytes
   * @return a formatted string (e.g., "1.23 MB")
   */
  def formatSize(bytes: Long): String = {
    val units = Array("B", "KB", "MB", "GB", "TB")
    var size = bytes.toDouble
    var unitIndex = 0

    while (size > 1024 && unitIndex < units.length - 1) {
      size /= 1024
      unitIndex += 1
    }

    //use fixed locale to use dot (".") as the Double delimiter
    "%.2f %s".formatLocal(java.util.Locale.US, size, units(unitIndex))
  }

  private implicit class StringOps(private val str: String) extends AnyVal {
    def indented(indent: Int): String = {
      val indentStr = " " * indent
      str.linesIterator.map(indentStr + _).mkString("\n")
    }
  }
}