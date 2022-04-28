package org.jetbrains.sbtidea.runIdea

import java.nio.file.Path

import scala.collection.JavaConverters._
import scala.collection.mutable

import org.jetbrains.sbtidea._

case class IntellijVMOptions(platform: IntelliJPlatform,
                             pluginPath: Path,
                             ideaHome: Path,
                             xmx: Int = 1536,
                             xms: Int = 128,
                             reservedCodeCacheSize: Int = 512,
                             softRefLRUPolicyMSPerMB: Int = 50,
                             gc: String = "-XX:+UseG1GC",
                             gcOpt: String = "-XX:CICompilerCount=2",
                             noPCE: Boolean = false,
                             debug: Boolean = true,
                             debugPort: Int = 5005,
                             suspend: Boolean = false,
                             test: Boolean = false,
                             defaultOptions: Seq[String] = IntellijVMOptions.DEFAULT_STATIC_OPTS)

object IntellijVMOptions {

  implicit class VMOptionOps(val options: IntellijVMOptions) extends AnyVal {
    import options._

    private def build(quoteValues: Boolean): Seq[String] = {
      def OQ(str: String): String = if (quoteValues) str.xmlQuote else str
      val buffer = new mutable.ArrayBuffer[String]()
      buffer ++= defaultOptions
      buffer +=  s"-Xms${xms}m"
      buffer +=  s"-Xmx${xmx}m"
      buffer +=  s"-XX:ReservedCodeCacheSize=${reservedCodeCacheSize}m"
      buffer +=  s"-XX:SoftRefLRUPolicyMSPerMB=$softRefLRUPolicyMSPerMB"
      buffer +=  gc
      buffer +=  gcOpt
      val (system, config) =
        if (test) (ideaHome.resolve("test-system"), ideaHome.resolve("test-config"))
        else      (ideaHome.resolve("system"), ideaHome.resolve("config"))
      buffer += s"-Didea.system.path=${OQ(system.toString)}"
      buffer += s"-Didea.config.path=${OQ(config.toString)}"
      buffer += s"-Dplugin.path=${OQ(pluginPath.toString)}"
      if(test) {
        buffer += "-Didea.use.core.classloader.for.plugin.path=true"
        buffer += "-Didea.force.use.core.classloader=true"
      }
      if (noPCE)
        buffer += "-Didea.ProcessCanceledException=disabled"
      if (!test)
        buffer += "-Didea.is.internal=true"
      if (debug) {
        val suspendValue = if (suspend) "y" else "n"
        buffer += s"-agentlib:jdwp=transport=dt_socket,server=y,suspend=$suspendValue,address=$debugPort"
      }
      if (platform.platformPrefix.nonEmpty)
        buffer += s"-Didea.platform.prefix=${platform.platformPrefix}"
      buffer
    }

    def add(opts: Seq[String]): IntellijVMOptions = copy(defaultOptions = defaultOptions ++ opts)
    def add(opt: String): IntellijVMOptions = copy(defaultOptions = defaultOptions :+ opt)

    def asSeq(quoteValues: Boolean = false): Seq[String] = build(quoteValues).filter(_.nonEmpty)
    def asJava(quoteValues: Boolean = false): java.util.List[String] = asSeq(quoteValues).asJava
  }

  val IDEA_MAIN = "com.intellij.idea.Main"

  /**
    * @note -Dsun.io.useCanonCaches & -Dsun.io.useCanonPrefixCache are disabled by default in JDK 17,
    *       but we still explicitly pass `false` just in case (e.g. if JBR 11 is used)
    */
  val DEFAULT_STATIC_OPTS: Seq[String] =
    """-Dsun.io.useCanonCaches=false
      |-Dsun.io.useCanonPrefixCache=false
      |-ea
      |-Djava.net.preferIPv4Stack=true
      |-XX:+HeapDumpOnOutOfMemoryError
      |-XX:-OmitStackTraceInFastThrow
      |-Dawt.useSystemAAFontSettings=lcd
      |-Dsun.java2d.renderer=sun.java2d.marlin.MarlinRenderingEngine
      |-Dsun.tools.attach.tmp.only=true
      |-Dide.no.platform.update=true
      |-Dkotlinx.coroutines.debug=off
      |-Djdk.attach.allowAttachSelf=true
      |-XX:MaxJavaStackTraceDepth=10000
      |-Didea.debug.mode=true
      |-Dapple.laf.useScreenMenuBar=true
      |-Duse.linux.keychain=false
      |-Didea.initially.ask.config=true
      |
      |-Djdk.module.illegalAccess.silent=true
      |-XX:+IgnoreUnrecognizedVMOptions
      |--jbr-illegal-access
      |""".stripMargin.split("\n").filter(_.nonEmpty).toSeq
}
