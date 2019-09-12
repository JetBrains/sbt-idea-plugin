package org.jetbrains.sbtidea

class CapturingLogger extends PluginLogger {
  val messages = new scala.collection.mutable.ArrayBuffer[String]()
  override def info(msg: => String): Unit  = {messages += msg; println(msg)}
  override def warn(msg: => String): Unit  = {messages += msg; println(msg)}
  override def error(msg: => String): Unit = {messages += msg; println(msg)}
}
