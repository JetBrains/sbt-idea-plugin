package org.jetbrains.sbtidea.packaging

import java.io.File

case class Mapping(
  from: File,
  to: File,
  metaData: MappingMetaData
)