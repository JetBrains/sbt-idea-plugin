package org.jetbrains.sbtidea

trait PluginLogger {
  def info(msg: =>String): Unit
  def warn(msg: =>String): Unit
  def error(msg: =>String): Unit
}

