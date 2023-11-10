package org.jetbrains.sbtidea

import org.jetbrains.sbtidea.Keys.String2Plugin
import org.scalatest.featurespec.AnyFeatureSpecLike

class UtilsTest extends AnyFeatureSpecLike {

  Feature("All example strings can be parse using `toPlugin`") {
    Utils.PluginStringExamples.foreach { exampleString =>
      Scenario(s"Parse $exampleString") {
        exampleString.toPlugin //shouldn't throw exceptions
      }
    }
  }
}
