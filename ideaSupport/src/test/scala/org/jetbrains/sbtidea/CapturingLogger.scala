package org.jetbrains.sbtidea

private class CapturingLogger extends PluginLogger {
  val messages = new scala.collection.mutable.ArrayBuffer[String]()
  private def capture(msg: => String): Unit = {messages += msg; println(msg)}
  override def info(msg: => String): Unit  = capture("[info] " + msg)
  override def warn(msg: => String): Unit  = capture("[warn] " + msg)
  override def error(msg: => String): Unit = capture("[error] " + msg)
  override def fatal(msg: => String): Unit = capture("[fatal] " + msg)
}

object CapturingLogger {
  def captureLog(f: => Any): Seq[String] = {
    val capturingLogger = new CapturingLogger
    val previousLogger = PluginLogger.bind(capturingLogger)
    f
    PluginLogger.bind(previousLogger)
    capturingLogger.messages
  }
  def captureLogAndValue[T](f: => T): (Seq[String], T) = {
    val capturingLogger = new CapturingLogger
    val previousLogger = PluginLogger.bind(capturingLogger)
    val result = f
    PluginLogger.bind(previousLogger)
    capturingLogger.messages -> result
  }
}
