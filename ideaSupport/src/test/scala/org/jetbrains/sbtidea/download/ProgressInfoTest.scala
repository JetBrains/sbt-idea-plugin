package org.jetbrains.sbtidea.download

import org.jetbrains.sbtidea.download.FileDownloader.ProgressInfo
import org.scalatest.featurespec.AnyFeatureSpec
import org.scalatest.matchers.should.Matchers

import java.io.File

class ProgressInfoTest extends AnyFeatureSpec with Matchers {

  Feature("render progress bar") {
    val Width = 20
    // using `_` cause test failure diff uses `...` to represent long suffixes
    // and it can be easily confused with '.' in progress bar
    def bar(percent: Int): String = ProgressInfo(percent, -1, -1, -1).renderBar(Width, '=', '_')

    val data = Seq(
      //_______01234567890123456789
      0   -> "[>___________________]",
      1   -> "[>___________________]",
      4   -> "[>___________________]",
      5   -> "[=>__________________]", // 1 / 20 done, should mark 1 cell as "done"
      6   -> "[=>__________________]",
      9   -> "[=>__________________]",
      10  -> "[==>_________________]",
      11  -> "[==>_________________]",
      14  -> "[==>_________________]",
      15  -> "[===>________________]",
      16  -> "[===>________________]",
      19  -> "[===>________________]",
      20  -> "[====>_______________]",
      21  -> "[====>_______________]",
      89  -> "[=================>__]",
      90  -> "[==================>_]",
      91  -> "[==================>_]",
      94  -> "[==================>_]",
      95  -> "[===================>]",
      96  -> "[===================>]",
      99  -> "[===================>]",
      100 -> "[====================]"
    )

    data.foreach { case (percent, expectedString) =>
      Scenario(s"percent: $percent") {
        val actual = bar(percent)
        actual shouldBe expectedString
      }
    }
  }

  Feature("health check FileDownloader creation") {
    new FileDownloader(new File("dummy").toPath)
  }
}
