package org.jetbrains.sbtidea

import scala.collection.mutable.ArrayBuffer

trait PluginLogger {
  def info(msg: =>String): Unit
  def warn(msg: =>String): Unit
  def error(msg: =>String): Unit
  def fatal(msg: =>String): Unit
}

object PluginLogger extends PluginLogger {
  private[this] class BufferLogger extends PluginLogger {
    private val (lINFO, lWARN, lERR, lFATAL) = (1, 2, 3, 4)
    private val buffer = ArrayBuffer[(Int, String)]()
    override def info(msg: => String): Unit = buffer += lINFO -> msg
    override def warn(msg: => String): Unit = buffer += lWARN -> msg
    override def error(msg: => String): Unit = buffer += lERR -> msg
    override def fatal(msg: => String): Unit = buffer += lFATAL -> msg
    def flush(actualLogger: PluginLogger): Unit = buffer.foreach {
      case (level, msg) if level == lINFO => actualLogger.info(msg)
      case (level, msg) if level == lWARN => actualLogger.warn(msg)
      case (level, msg) if level == lERR  => actualLogger.error(msg)
      case (level, msg) if level == lFATAL  => actualLogger.fatal(msg)
    }

  }
  private var instance: PluginLogger = new BufferLogger
  def bind(actualLogger: PluginLogger): PluginLogger = {
    val savedInstance = instance
    instance match {
      case bl: BufferLogger =>
        bl.flush(actualLogger)
        instance = actualLogger
      case _ =>
        instance = actualLogger
    }
    savedInstance
  }
  override def info(msg: => String): Unit  = instance.info(msg)
  override def warn(msg: => String): Unit  = instance.warn(msg)
  override def error(msg: => String): Unit = instance.error(msg)
  override def fatal(msg: => String): Unit = instance.fatal(msg)
}

