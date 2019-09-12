package org.jetbrains.sbtidea

trait ConsoleLogger extends LogAware {
  override protected def log: PluginLogger =  new PluginLogger {
    override def info(msg: => String): Unit = println(msg)
    override def warn(msg: => String): Unit = println(msg)
    override def error(msg: => String): Unit = sys.error(msg)
  }
}