package org.jetbrains.sbtidea.productInfo

import org.jetbrains.annotations.TestOnly
import org.jetbrains.sbtidea.PluginLogger as log
import spray.json.DefaultJsonProtocol.*

import java.io.File

object ProductInfoParser {

  import spray.json.*

  /**
   * @param file `product-info.json`
   */
  def parse(file: File): ProductInfo = {
    val source = scala.io.Source.fromFile(file)
    val lines = try source.getLines.mkString("\n") finally source.close()
    val jsonAst = lines.parseJson
    jsonAst.convertTo[ProductInfo]
  }

  @TestOnly
  def toJsonString(productInfo: ProductInfo): String =
    productInfo.toJson(productInfoFormat).prettyPrint

  private implicit def productInfoFormat: RootJsonFormat[ProductInfo] = jsonFormat8(ProductInfo)
  private implicit def launchFormat: JsonFormat[Launch] = jsonFormat8(Launch)
  private implicit def layoutItemFormat: RootJsonFormat[LayoutItem] = jsonFormat3(LayoutItem)

  private implicit def layoutItemKindFormat: JsonFormat[LayoutItemKind] = new JsonFormat[LayoutItemKind] {
    override def read(json: JsValue): LayoutItemKind = json match {
      case JsString(value) => value match {
        case "moduleV2" => LayoutItemKind.ModuleV2
        case "plugin" => LayoutItemKind.Plugin
        case "pluginAlias" => LayoutItemKind.PluginAlias
        case "productModuleV2" => LayoutItemKind.ProductModuleV2
        case value =>
          log.warn(s"Unknown layout item kind: $value")
          //use special "Unknown" case class to be more fail-tolerant
          LayoutItemKind.Unknown(value)
      }
      case _ =>
        deserializationError("LayoutItemKind expected")
    }

    override def write(obj: LayoutItemKind): JsValue = JsString(obj.toString)
  }


  private implicit def osFormat: JsonFormat[OS] = new JsonFormat[OS] {
    override def read(json: JsValue): OS = json match {
      case JsString(value) => value.toLowerCase match {
        case "windows" => OS.Windows
        case "macos" => OS.macOs
        case "linux" => OS.Linux
        case _ =>
          throw new RuntimeException(s"Unknown OS: $value")
      }
      case _ =>
        deserializationError("OS expected")
    }

    override def write(obj: OS): JsValue = JsString(obj.toString)
  }
}
