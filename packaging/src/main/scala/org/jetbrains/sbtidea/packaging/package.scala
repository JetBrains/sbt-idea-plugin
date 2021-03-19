package org.jetbrains.sbtidea

package object packaging {

  type Mappings = Seq[Mapping]

  implicit def MappingOrder[A <: Mapping]: Ordering[A] = Ordering.by(x => x.from -> x.to) // order by target jar file

}
