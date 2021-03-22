package org.jetbrains.sbtidea.packaging

object MAPPING_KIND extends Enumeration {
  type MAPPING_KIND = Value
  val TARGET, LIB, LIB_ASSEMBLY, MISC, UNDEFINED = Value
}
