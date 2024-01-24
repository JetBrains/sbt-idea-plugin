package org.jetbrains.sbtidea.tasks

import org.apache.hc.client5.http.entity.mime.InputStreamBody
import org.apache.hc.core5.http.ContentType

import java.io.{InputStream, OutputStream}

class ProgressTrackingInputStreamBody(
  in: InputStream,
  contentType: ContentType,
  filename: String,
  trackFunc: Long => Unit
) extends InputStreamBody(in, contentType, filename) {

  override def writeTo(out: OutputStream): Unit = {
    val buffer = new Array[Byte](4096)
    var bytesRead: Int = 0
    var bytesTotalRead: Long = 0

    try while ({
      bytesRead = in.read(buffer)
      bytesRead != -1
    }){
      out.write(buffer, 0, bytesRead)
      bytesTotalRead += bytesRead
      trackFunc(bytesTotalRead)
    } finally {
      this.in.close()
    }
  }
}