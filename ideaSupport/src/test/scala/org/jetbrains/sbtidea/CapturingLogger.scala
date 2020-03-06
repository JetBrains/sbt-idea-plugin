package org.jetbrains.sbtidea

private class CapturingLogger extends PluginLogger {
  val messages = new scala.collection.mutable.ArrayBuffer[String]()
  override def info(msg: => String): Unit  = {messages += msg; println(msg)}
  override def warn(msg: => String): Unit  = {messages += msg; println(msg)}
  override def error(msg: => String): Unit = {messages += msg; println(msg)}
}

object CapturingLogger {
  def captureLog(f: => Any): Seq[String] = {
    val capturingLogger = new CapturingLogger
    val previousLogger = PluginLogger.bind(capturingLogger)
    f
    PluginLogger.bind(previousLogger)
    capturingLogger.messages
  }
}
