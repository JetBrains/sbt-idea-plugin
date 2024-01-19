package org.jetbrains.sbtidea.packaging.artifact

import sbt.Keys.TaskStreams

import java.io.{BufferedOutputStream, ByteArrayInputStream, ObjectInputStream, ObjectOutputStream}
import java.nio.file.{Files, Path}
import scala.collection.mutable
import scala.util.Using

trait IncrementalCache extends AutoCloseable {
  def fileChanged(in: Path): Boolean
}

object DumbIncrementalCache extends IncrementalCache {
  override def fileChanged(in: Path): Boolean = true
  override def close(): Unit = ()
}

class PersistentIncrementalCache(private val root: Path)(implicit private val streams: TaskStreams) extends IncrementalCache {

  private val FILENAME = "sbtidea.cache"
  private val myFile   = root.resolve(FILENAME)
  private val myData   = loadOrCreate()

  private type Data = mutable.HashMap[String, Long]

  private def loadFromDisk(): Either[String, Data] = {
    if (!Files.exists(myFile) || Files.size(myFile) <= 0)
      return Left("Cache file is empty or doesn't exist")
    val data = Files.readAllBytes(myFile)
    Using.resource(new ObjectInputStream(new ByteArrayInputStream(data))) { stream =>
      Right(stream.readObject().asInstanceOf[Data])
    }
  }

  private def loadOrCreate(): Data = loadFromDisk() match {
    case Left(message) =>
      streams.log.info(message)
      new Data()
    case Right(value) => value
  }

  private def saveToDisk(): Unit = {
    import java.nio.file.StandardOpenOption.*
    if (!Files.exists(myFile.getParent)) {
      Files.createDirectories(myFile.getParent)
      Files.createFile(myFile)
    }
    Using.resource(new ObjectOutputStream(new BufferedOutputStream(
      Files.newOutputStream(myFile, CREATE, WRITE, TRUNCATE_EXISTING)
    ))) { stream =>
      stream.writeObject(myData)
    }
  }

  override def close(): Unit = saveToDisk()

  override def fileChanged(in: Path): Boolean = {
    val newTimestamp = Files.getLastModifiedTime(in).toMillis
    val inStr = in.toString
    val lastTimestamp = myData.getOrElseUpdate(inStr, newTimestamp)
    val result = newTimestamp > lastTimestamp
    myData.put(inStr, newTimestamp)
    result
  }
}

