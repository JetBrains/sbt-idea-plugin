package org.jetbrains.sbtidea.productInfo

import org.scalatest.funsuite.AnyFunSuiteLike
import org.scalatest.matchers.should.Matchers.convertToAnyShouldWrapper

import java.io.File

class ProductInfoParserTest extends AnyFunSuiteLike {
  test("parse product-info-242.10180.25.json") {
    val file = new File(this.getClass.getClassLoader.getResource("org/jetbrains/sbtidea/productInfo/product-info-242.10180.25.json").getPath)
    val productInfo = ProductInfoParser.parse(file)
    productInfo shouldBe ProductInfo(
      name = "IntelliJ IDEA",
      version = "2024.2",
      versionSuffix = Some("EAP"),
      buildNumber = "242.10180.25",
      productCode = "IU",
      modules = Seq(
        "com.intellij.modules.all",
        "com.intellij.modules.coverage",
      ),
      launch = List(
        Launch(
          OS.Linux,
          "amd64",
          "bin/idea.sh",
          None,
          "bin/linux/idea64.vmoptions",
          Some("jetbrains-idea"),
          List("platform-loader.jar", "util-8.jar", "util.jar", "app-client.jar", "util_rt.jar", "product.jar", "opentelemetry.jar", "app.jar", "product-client.jar", "lib-client.jar", "stats.jar", "jps-model.jar", "external-system-rt.jar", "rd.jar", "bouncy-castle.jar", "protobuf.jar", "intellij-test-discovery.jar", "forms_rt.jar", "lib.jar", "externalProcess-rt.jar", "groovy.jar", "annotations.jar", "idea_rt.jar", "intellij-coverage-agent-1.0.750.jar", "jsch-agent.jar", "junit4.jar", "nio-fs.jar", "trove.jar"),
          List("-Djava.system.class.loader=com.intellij.util.lang.PathClassLoader", "-Didea.vendor.name=JetBrains", "-Didea.paths.selector=IntelliJIdea2024.2", "-Djna.boot.library.path=$IDE_HOME/lib/jna/amd64", "-Dpty4j.preferred.native.folder=$IDE_HOME/lib/pty4j", "-Djna.nosys=true", "-Djna.noclasspath=true", "-Dintellij.platform.runtime.repository.path=$IDE_HOME/modules/module-descriptors.jar", "-Dsplash=true", "-Daether.connector.resumeDownloads=false", "--add-opens=java.base/java.io=ALL-UNNAMED", "--add-opens=java.base/java.lang=ALL-UNNAMED", "--add-opens=java.base/java.lang.ref=ALL-UNNAMED", "--add-opens=java.base/java.lang.reflect=ALL-UNNAMED", "--add-opens=java.base/java.net=ALL-UNNAMED", "--add-opens=java.base/java.nio=ALL-UNNAMED", "--add-opens=java.base/java.nio.charset=ALL-UNNAMED", "--add-opens=java.base/java.text=ALL-UNNAMED", "--add-opens=java.base/java.time=ALL-UNNAMED", "--add-opens=java.base/java.util=ALL-UNNAMED", "--add-opens=java.base/java.util.concurrent=ALL-UNNAMED", "--add-opens=java.base/java.util.concurrent.atomic=ALL-UNNAMED", "--add-opens=java.base/java.util.concurrent.locks=ALL-UNNAMED", "--add-opens=java.base/jdk.internal.vm=ALL-UNNAMED", "--add-opens=java.base/sun.net.dns=ALL-UNNAMED", "--add-opens=java.base/sun.nio.ch=ALL-UNNAMED", "--add-opens=java.base/sun.nio.fs=ALL-UNNAMED", "--add-opens=java.base/sun.security.ssl=ALL-UNNAMED", "--add-opens=java.base/sun.security.util=ALL-UNNAMED", "--add-opens=java.desktop/com.sun.java.swing=ALL-UNNAMED", "--add-opens=java.desktop/com.sun.java.swing.plaf.gtk=ALL-UNNAMED", "--add-opens=java.desktop/java.awt=ALL-UNNAMED", "--add-opens=java.desktop/java.awt.dnd.peer=ALL-UNNAMED", "--add-opens=java.desktop/java.awt.event=ALL-UNNAMED", "--add-opens=java.desktop/java.awt.font=ALL-UNNAMED", "--add-opens=java.desktop/java.awt.image=ALL-UNNAMED", "--add-opens=java.desktop/java.awt.peer=ALL-UNNAMED", "--add-opens=java.desktop/javax.swing=ALL-UNNAMED", "--add-opens=java.desktop/javax.swing.plaf.basic=ALL-UNNAMED", "--add-opens=java.desktop/javax.swing.text=ALL-UNNAMED", "--add-opens=java.desktop/javax.swing.text.html=ALL-UNNAMED", "--add-opens=java.desktop/sun.awt=ALL-UNNAMED", "--add-opens=java.desktop/sun.awt.X11=ALL-UNNAMED", "--add-opens=java.desktop/sun.awt.datatransfer=ALL-UNNAMED", "--add-opens=java.desktop/sun.awt.image=ALL-UNNAMED", "--add-opens=java.desktop/sun.font=ALL-UNNAMED", "--add-opens=java.desktop/sun.java2d=ALL-UNNAMED", "--add-opens=java.desktop/sun.swing=ALL-UNNAMED", "--add-opens=jdk.attach/sun.tools.attach=ALL-UNNAMED", "--add-opens=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED", "--add-opens=jdk.internal.jvmstat/sun.jvmstat.monitor=ALL-UNNAMED", "--add-opens=jdk.jdi/com.sun.tools.jdi=ALL-UNNAMED"),
        ),
        Launch(
          OS.macOs,
          "amd64",
          "bin/idea.sh",
          None,
          "bin/mac/idea.vmoptions",
          None,
          List("platform-loader.jar", "util-8.jar", "util.jar", "app-client.jar", "util_rt.jar", "product.jar", "opentelemetry.jar", "app.jar", "product-client.jar", "lib-client.jar", "stats.jar", "jps-model.jar", "external-system-rt.jar", "rd.jar", "bouncy-castle.jar", "protobuf.jar", "intellij-test-discovery.jar", "forms_rt.jar", "lib.jar", "externalProcess-rt.jar", "groovy.jar", "annotations.jar", "idea_rt.jar", "intellij-coverage-agent-1.0.750.jar", "jsch-agent.jar", "junit4.jar", "nio-fs.jar", "trove.jar"),
          List("-Djava.system.class.loader=com.intellij.util.lang.PathClassLoader", "-Didea.vendor.name=JetBrains", "-Didea.paths.selector=IntelliJIdea2024.2", "-Djna.boot.library.path=$APP_PACKAGE/lib/jna/amd64", "-Dpty4j.preferred.native.folder=$APP_PACKAGE/lib/pty4j", "-Djna.nosys=true", "-Djna.noclasspath=true", "-Dintellij.platform.runtime.repository.path=$APP_PACKAGE/modules/module-descriptors.jar", "-Dsplash=true", "-Daether.connector.resumeDownloads=false", "--add-opens=java.base/java.io=ALL-UNNAMED", "--add-opens=java.base/java.lang=ALL-UNNAMED", "--add-opens=java.base/java.lang.ref=ALL-UNNAMED", "--add-opens=java.base/java.lang.reflect=ALL-UNNAMED", "--add-opens=java.base/java.net=ALL-UNNAMED", "--add-opens=java.base/java.nio=ALL-UNNAMED", "--add-opens=java.base/java.nio.charset=ALL-UNNAMED", "--add-opens=java.base/java.text=ALL-UNNAMED", "--add-opens=java.base/java.time=ALL-UNNAMED", "--add-opens=java.base/java.util=ALL-UNNAMED", "--add-opens=java.base/java.util.concurrent=ALL-UNNAMED", "--add-opens=java.base/java.util.concurrent.atomic=ALL-UNNAMED", "--add-opens=java.base/java.util.concurrent.locks=ALL-UNNAMED", "--add-opens=java.base/jdk.internal.vm=ALL-UNNAMED", "--add-opens=java.base/sun.net.dns=ALL-UNNAMED", "--add-opens=java.base/sun.nio.ch=ALL-UNNAMED", "--add-opens=java.base/sun.nio.fs=ALL-UNNAMED", "--add-opens=java.base/sun.security.ssl=ALL-UNNAMED", "--add-opens=java.base/sun.security.util=ALL-UNNAMED", "--add-opens=java.desktop/com.apple.eawt=ALL-UNNAMED", "--add-opens=java.desktop/com.apple.eawt.event=ALL-UNNAMED", "--add-opens=java.desktop/com.apple.laf=ALL-UNNAMED", "--add-opens=java.desktop/com.sun.java.swing=ALL-UNNAMED", "--add-opens=java.desktop/java.awt=ALL-UNNAMED", "--add-opens=java.desktop/java.awt.dnd.peer=ALL-UNNAMED", "--add-opens=java.desktop/java.awt.event=ALL-UNNAMED", "--add-opens=java.desktop/java.awt.font=ALL-UNNAMED", "--add-opens=java.desktop/java.awt.image=ALL-UNNAMED", "--add-opens=java.desktop/java.awt.peer=ALL-UNNAMED", "--add-opens=java.desktop/javax.swing=ALL-UNNAMED", "--add-opens=java.desktop/javax.swing.plaf.basic=ALL-UNNAMED", "--add-opens=java.desktop/javax.swing.text=ALL-UNNAMED", "--add-opens=java.desktop/javax.swing.text.html=ALL-UNNAMED", "--add-opens=java.desktop/sun.awt=ALL-UNNAMED", "--add-opens=java.desktop/sun.awt.datatransfer=ALL-UNNAMED", "--add-opens=java.desktop/sun.awt.image=ALL-UNNAMED", "--add-opens=java.desktop/sun.font=ALL-UNNAMED", "--add-opens=java.desktop/sun.java2d=ALL-UNNAMED", "--add-opens=java.desktop/sun.lwawt=ALL-UNNAMED", "--add-opens=java.desktop/sun.lwawt.macosx=ALL-UNNAMED", "--add-opens=java.desktop/sun.swing=ALL-UNNAMED", "--add-opens=jdk.attach/sun.tools.attach=ALL-UNNAMED", "--add-opens=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED", "--add-opens=jdk.internal.jvmstat/sun.jvmstat.monitor=ALL-UNNAMED", "--add-opens=jdk.jdi/com.sun.tools.jdi=ALL-UNNAMED"),
        )
      ),
      layout = List(
        LayoutItem("ByteCodeViewer", LayoutItemKind.Plugin, Option(List("plugins/java-byteCodeViewer/lib/java-byteCodeViewer.jar"))),
        LayoutItem("com.intellij", LayoutItemKind.Plugin, Option(List("lib/platform-loader.jar", "lib/util-8.jar", "lib/util.jar", "lib/util_rt.jar", "lib/product.jar", "lib/opentelemetry.jar", "lib/app.jar", "lib/stats.jar", "lib/jps-model.jar", "lib/external-system-rt.jar", "lib/rd.jar", "lib/bouncy-castle.jar", "lib/protobuf.jar", "lib/intellij-test-discovery.jar", "lib/forms_rt.jar", "lib/lib.jar", "lib/externalProcess-rt.jar", "lib/groovy.jar", "lib/annotations.jar", "lib/idea_rt.jar", "lib/intellij-coverage-agent-1.0.750.jar", "lib/jsch-agent.jar", "lib/junit.jar", "lib/junit4.jar", "lib/nio-fs.jar", "lib/testFramework.jar", "lib/trove.jar"))),
        LayoutItem("training", LayoutItemKind.Plugin, Option(List("plugins/featuresTrainer/lib/featuresTrainer.jar"))),
        LayoutItem("kotlin.compiler-reference-index", LayoutItemKind.ModuleV2, None),
        LayoutItem("org.jetbrains.plugins.gradle.java", LayoutItemKind.PluginAlias, None),
        LayoutItem("intellij.execution.process.elevation", LayoutItemKind.ProductModuleV2, Some(Seq("lib/modules/intellij.execution.process.elevation.jar"))),
      )
    )
  }

  test("parse product-info-242.20224.300.json") {
    val file = new File(this.getClass.getClassLoader.getResource("org/jetbrains/sbtidea/productInfo/product-info-242.20224.300.json").getPath)
    val productInfo = ProductInfoParser.parse(file)
    productInfo shouldBe ProductInfo(
      name = "IntelliJ IDEA",
      version = "2024.2",
      versionSuffix = None,
      buildNumber = "242.20224.300",
      productCode = "IU",
      modules = Seq(
        "com.intellij.modules.all",
        "com.intellij.modules.coverage",
      ),
      launch = List(
        Launch(
          OS.Linux,
          "amd64",
          "bin/idea.sh",
          None,
          "bin/linux/idea64.vmoptions",
          Some("jetbrains-idea"),
          List("platform-loader.jar", "util-8.jar", "util.jar", "app-client.jar", "util_rt.jar", "product.jar", "opentelemetry.jar", "app.jar", "product-client.jar", "lib-client.jar", "stats.jar", "jps-model.jar", "external-system-rt.jar", "rd.jar", "bouncy-castle.jar", "protobuf.jar", "intellij-test-discovery.jar", "forms_rt.jar", "lib.jar", "externalProcess-rt.jar", "groovy.jar", "annotations.jar", "idea_rt.jar", "jsch-agent.jar", "junit4.jar", "nio-fs.jar", "trove.jar"),
          List("-Djava.system.class.loader=com.intellij.util.lang.PathClassLoader", "-Didea.vendor.name=JetBrains", "-Didea.paths.selector=IntelliJIdea2024.2", "-Djna.boot.library.path=$IDE_HOME/lib/jna/amd64", "-Dpty4j.preferred.native.folder=$IDE_HOME/lib/pty4j", "-Djna.nosys=true", "-Djna.noclasspath=true", "-Dintellij.platform.runtime.repository.path=$IDE_HOME/modules/module-descriptors.jar", "-Dsplash=true", "-Daether.connector.resumeDownloads=false", "--add-opens=java.base/java.io=ALL-UNNAMED", "--add-opens=java.base/java.lang=ALL-UNNAMED", "--add-opens=java.base/java.lang.ref=ALL-UNNAMED", "--add-opens=java.base/java.lang.reflect=ALL-UNNAMED", "--add-opens=java.base/java.net=ALL-UNNAMED", "--add-opens=java.base/java.nio=ALL-UNNAMED", "--add-opens=java.base/java.nio.charset=ALL-UNNAMED", "--add-opens=java.base/java.text=ALL-UNNAMED", "--add-opens=java.base/java.time=ALL-UNNAMED", "--add-opens=java.base/java.util=ALL-UNNAMED", "--add-opens=java.base/java.util.concurrent=ALL-UNNAMED", "--add-opens=java.base/java.util.concurrent.atomic=ALL-UNNAMED", "--add-opens=java.base/java.util.concurrent.locks=ALL-UNNAMED", "--add-opens=java.base/jdk.internal.vm=ALL-UNNAMED", "--add-opens=java.base/sun.net.dns=ALL-UNNAMED", "--add-opens=java.base/sun.nio.ch=ALL-UNNAMED", "--add-opens=java.base/sun.nio.fs=ALL-UNNAMED", "--add-opens=java.base/sun.security.ssl=ALL-UNNAMED", "--add-opens=java.base/sun.security.util=ALL-UNNAMED", "--add-opens=java.desktop/com.sun.java.swing=ALL-UNNAMED", "--add-opens=java.desktop/com.sun.java.swing.plaf.gtk=ALL-UNNAMED", "--add-opens=java.desktop/java.awt=ALL-UNNAMED", "--add-opens=java.desktop/java.awt.dnd.peer=ALL-UNNAMED", "--add-opens=java.desktop/java.awt.event=ALL-UNNAMED", "--add-opens=java.desktop/java.awt.font=ALL-UNNAMED", "--add-opens=java.desktop/java.awt.image=ALL-UNNAMED", "--add-opens=java.desktop/java.awt.peer=ALL-UNNAMED", "--add-opens=java.desktop/javax.swing=ALL-UNNAMED", "--add-opens=java.desktop/javax.swing.plaf.basic=ALL-UNNAMED", "--add-opens=java.desktop/javax.swing.text=ALL-UNNAMED", "--add-opens=java.desktop/javax.swing.text.html=ALL-UNNAMED", "--add-opens=java.desktop/sun.awt=ALL-UNNAMED", "--add-opens=java.desktop/sun.awt.X11=ALL-UNNAMED", "--add-opens=java.desktop/sun.awt.datatransfer=ALL-UNNAMED", "--add-opens=java.desktop/sun.awt.image=ALL-UNNAMED", "--add-opens=java.desktop/sun.font=ALL-UNNAMED", "--add-opens=java.desktop/sun.java2d=ALL-UNNAMED", "--add-opens=java.desktop/sun.swing=ALL-UNNAMED", "--add-opens=java.management/sun.management=ALL-UNNAMED", "--add-opens=jdk.attach/sun.tools.attach=ALL-UNNAMED", "--add-opens=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED", "--add-opens=jdk.internal.jvmstat/sun.jvmstat.monitor=ALL-UNNAMED", "--add-opens=jdk.jdi/com.sun.tools.jdi=ALL-UNNAMED"),
        ),
        Launch(
          OS.macOs,
          "amd64",
          "bin/idea.sh",
          None,
          "bin/mac/idea.vmoptions",
          None,
          List("platform-loader.jar", "util-8.jar", "util.jar", "app-client.jar", "util_rt.jar", "product.jar", "opentelemetry.jar", "app.jar", "product-client.jar", "lib-client.jar", "stats.jar", "jps-model.jar", "external-system-rt.jar", "rd.jar", "bouncy-castle.jar", "protobuf.jar", "intellij-test-discovery.jar", "forms_rt.jar", "lib.jar", "externalProcess-rt.jar", "groovy.jar", "annotations.jar", "idea_rt.jar", "jsch-agent.jar", "junit4.jar", "nio-fs.jar", "trove.jar"),
          List("-Djava.system.class.loader=com.intellij.util.lang.PathClassLoader", "-Didea.vendor.name=JetBrains", "-Didea.paths.selector=IntelliJIdea2024.2", "-Djna.boot.library.path=$APP_PACKAGE/lib/jna/amd64", "-Dpty4j.preferred.native.folder=$APP_PACKAGE/lib/pty4j", "-Djna.nosys=true", "-Djna.noclasspath=true", "-Dintellij.platform.runtime.repository.path=$APP_PACKAGE/modules/module-descriptors.jar", "-Dsplash=true", "-Daether.connector.resumeDownloads=false", "--add-opens=java.base/java.io=ALL-UNNAMED", "--add-opens=java.base/java.lang=ALL-UNNAMED", "--add-opens=java.base/java.lang.ref=ALL-UNNAMED", "--add-opens=java.base/java.lang.reflect=ALL-UNNAMED", "--add-opens=java.base/java.net=ALL-UNNAMED", "--add-opens=java.base/java.nio=ALL-UNNAMED", "--add-opens=java.base/java.nio.charset=ALL-UNNAMED", "--add-opens=java.base/java.text=ALL-UNNAMED", "--add-opens=java.base/java.time=ALL-UNNAMED", "--add-opens=java.base/java.util=ALL-UNNAMED", "--add-opens=java.base/java.util.concurrent=ALL-UNNAMED", "--add-opens=java.base/java.util.concurrent.atomic=ALL-UNNAMED", "--add-opens=java.base/java.util.concurrent.locks=ALL-UNNAMED", "--add-opens=java.base/jdk.internal.vm=ALL-UNNAMED", "--add-opens=java.base/sun.net.dns=ALL-UNNAMED", "--add-opens=java.base/sun.nio.ch=ALL-UNNAMED", "--add-opens=java.base/sun.nio.fs=ALL-UNNAMED", "--add-opens=java.base/sun.security.ssl=ALL-UNNAMED", "--add-opens=java.base/sun.security.util=ALL-UNNAMED", "--add-opens=java.desktop/com.apple.eawt=ALL-UNNAMED", "--add-opens=java.desktop/com.apple.eawt.event=ALL-UNNAMED", "--add-opens=java.desktop/com.apple.laf=ALL-UNNAMED", "--add-opens=java.desktop/com.sun.java.swing=ALL-UNNAMED", "--add-opens=java.desktop/java.awt=ALL-UNNAMED", "--add-opens=java.desktop/java.awt.dnd.peer=ALL-UNNAMED", "--add-opens=java.desktop/java.awt.event=ALL-UNNAMED", "--add-opens=java.desktop/java.awt.font=ALL-UNNAMED", "--add-opens=java.desktop/java.awt.image=ALL-UNNAMED", "--add-opens=java.desktop/java.awt.peer=ALL-UNNAMED", "--add-opens=java.desktop/javax.swing=ALL-UNNAMED", "--add-opens=java.desktop/javax.swing.plaf.basic=ALL-UNNAMED", "--add-opens=java.desktop/javax.swing.text=ALL-UNNAMED", "--add-opens=java.desktop/javax.swing.text.html=ALL-UNNAMED", "--add-opens=java.desktop/sun.awt=ALL-UNNAMED", "--add-opens=java.desktop/sun.awt.datatransfer=ALL-UNNAMED", "--add-opens=java.desktop/sun.awt.image=ALL-UNNAMED", "--add-opens=java.desktop/sun.font=ALL-UNNAMED", "--add-opens=java.desktop/sun.java2d=ALL-UNNAMED", "--add-opens=java.desktop/sun.lwawt=ALL-UNNAMED", "--add-opens=java.desktop/sun.lwawt.macosx=ALL-UNNAMED", "--add-opens=java.desktop/sun.swing=ALL-UNNAMED", "--add-opens=java.management/sun.management=ALL-UNNAMED", "--add-opens=jdk.attach/sun.tools.attach=ALL-UNNAMED", "--add-opens=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED", "--add-opens=jdk.internal.jvmstat/sun.jvmstat.monitor=ALL-UNNAMED", "--add-opens=jdk.jdi/com.sun.tools.jdi=ALL-UNNAMED"),
        )
      ),
      layout = List(
        LayoutItem("ByteCodeViewer", LayoutItemKind.Plugin, Option(List("plugins/java-byteCodeViewer/lib/java-byteCodeViewer.jar"))),
        LayoutItem("com.intellij", LayoutItemKind.Plugin, Option(List("lib/platform-loader.jar", "lib/util-8.jar", "lib/util.jar", "lib/app-client.jar", "lib/util_rt.jar", "lib/product.jar", "lib/opentelemetry.jar", "lib/app.jar", "lib/product-client.jar", "lib/lib-client.jar", "lib/stats.jar", "lib/jps-model.jar", "lib/external-system-rt.jar", "lib/rd.jar", "lib/bouncy-castle.jar", "lib/protobuf.jar", "lib/intellij-test-discovery.jar", "lib/forms_rt.jar", "lib/lib.jar", "lib/externalProcess-rt.jar", "lib/groovy.jar", "lib/annotations.jar", "lib/idea_rt.jar", "lib/jsch-agent.jar", "lib/junit4.jar", "lib/nio-fs.jar", "lib/testFramework.jar", "lib/trove.jar"))),
        LayoutItem("intellij.execution.process.elevation", LayoutItemKind.ProductModuleV2, Some(Seq("lib/modules/intellij.execution.process.elevation.jar"))),
        LayoutItem("kotlin.compiler-reference-index", LayoutItemKind.ModuleV2, None),
        LayoutItem("org.jetbrains.plugins.gradle.java", LayoutItemKind.PluginAlias, None),
        LayoutItem("training", LayoutItemKind.Plugin, Option(List("plugins/featuresTrainer/lib/featuresTrainer.jar"))),
      )
    )
  }
}
