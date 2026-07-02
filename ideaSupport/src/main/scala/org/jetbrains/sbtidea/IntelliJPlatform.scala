package org.jetbrains.sbtidea

sealed trait IntelliJPlatform {
  val name: String
  def edition: String = name.takeRight(2)
  def platformPrefix: String
  override def toString: String = name
}

object IntelliJPlatform {
  object Idea extends IntelliJPlatform {
    override val name = "ideaIU"
    override def platformPrefix: String = ""
  }

  object PyCharm extends IntelliJPlatform {
    override val name: String = "pycharmPY"
    override def platformPrefix: String = "Python"
  }

  @deprecated("Use IntelliJPlatform.Idea instead")
  object IdeaCommunity extends IntelliJPlatform {
    override val name = "ideaIC"
    override def platformPrefix: String = "Idea"
  }

  @deprecated("Use IntelliJPlatform.Idea instead")
  object IdeaUltimate extends IntelliJPlatform {
    override val name = "ideaIU"
    override def platformPrefix: String = ""
  }

  @deprecated("Use IntelliJPlatform.PyCharm instead")
  object PyCharmCommunity extends IntelliJPlatform {
    override val name: String = "pycharmPC"
    override def platformPrefix: String = "PyCharmCore"
  }

  @deprecated("Use IntelliJPlatform.PyCharm instead")
  object PyCharmProfessional extends IntelliJPlatform {
    override val name: String = "pycharmPY"
    override def platformPrefix: String = "Python"
  }

  object CLion extends IntelliJPlatform {
    override val name: String = "clion"
    override def edition: String = name
    override def platformPrefix: String = "CLion"
  }

  object MPS extends IntelliJPlatform {
    override val name: String = "mps"
    override def edition: String = name
    override def platformPrefix: String = ""
  }
}
