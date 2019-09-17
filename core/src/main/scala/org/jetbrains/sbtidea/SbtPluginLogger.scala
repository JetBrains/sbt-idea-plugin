package org.jetbrains.sbtidea

import sbt.Keys._

class SbtPluginLogger(streams: TaskStreams) extends PluginLogger {
  override def info(msg: => String): Unit = streams.log.info(msg)
  override def warn(msg: => String): Unit = streams.log.warn(msg)
  override def error(msg: => String): Unit = streams.log.error(msg)
}
