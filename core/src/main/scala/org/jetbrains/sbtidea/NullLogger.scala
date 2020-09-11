package org.jetbrains.sbtidea

object NullLogger extends PluginLogger {
  override def info(msg: => String): Unit = ()
  override def warn(msg: => String): Unit = ()
  override def error(msg: => String): Unit = ()
  override def fatal(msg: => String): Unit = ()
}
