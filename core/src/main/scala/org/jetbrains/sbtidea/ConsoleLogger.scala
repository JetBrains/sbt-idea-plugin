package org.jetbrains.sbtidea

final class ConsoleLogger extends PluginLogger {
  override def debug(msg: => String): Unit = println(msg)
  override def info(msg: => String): Unit = println(msg)
  override def warn(msg: => String): Unit = println(msg)
  override def error(msg: => String): Unit = throw new RuntimeException(msg)
  override def fatal(msg: => String): Unit = throw new RuntimeException(msg)
}