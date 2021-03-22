package org.jetbrains.sbtidea.packaging

/**
  * type aliases and val aliases for sbt autoImport
  * all of the types mentioned in PackagingKeys aka exposed to the user should be aliased here to avoid the
  * necessity of explicit importing
  */
trait PackagingDefs {
  type PackagingMethod = org.jetbrains.sbtidea.packaging.PackagingMethod
  final val PackagingMethod: org.jetbrains.sbtidea.packaging.PackagingMethod.type = org.jetbrains.sbtidea.packaging.PackagingMethod

  type ShadePattern = org.jetbrains.sbtidea.packaging.ShadePattern
  final val ShadePattern: org.jetbrains.sbtidea.packaging.ShadePattern.type  = org.jetbrains.sbtidea.packaging.ShadePattern

  final val ExcludeFilter: org.jetbrains.sbtidea.packaging.ExcludeFilter.type  = org.jetbrains.sbtidea.packaging.ExcludeFilter
}
