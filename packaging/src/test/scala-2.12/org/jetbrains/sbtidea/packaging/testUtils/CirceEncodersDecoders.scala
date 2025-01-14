package org.jetbrains.sbtidea.packaging.testUtils

import io.circe.syntax.*
import org.jetbrains.sbtidea.packaging.structure.sbtImpl.{SbtPackagedProjectNodeImpl, SbtProjectPackagingOptionsImpl}
import org.jetbrains.sbtidea.packaging.structure.{PackagedProjectNode, ProjectPackagingOptions}
import org.jetbrains.sbtidea.packaging.{ExcludeFilter, MAPPING_KIND, Mapping, MappingMetaData, ShadePattern}
import org.jetbrains.sbtidea.structure.sbtImpl.{ModuleKeyImpl, SbtIvyLibrary}
import org.jetbrains.sbtidea.structure.{Library, ModuleKey}
import sbt.ProjectRef
import sbt.librarymanagement.ModuleID

import java.io.File

object CirceEncodersDecoders {

  import io.circe.*
  import io.circe.generic.semiauto.*

  implicit val fileEncoder: Encoder[File] = Encoder.instance { file =>
    Json.fromString(file.getPath)
  }
  implicit val fileDecoder: Decoder[File] = Decoder.instance { cursor =>
    cursor.as[String].map(new File(_))
  }

  implicit val mappingEncoder: Encoder[Mapping] = deriveEncoder[Mapping]
  implicit val mappingDecoder: Decoder[Mapping] = deriveDecoder[Mapping]

  implicit val mappingMetaDataEncoder: Encoder[MappingMetaData] = deriveEncoder[MappingMetaData]
  implicit val mappingMetaDataDecoder: Decoder[MappingMetaData] = deriveDecoder[MappingMetaData]

  implicit val shadePatternEncoder: Encoder[ShadePattern] = deriveEncoder[ShadePattern]
  implicit val shadePatternDecoder: Decoder[ShadePattern] = deriveDecoder[ShadePattern]

  implicit val libraryEncoder: Encoder[Library] = Encoder.instance { lib => sbtLibraryEncoder(lib.asInstanceOf[SbtIvyLibrary]) }
  implicit val libraryDecoder: Decoder[Library] = Decoder.instance { cursor => sbtLibraryDecoder(cursor) }
  
  implicit val sbtLibraryEncoder: Encoder[SbtIvyLibrary] = deriveEncoder[SbtIvyLibrary]
  implicit val sbtLibraryDecoder: Decoder[SbtIvyLibrary] =
    Decoder.instance { cursor =>
      for {
        key <- cursor.downField("key").as[ModuleKey]
        jarFilesOpt <- cursor.downField("jarFiles").as[Option[Seq[File]]]
        sbtLibrary <- jarFilesOpt match {
          case Some(jarFiles) => Right(SbtIvyLibrary(key, jarFiles))
          case None =>
            // Handle old format of SbtIvyLibrary when the field was just a single jar
            // We need to make updating of test data more convenient
            cursor.downField("jarFile").as[File].map(jarFile => SbtIvyLibrary(key, Seq(jarFile)))
        }
      } yield sbtLibrary
    }

  implicit val mappingKindEncoder: Encoder[MAPPING_KIND.Value] = Encoder.encodeEnumeration(MAPPING_KIND)
  implicit val mappingKindDecoder: Decoder[MAPPING_KIND.Value] = Decoder.decodeEnumeration(MAPPING_KIND)

  implicit val moduleKeyEncoder: Encoder[ModuleKey] = Encoder.instance {
    case impl: ModuleKeyImpl => moduleKeyImplEncoder(impl)
    case _ => throw new IllegalArgumentException("Unsupported ModuleKey implementation for encoding")
  }

  implicit val moduleKeyDecoder: Decoder[ModuleKey] = Decoder.instance { cursor =>
    moduleKeyImplDecoder.tryDecode(cursor)
  }

  implicit val moduleKeyImplEncoder: Encoder[ModuleKeyImpl] = deriveEncoder[ModuleKeyImpl]
  implicit val moduleKeyImplDecoder: Decoder[ModuleKeyImpl] = deriveDecoder[ModuleKeyImpl]
  
  implicit val moduleIDEncoder: Encoder[ModuleID] = Encoder.instance { moduleID =>
    Json.obj(
      "organization" -> Json.fromString(moduleID.organization),
      "name" -> Json.fromString(moduleID.name),
      "revision" -> Json.fromString(moduleID.revision)
    )
  }

  implicit val moduleIDDecoder: Decoder[ModuleID] = Decoder.instance { cursor =>
    for {
      organization <- cursor.downField("organization").as[String]
      name <- cursor.downField("name").as[String]
      revision <- cursor.downField("revision").as[String]
    } yield ModuleID(organization, name, revision)
  }
  
  implicit val excludeFilterEncoder: Encoder[ExcludeFilter] = Encoder.instance {
    case ExcludeFilter.AllPassExcludeFilter =>
      Json.obj(
        "type" -> Json.fromString("AllPass")
      )
    case merged: ExcludeFilter.MergedExcludeFilter =>
      Json.obj(
        "type" -> Json.fromString("Merged"),
        "filters" -> merged.filters.asJson
      )
    case null => Json.Null
    case _ =>
      throw new IllegalArgumentException("Unsupported ExcludeFilter implementation for encoding")
  }

  implicit val excludeFilterDecoder: Decoder[ExcludeFilter] = Decoder.instance { cursor =>
    cursor.value match {
      case Json.Null =>
        Right(null)
      case _ =>
        cursor.downField("type").as[String].flatMap {
          case "AllPass" =>
            Right(ExcludeFilter.AllPass)
          case "Merged" =>
            cursor.downField("filters").as[Iterable[ExcludeFilter]].map { filters =>
              ExcludeFilter.merge(filters)
            }
          case unknown =>
            Left(DecodingFailure(s"Unknown ExcludeFilter type: $unknown", cursor.history))
        }
    }
  }
  
  implicit lazy val projectRefEncoder: Encoder.AsObject[ProjectRef] = deriveEncoder[sbt.ProjectRef]
  implicit lazy val projectRefDecoder: Decoder[ProjectRef] = deriveDecoder[sbt.ProjectRef]

  import org.jetbrains.sbtidea.packaging.structure.PackagingMethod as SPackagingMethod

  implicit val packagingMethodEncoder: Encoder[SPackagingMethod] = Encoder.instance {
    case SPackagingMethod.Skip() =>
      Json.obj("type" -> Json.fromString("Skip"))
    case SPackagingMethod.MergeIntoParent() =>
      Json.obj("type" -> Json.fromString("MergeIntoParent"))
    case SPackagingMethod.DepsOnly(targetPath) =>
      Json.obj(
        "type" -> Json.fromString("DepsOnly"),
        "targetPath" -> Json.fromString(targetPath)
      )
    case SPackagingMethod.MergeIntoOther(project) =>
      ???
//      Json.obj(
//        "type" -> Json.fromString("MergeIntoOther"),
//        "project" -> project.asJson
//      )
    case SPackagingMethod.Standalone(targetPath, static) =>
      Json.obj(
        "type" -> Json.fromString("Standalone"),
        "targetPath" -> Json.fromString(targetPath),
        "static" -> Json.fromBoolean(static)
      )
    case SPackagingMethod.PluginModule(moduleName, static) =>
      Json.obj(
        "type" -> Json.fromString("PluginModule"),
        "moduleName" -> Json.fromString(moduleName),
        "static" -> Json.fromBoolean(static)
      )
  }

  implicit val packagingMethodDecoder: Decoder[SPackagingMethod] = Decoder.instance { cursor =>
    cursor.downField("type").as[String].flatMap {
      case "Skip" =>
        Right(SPackagingMethod.Skip())
      case "MergeIntoParent" =>
        Right(SPackagingMethod.MergeIntoParent())
      case "DepsOnly" =>
        for {
          targetPath <- cursor.downField("targetPath").as[String]
        } yield SPackagingMethod.DepsOnly(targetPath)
      case "MergeIntoOther" =>
        ???
//        for {
//          project <- cursor.downField("project").as[PackagedProjectNode]
//        } yield SPackagingMethod.MergeIntoOther(project)
      case "Standalone" =>
        for {
          targetPath <- cursor.downField("targetPath").as[String]
          static <- cursor.downField("static").as[Boolean]
        } yield SPackagingMethod.Standalone(targetPath, static)
      case "PluginModule" =>
        for {
          moduleName <- cursor.downField("moduleName").as[String]
          static <- cursor.downField("static").as[Boolean]
        } yield SPackagingMethod.PluginModule(moduleName, static)
      case unknown =>
        Left(DecodingFailure(s"Unknown PackagingMethod type: $unknown", cursor.history))
    }
  }

  implicit val sbtProjectPackagingOptionsEncoder: Encoder[ProjectPackagingOptions] = Encoder.instance { options =>
    sbtProjectPackagingOptionsImplEncoder(options.asInstanceOf[SbtProjectPackagingOptionsImpl])
  }

  implicit val sbtProjectPackagingOptionsDecoder: Decoder[ProjectPackagingOptions] = Decoder.instance { cursor =>
    sbtProjectPackagingOptionsImplDecoder(cursor).map(_.asInstanceOf[ProjectPackagingOptions])
  }

  implicit val sbtProjectPackagingOptionsImplEncoder: Encoder[SbtProjectPackagingOptionsImpl] = Encoder.instance { options =>
    Json.obj(
      "packageMethod" -> options.packageMethod.asJson,
      "libraryMappings" -> options.libraryMappings.asJson,
      "libraryBaseDir" -> options.libraryBaseDir.asJson,
      "fileMappings" -> options.fileMappings.asJson,
      "shadePatterns" -> options.shadePatterns.asJson,
      "excludeFilter" -> options.excludeFilter.asJson,
      "classRoots" -> options.classRoots.asJson,
      "assembleLibraries" -> options.assembleLibraries.asJson,
      "additionalProjects" -> options.additionalProjects.asJson
    )
  }

  implicit val sbtProjectPackagingOptionsImplDecoder: Decoder[SbtProjectPackagingOptionsImpl] = Decoder.instance { cursor =>
    for {
      packageMethod <- cursor.downField("packageMethod").as[SPackagingMethod]
      libraryMappings <- cursor.downField("libraryMappings").as[Seq[(ModuleKey, Option[String])]]
      libraryBaseDir <- cursor.downField("libraryBaseDir").as[File]
      fileMappings <- cursor.downField("fileMappings").as[Seq[(File, String)]]
      shadePatterns <- cursor.downField("shadePatterns").as[Seq[ShadePattern]]
      excludeFilter <- cursor.downField("excludeFilter").as[ExcludeFilter]
      classRoots <- cursor.downField("classRoots").as[Seq[File]]
      assembleLibraries <- cursor.downField("assembleLibraries").as[Boolean]
      additionalProjects <- cursor.downField("additionalProjects").as[Seq[PackagedProjectNode]]
    } yield SbtProjectPackagingOptionsImpl(
      packageMethod = packageMethod,
      libraryMappings = libraryMappings,
      libraryBaseDir = libraryBaseDir,
      fileMappings = fileMappings,
      shadePatterns = shadePatterns,
      excludeFilter = excludeFilter,
      classRoots = classRoots,
      assembleLibraries = assembleLibraries,
      additionalProjects = additionalProjects
    )
  }

  implicit lazy val packagedProjectNodeSeqEncoder: Encoder[Seq[PackagedProjectNode]] = Encoder.instance { nodes =>
    nodeGraphImplEncoder(nodes.map(_.asInstanceOf[SbtPackagedProjectNodeImpl]))
  }
  implicit lazy val packagedProjectNodeSeqDecoder: Decoder[Seq[PackagedProjectNode]] = Decoder.instance { cursor =>
    nodeGraphImplDecoder(cursor).map(_.map(_.asInstanceOf[PackagedProjectNode]))
  }

  implicit lazy val nodeGraphImplEncoder: Encoder[Seq[SbtPackagedProjectNodeImpl]] = Encoder.instance { nodes =>
    val nodeNames = nodes.map(_.name)
    require(nodeNames.distinct.size == nodeNames.size, "Node names must be unique")

    val parentsMapping = nodes.map(node => node.name -> node.parents.map(_.name)).toMap
    val childrenMapping = nodes.map(node => node.name -> node.children.map(_.name)).toMap
    val encodedNodes = nodes.map(node => Json.obj(
      "ref" -> node.ref.asJson,
      "name" -> node.name.asJson,
      "rootProjectName" -> node.rootProjectName.asJson,
      "libs" -> node.libs.asJson,
      "packagingOptions" -> node.packagingOptions.asJson
    ))

    Json.obj(
      "nodes" -> encodedNodes.asJson,
      "parentsMapping" -> parentsMapping.asJson,
      "childrenMapping" -> childrenMapping.asJson
    )
  }

  implicit lazy val nodeGraphImplDecoder: Decoder[Seq[SbtPackagedProjectNodeImpl]] = Decoder.instance { cursor =>
    for {
      nodes <- cursor.downField("nodes").as[Seq[Json]]
      parentsMapping <- cursor.downField("parentsMapping").as[Map[String, Seq[String]]]
      childrenMapping <- cursor.downField("childrenMapping").as[Map[String, Seq[String]]]
    } yield {
      val nodeByName: Map[String, SbtPackagedProjectNodeImpl] = nodes.map(json => {
        val ref = json.hcursor.downField("ref").as[ProjectRef].right.get
        val name = json.hcursor.downField("name").as[String].right.get
        val rootProjectName = json.hcursor.downField("rootProjectName").as[Option[String]].right.get
        val libs = json.hcursor.downField("libs").as[Seq[SbtIvyLibrary]].right.get
        val packagingOptions = json.hcursor.downField("packagingOptions").as[ProjectPackagingOptions].right.get
        
        name -> SbtPackagedProjectNodeImpl(
          ref = ref,
          name = name,
          rootProjectName = rootProjectName,
          libs = libs,
          packagingOptions = packagingOptions,
          parents = Seq(), // Temporarily empty, will be resolved later
          children = Seq() // Temporarily empty, will be resolved later
        )
      }).toMap

      // Reconnect parents and children using the mappings
      nodeByName.foreach { case (name, node) =>
        node.parents = parentsMapping.getOrElse(name, Seq()).flatMap(nodeByName.get)
        node.children = childrenMapping.getOrElse(name, Seq()).flatMap(nodeByName.get)
      }

      nodeByName.values.toSeq
    }
  }
}