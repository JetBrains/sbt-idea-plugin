package org.jetbrains.sbtidea.tasks

import org.bouncycastle.openssl.EncryptionException
import org.jetbrains.sbtidea.PluginSigningOptions
import org.jetbrains.sbtidea.download.plugin.{PluginDescriptor, PluginMock}
import org.jetbrains.zip.signer.verifier.{SuccessfulVerificationResult, ZipVerificationResult, ZipVerifier}
import org.scalatest.{FunSuite, Matchers}
import org.jetbrains.sbtidea.pathToPathExt
import sbt._

import java.nio.file.Files

class PluginSigningTest extends FunSuite with Matchers with PluginMock {

  test("sign with cert and key without password") {
    signAndVerify("sub_cert.key", "chain.pem") shouldBe a [SuccessfulVerificationResult]
  }

  test("sign with cert and key WITH password") {
    signAndVerify("root_ca.key", "root_ca.pem", Some("testpassword")) shouldBe a [SuccessfulVerificationResult]
  }

  test("sign should fail with a wrong password") {
    an [EncryptionException] should be thrownBy signAndVerify("root_ca.key", "root_ca.pem", Some("wrong"))
  }

  test("sign task should fail if signing is disabled in options") {
    an [IllegalStateException] should be thrownBy {
      SignPluginArtifactTask.doSignPlugin(SignPluginArtifactTask.defaultSignOptions, null, null)
    }
  }

  private def signAndVerify(keyName: String, certName: String, keyPassword: Option[String] = None): ZipVerificationResult = {
    val pluginMetadata = PluginDescriptor("org.intellij.scala", "JetBrains", "Scala", "2019.3.1", "193.0", "194.0")
    val pluginZip = createPluginZipMock(pluginMetadata)
    val targetZip = pluginZip.getParent.resolve("signed.zip")
    val keyPath   = pluginZip.getParent / "key"
    val certPath  = pluginZip.getParent / "cert"
    Files.copy(getClass.getResourceAsStream(keyName), keyPath)
    Files.copy(getClass.getResourceAsStream(certName), certPath)
    val opts = PluginSigningOptions(
        enabled = true,
        Some(certPath.toFile),
        Some(keyPath.toFile),
        keyPassword)
    val signedFile = SignPluginArtifactTask.doSignPlugin(opts, pluginZip.toFile, targetZip.toFile)
    ZipVerifier.INSTANCE.verify(signedFile)
  }
}
