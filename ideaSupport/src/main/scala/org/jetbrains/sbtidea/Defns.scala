package org.jetbrains.sbtidea

import java.net.URL

trait Defns { this: Keys.type =>

  sealed trait IdeaPlugin {
    val name: String
  }

  object IdeaPlugin {
    final case class Id(name: String, id: String, channel: Option[String]) extends IdeaPlugin
    final case class Zip(name: String, url: URL) extends IdeaPlugin
    final case class Jar(name: String, url: URL) extends IdeaPlugin
  }

  sealed trait IdeaEdition {
    val name: String
    def shortname: String = name.takeRight(2)
  }

  object IdeaEdition {
    object Community extends IdeaEdition {
      override val name = "ideaIC"
    }

    object Ultimate extends IdeaEdition {
      override val name = "ideaIU"
    }
  }

  final case class PublishSettings(pluginId: String, username: String, password: String, channel: Option[String])

}
