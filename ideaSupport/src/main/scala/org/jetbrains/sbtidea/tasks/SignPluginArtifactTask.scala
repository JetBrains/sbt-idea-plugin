package org.jetbrains.sbtidea.tasks

import org.jetbrains.sbtidea.Keys.signPluginOptions
import org.jetbrains.sbtidea.PluginSigningOptions
import org.jetbrains.sbtidea.packaging.PackagingKeys._
import org.jetbrains.zip.signer.signer.{PublicKeyUtils, SignerInfoLoader}
import org.jetbrains.zip.signer.signing.{DefaultSignatureProvider, ZipSigner}
import sbt._

import java.io.File
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.util

object SignPluginArtifactTask extends SbtIdeaTask[File] {

  def defaultSignOptions: PluginSigningOptions =
    PluginSigningOptions(enabled = false, None, None, None)

  override def createTask = sbt.Def.task {
    val opts = getSigningOptions(signPluginOptions.value)
    val inputFile = packageArtifactZip.value
    val outputFile = inputFile.getParentFile / s"signed-${inputFile.name}"

    doSignPlugin(opts, inputFile, outputFile)
  }

  private[tasks] def doSignPlugin(opts: PluginSigningOptions, inputFile: File, outputFile: File): File = {
    def sign(certs: util.List[X509Certificate], key: PrivateKey): File = {
      val signatureProvider = new DefaultSignatureProvider(
        PublicKeyUtils.INSTANCE.getSuggestedSignatureAlgorithm(certs.get(0).getPublicKey),
        key)
      ZipSigner.sign(inputFile, outputFile, certs, signatureProvider)
      outputFile
    }
    opts match {
      case PluginSigningOptions(true, Some(certFile), Some(privateKeyFile), Some(keyPassphrase)) =>
        val info = SignerInfoLoader.INSTANCE.loadSignerInfoFromFiles(privateKeyFile, certFile, keyPassphrase.toCharArray)
        sign(info.component1(), info.component2())
      case PluginSigningOptions(true, Some(certFile), Some(privateKeyFile), None) =>
        val info = SignerInfoLoader.INSTANCE.loadSignerInfoFromFiles(privateKeyFile, certFile)
        sign(info.component1(), info.component2())
      case PluginSigningOptions(true, None, Some(_), _) =>
        throw new IllegalArgumentException("Certificate chain file doesn't exist")
      case PluginSigningOptions(true, Some(_), None, _) =>
        throw new IllegalArgumentException("Private key file doesn't exist")
      case PluginSigningOptions(false, _, _, _) =>
        throw new IllegalStateException("Plugin signing disabled in options")
    }

  }

  private def envOrError(name: String, optional: Boolean = false): String =
    sys.env.getOrElse(name,
      { if (optional) null else throw new IllegalArgumentException(s"Required env $name was not provided")})

  /**
    * Fills in missing signing options from env vars
    */
  private def getSigningOptions(optsFromSbt: PluginSigningOptions): PluginSigningOptions = {
    val actualCert = optsFromSbt.certFile.orElse(Some(new File(envOrError(PLUGIN_SIGN_CERT)))).filter(_.exists())
    val actualKey  = optsFromSbt.privateKeyFile.orElse(Some(new File(envOrError(PLUGIN_SIGN_KEY)))).filter(_.exists())
    val actualPass = optsFromSbt.keyPassphrase.orElse(Option(envOrError(PLUGIN_SIGN_KEY_PWD, optional = true)))
    optsFromSbt.copy(certFile = actualCert, privateKeyFile = actualKey, keyPassphrase = actualPass)
  }

  private final val PLUGIN_SIGN_KEY       = "PLUGIN_SIGN_KEY"
  private final val PLUGIN_SIGN_KEY_PWD   = "PLUGIN_SIGN_KEY_PWD"
  private final val PLUGIN_SIGN_CERT      = "PLUGIN_SIGN_CERT"
}
