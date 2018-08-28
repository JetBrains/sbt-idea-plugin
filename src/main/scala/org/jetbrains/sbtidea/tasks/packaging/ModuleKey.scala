package org.jetbrains.sbtidea.tasks.packaging

import sbt.ModuleID

case class ModuleKey(id: ModuleID, attributes: Map[String, String]) {
  def ~==(other: ModuleKey): Boolean = id.organization == other.id.organization && id.name == other.id.name

  override def hashCode(): Int = id.organization.hashCode

  override def equals(o: scala.Any): Boolean = o match {
    case ModuleKey(_id, _attributes) =>
      id.organization.equals(_id.organization) &&
        (id.name == _id.name || id.name.matches(_id.name)) &&
        (id.revision == _id.revision || id.revision.matches(_id.revision)) &&
        attributes == _attributes
    case _ => false
  }

  override def toString: String = s"$id[${if (attributes.nonEmpty) attributes.toString else ""}]"
}
