package org.jetbrains.sbtidea.packaging

/**
 * @param revisionHint used as a reminder where the test data was generated from
 */
case class RevisionTestDataDescription(
  testDataFileName: String,
  revisionHint: RevisionReference
)
