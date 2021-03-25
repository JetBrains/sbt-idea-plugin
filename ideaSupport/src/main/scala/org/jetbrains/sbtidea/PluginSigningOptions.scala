package org.jetbrains.sbtidea

import java.io.File

case class PluginSigningOptions(enabled: Boolean,
                                  certFile: Option[File],
                                  privateKeyFile: Option[File],
                                  keyPassphrase: Option[String])
