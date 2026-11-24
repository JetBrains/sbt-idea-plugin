# sbt-idea-plugin

[![Version](https://index.scala-lang.org/jetbrains/sbt-idea-plugin/sbt-idea-plugin/latest.svg)](https://index.scala-lang.org/jetbrains/sbt-idea-plugin/sbt-idea-plugin)
![Build Status](https://github.com/jetbrains/sbt-idea-plugin/actions/workflows/scala.yml/badge.svg)
[![JetBrains team project](http://jb.gg/badges/team.svg)](https://confluence.jetbrains.com/display/ALL/JetBrains+on+GitHub)
[![Discord](https://badgen.net/badge/icon/discord?icon=discord&label)](https://discord.gg/aUKpZzeHCK)
[![Gitter](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/JetBrains/intellij-scala)

SBT plugin that makes development of IntelliJ Platform plugins in Scala easier by providing features such as:

- Downloading and attaching IntelliJ Platform binaries
- Setting up the environment for running tests
- Flexible way to define plugin artifact structure
- Publishing the plugin to JetBrains plugin repository

Design decisions and their rationale are documented in [Architecture Decision Records](docs/adr/README.md).

For a comprehensive usage example see [Scala plugin](https://github.com/JetBrains/intellij-scala) or 
[HOCON plugin](https://github.com/JetBrains/intellij-hocon/) build definition.

A complete list of public IJ plugins implemented in Scala/SBT can be found on [IntelliJ Platform Explorer](https://plugins.jetbrains.com/intellij-platform-explorer/?buildSystem=sbt)

Note that some features of this plugin may be used independently, i.e. if you only want to [print project structure](#printprojectgraph--taskkeyunit)
or [package artifacts](#packaging) you can depend on:\
`"org.jetbrains.scala" % "sbt-declarative-visualizer" % "LATEST_VERSION"` or \
`"org.jetbrains.scala" % "sbt-declarative-packaging" % "LATEST_VERSION"`

Note, in versions 4.x, and before you had to use group `org.jetbrains` instead of `org.jetbrains.scala`:\
`"org.jetbrains" % "sbt-declarative-visualizer" % "LATEST_VERSION"` or \
`"org.jetbrains" % "sbt-declarative-packaging" % "LATEST_VERSION"`

Please see the [Known Issues](#known-issues-and-limitations) section if you come across a problem, and feel free
file a bug on the [Issues](https://github.com/JetBrains/sbt-idea-plugin/issues) page of this repo if you find one.

## Quickstart: IJ Plugin Template Project

To quickly create a Scala based IJ Plugin we provide a template project. Create your own repo on GitHub from the [ JetBrains / **sbt-idea-example** ](https://github.com/JetBrains/sbt-idea-example) template by clicking the green `Use this template` button.
   Clone the sources and open the `build.sbt` via `File | Open` menu in IDEA by choosing `Open as a project`.

## Manual Installation (adding to an already existing sbt build)

From version 1.0.0, this plugin is published for sbt 0.13 and 1.0.
From version 3.17.0, this plugin is published for sbt 1.0 only.
From version 5.0.0 this plugin is published in a new group `org.jetbrains.scala`

* Insert into `project/plugins.sbt`:
```scala
addSbtPlugin("org.jetbrains.scala" % "sbt-idea-plugin" % "LATEST_VERSION")
```

Note, in versions 4.x, and before you had to use group `org.jetbrains` instead of `org.jetbrains.scala`:
```scala
addSbtPlugin("org.jetbrains" % "sbt-idea-plugin" % "LATEST_VERSION")
```

* [Enable](#auto-enable-the-plugin) the plugin for your desired projects (your main plugin project and all its dependencies)

* Run SBT and the plugin will automatically download and attach IntelliJ Platform dependencies.

* Start coding

## SBT Related Settings and Tasks

### IntelliJ Platform and Plugin

#### `intellijPluginName in ThisBuild :: SettingKey[String]`

**Default**: `name.in(LocalRootProject).value`

Name of your plugin. Better set this beforehand since several other settings such as
IntelliJ Platform directories and artifact names depend on it. Please see [name troubleshooting](#name-key-in-projects)
for more info.

#### `intellijBuild in ThisBuild :: SettingKey[String]`

**Default**: `LATEST-EAP-SNAPSHOT`

Selected IDE's build number. Binaries and sources of this build will be downloaded from the
[repository](https://www.jetbrains.org/intellij/sdk/docs/reference_guide/intellij_artifacts.html) and used in 
compilation and testing. You can find build number of your IntelliJ product in `Help -> About` dialog. However, it might be 
incomplete, so it is strongly recommended to verify it against 
[available releases](https://www.jetbrains.com/intellij-repository/releases) and
[available snapshots](https://www.jetbrains.com/intellij-repository/snapshots).

**Note**: minimum supported major IDEA version: `242.x` (~`2024.2.x`)

#### `intellijPlatform in ThisBuild :: SettingKey[IntelliJPlatform]`

**Default**: `IntelliJPlatform.Idea`

Edition of IntelliJ IDE to use in project. Currently available options are:

- Idea
- PyCharm
- CLion
- MPS

Deprecated compatibility options:

- IdeaCommunity
- IdeaUltimate
- PyCharmCommunity
- PyCharmProfessional

#### `intellijPlugins :: SettingKey[IdeaPlugin]`

**Default**: `Seq.empty`

IntelliJ plugins to depend on. Bundled(internal) plugins are specified by their plugin ID.
Plugins from repo can be specified by the plugin's id, optional version and update channel.
Plugins will be checked for compatibility against the `intellijBuild` you specified and updated to the latest version unless
 some specific version is given explicitly. Inter-plugin dependencies are also transitively resolved(e.g. depending
 on the Scala plugin will automatically attach Java and other plugin dependencies)
 
 Plugin IDs can be either searched by plugin name with the help of [searchPluginId](#searchpluginid--mapstring-string-boolean)
 task or [manually](https://github.com/JetBrains/sbt-idea-plugin/wiki/How-to-find-plugin's-id)
 
 You can tune plugin resolving on individual plugin level by specifying several options to `toPlugin` method:
 - `transitive`   - use transitive plugin resolution(default: true)
 - `optionalDeps` - resolve optional plugin dependencies(default: true)
 - `excludedIds`  - blacklist certain plugins from transitive resolution(default: Set.empty)
 
 :exclamation: Please note that Java support in IJ is implemented by a plugin: `com.intellij.java`
 
 :exclamation: Please remember that you must [declare plugin dependencies in plugin.xml](https://jetbrains.org/intellij/sdk/docs/basics/plugin_structure/plugin_dependencies.html#dependency-declaration-in-pluginxml) or your plugin may fail to load.

```SBT
// use properties plugin bundled with IDEA
intellijPlugins += "com.intellij.properties".toPlugin
// use Scala plugin as a dependency
intellijPlugins += "org.intellij.scala".toPlugin
// use Scala plugin version 2023.3.10
intellijPlugins += "org.intellij.scala:2023.3.10".toPlugin
// use latest nightly build from the repo
intellijPlugins += "org.intellij.scala::Nightly".toPlugin
// use specific version from Eap update channel
intellijPlugins += "org.intellij.scala:2023.3.10:Eap".toPlugin
// add JavaScript plugin but without its Grazie plugin dependency
intellijPlugins += "JavaScript".toPlugin(excludedIds = Set("tanvd.grazi"))
// add custom plugin with id `org.custom.plugin`, download it using the direct link https://org.example/path/to/your/plugin.zip
intellijPlugins += "org.custom.plugin:https://org.example/path/to/your/plugin.zip".toPlugin
// add custom plugin with id `org.custom.plugin` and resolve it from Marketplace.
//  if it fails to resolve it in Marketplace it will use the fallback download link
intellijPlugins += "org.custom.plugin".toPlugin.withFallbackDownloadUrl("https://org.example/path/to/your/plugin.zip")
```

#### `intellijRuntimePlugins :: SettingKey[IdeaPlugin]`

**Default**: `Seq.empty`

IntelliJ plugins to load at runtime (includes tests). These plugins are not a compile time dependencies and cannot be
referenced in code. Useful for testing your plugin in the presence of other plugins.

The usage is the same as `intellijPlugins`.

#### `searchPluginId :: Map[String, (String, Boolean)]`

Usage: `searchPluginId [--nobundled|--noremote] <plugin name regexp>`

Searches and prints plugins across locally installed IJ sdk and plugin marketplace.
Use provided flags to limit search scope to only bundled or marketplace plugins.

```
> searchPluginId Prop
[info] bundled          - Properties[com.intellij.properties]
[info] bundled          - Resource Bundle Editor[com.intellij.properties.bundle.editor]
```

#### `jbrInfo :: Option[JbrInfo]`

**Default**: `AutoJbr()`

JetBrains Java runtime version to use when running the IDE with the plugin. By default JBR version is extracted from
IDE installation metadata. Only jbr 11 is supported. Available versions can be found on [jbr bintray](https://github.com/JetBrains/JetBrainsRuntime/releases).
To disable, set to `NoJbr`

#### `patchPluginXml :: SettingKey[pluginXmlOptions]`

**Default**: `pluginXmlOptions.DISABLED`

Define some [`plugin.xml`](https://www.jetbrains.org/intellij/sdk/docs/basics/plugin_structure/plugin_configuration_file.html)
fields to be patched when building the artifact. Only the file in `target` folder
is patched, original sources are left intact. Available options are:

```SBT
patchPluginXml := pluginXmlOptions { xml =>
  xml.version           = version.value
  xml.pluginDescription = "My cool IDEA plugin"
  xml.changeNotes       = sys.env("CHANGE_LOG_FROM_CI")
  xml.sinceBuild        = (intellijBuild in ThisBuild).value
  xml.untilBuild        = "193.*"
}
```

#### `customIntellijVMOptions :: SettingKey[CustomIntellijVMOptions]`

Fine tune Java VM options for running the plugin with [runIDE](#runide-nodebug-suspend-blocking--inputkeyunit)
task and for generated IntelliJ IDEA run configurations.

By default, `sbt-idea-plugin` uses VM options from the downloaded IntelliJ Platform distribution and appends
`customIntellijVMOptions` on top. Appended options can therefore be used for local development customizations.

Example:

```SBT
customIntellijVMOptions := customIntellijVMOptions.value.copy(xmx = Some(2048), xms = Some(256))
```

Additional VM options can be appended with `withExtraOption` / `withExtraOptions`:

```SBT
customIntellijVMOptions := customIntellijVMOptions.value
  .withExtraOption("-Dnosplash=true")
```

If you need a custom `idea.properties` file for the development IDE, point the platform to it explicitly:

```SBT
customIntellijVMOptions := customIntellijVMOptions.value
  .withExtraOption("-Didea.properties.file=/absolute/path/to/idea.properties")
```

The deprecated `intellijVMOptions` and `useNewVmOptions` keys and `IntellijVMOptions` type have
been removed. Migrate build definitions to `customIntellijVMOptions`: use `Some(...)` for heap-size
overrides and `withExtraOption` / `withExtraOptions` for additional JVM arguments. Regenerate IDEA
run configurations after updating the build definition.

#### `ideaConfigOptions :: SettingKey[IdeaConfigBuildingOptions]`

Fine tune how IntelliJ run configurations are generated when importing the project in IDEA.

#### `runIDE [noDebug] [suspend] [blocking] :: InputKey[Unit]`

Runs IntelliJ IDE with current plugin. This task is non-blocking by default, so you can continue using SBT console.

By default, IDE is run with non-suspending debug agent on port `5005`. This can be overridden by either optional
arguments above, or by modifying default
[`customIntellijVMOptions`](#customintellijvmoptions--settingkeycustomintellijvmoptions).

### Publishing and Verification

#### `publishPlugin [channel] :: InputKey[String]`

Upload and publish your IntelliJ plugin on https://plugins.jetbrains.com.
In order to publish to the repo you need to 
[obtain permanent token](http://www.jetbrains.org/intellij/sdk/docs/plugin_repository/api/plugin_upload.html) and
either place it into `~/.ij-plugin-repo-token` file or pass via `IJ_PLUGIN_REPO_TOKEN` env or java property.

This task also expects an optional argument - a [custom release channel](http://www.jetbrains.org/intellij/sdk/docs/plugin_repository/custom_channels.html).
If omitted, plugin will be published to the default plugin repository channel (Stable) 
 

#### `runPluginVerifier :: TaskKey[File]`

[IntelliJ Plugin Verifier](https://github.com/JetBrains/intellij-plugin-verifier) integration task allows to 
check the binary compatibility of the built plugin against the currently used or explicitly specified 
IntelliJ IDE builds. The task returns a folder with the verification reports.

The verification can be customized by changing the default options defined in the `pluginVerifierOptions` key.
```SBT
pluginVerifierOptions := pluginVerifierOptions.value.copy(
  version = "1.254",        // use a specific verifier version
  offline = true,           // forbid the verifier from reaching the internet
  overrideIDEs  = Seq("IC-2019.3.5", "PS-2019.3.2"), // verify against specific products instead of 'intellijBuild'
  failureLevels = Set(FailureLevel.DEPRECATED_API_USAGES) // only fail if deprecated APIs are used
   // ...
)
```

#### `signPlugin :: TaskKey[File]`

Utility task that signs the plugin artifact before uploading to the [JetBrains Marketplace](https://plugins.jetbrains.com/marketplace).
Signing is performed using the [Marketplace zip signer](https://github.com/JetBrains/marketplace-zip-signer)
library. To sign a plugin a valid certificate chain, and a private key are required.

Signing is disabled by default at the moment. To enable it and set the options, modify the `signPluginOptions` key:
```SBT
signPluginOptions := signPluginOptions.value.copy(
  enabled = true,
  certFile = Some(file("/path/to/certificate")), // or via PLUGIN_SIGN_KEY env var
  privateKeyFile  = Some(file("/path/to/privateKey")), // or via PLUGIN_SIGN_CERT env var
  keyPassphrase = Some("keyPassword") // or None if password is not set(or via PLUGIN_SIGN_KEY_PWD env var)
)
```

If signing the plugin artifact zip is enabled via `signPluginOptions`, this task will be used a dependency of the
[`publishPlugin`](#publishplugin-channel--inputkeystring) task, so that the artifact is automatically signed before 
uploading to the [JetBrains Marketplace](https://plugins.jetbrains.com/marketplace)

#### `buildIntellijOptionsIndex :: TaskKey[Unit]`

Builds index of options provided by the plugin to make them searchable via 
[search everywhere](https://www.jetbrains.com/help/idea/searching-everywhere.html#search_settings) action.
This task should either be manually called instead of `packageArtifact` or before `packageArtifactZip` since it patches 
jars already built by `packageArtifact`.

### Packaging

#### `packageMethod :: SettingKey[PackagingMethod]`

**Default for root project**: `PackagingMethod.Standalone(targetPath = s"lib/${name.value}.jar")`

**Default for all other subprojects**: `PackagingMethod.MergeIntoParent()`

Controls how current project will be treated when packaging the plugin artifact.
```SBT
// produce standalone jar with the same name as the project:
packageMethod := PackagingMethod.Standalone()

// put all classes of this project into parent's jar
// NB: this option supports transitive dependencies on projects: it will walk up the dependency 
// tree to find the first Standalone() project, however if your project has multiple such parents
// this will result in an error - in this case use MergeIntoOther(project: Project) to expicitly
// specify in which project to merge into
packageMethod := PackagingMethod.MergeIntoParent()

// This packages all projects that are marked with the same moduleName into
// a module jar under lib/modules/<moduleName>.jar
// Modules are a new mechanism in intellij to better configure dependencies
// between plugins and builtin modules.
packageMethod := PackagingMethod.PluginModule("moduleName")

// For a content module declared with loading="embedded" in plugin.xml,
// put its jar directly under lib/<moduleName>.jar:
packageMethod := PackagingMethod.PluginModule("moduleName", embedded = true)

// merge all dependencies of this project in a standalone jar
// being used together with assembleLibraries setting allows sbt-assembly like packaging
// the project may contain classes but they will be ignored during packaging
packageMethod := PackagingMethod.DepsOnly("lib/myProjectDeps.jar")
assembleLibraries := true

// skip project alltogether during packaging
packageMethod := PackagingMethod.Skip()
```

#### `packageLibraryMappings :: SettingKey[Seq[(ModuleID, Option[String])]]`

**Default for root project**: `Seq.empty`

**Default for all other project**s:
```SBT
"org.scala-lang"  % "scala-.*" % ".*"        -> None ::
"org.scala-lang.modules" % "scala-.*" % ".*" -> None :: Nil
```

Sequence of rules to fine-tune how the library dependencies are packaged. By default all dependencies
including transitive are placed in the subfolder defined by 
[`packageLibraryBaseDir`](#packagelibrarybasedir--settingkeyfile)(defaults to "lib") of the plugin artifact.

You can use the [`findLibraryMapping`](#findlibrarymapping--inputkeyseqstring-seqmodulekey-optionstring)
task to debug the library mappings

```SBT
// merge all scalameta jars into a single jar
packageLibraryMappings += "org.scalameta" %% ".*" % ".*" -> Some("lib/scalameta.jar")

// skip packaging protobuf
packageLibraryMappings += "com.google.protobuf" % "protobuf-java" % ".*" -> None

// rename scala library(strip version suffix)
packageLibraryMappings += "org.scala-lang" % "scala-library" % scalaVersion -> Some("lib/scala-library.jar")
```

#### `packageLibraryBaseDir :: SettingKey[File]`

**Default**: `file("lib")`

Sets the per-project default sub-folder into which external libraries are packaged. Rules from [`packageLibraryMappings`](#packagefilemappings--taskkeyseqfile-string)
will override this setting. 

**NB!**: This directory must be relative to the [`packageOutputDir`](#packageoutputdir--settingkeyfile) so don't prepend
values of the keys with absolute paths (such as `target` or `baseDirectory`) to it

**NB!**: IDEA plugin classloader **only** adds the `lib` folder to the classpath when loading your plugin. Modifying 
this setting will essentially exclude the libraries of a project from automatic classloading

```SBT
packageLibraryBaseDir  := file("lib") / "third-party"

// protobuf will still be packaged into lib/protobuf.jar
packageLibraryMappings += "com.google.protobuf" % "protobuf-java" % ".*" -> Some("lib/protobuf.jar")
```

#### `packageFileMappings :: TaskKey[Seq[(File, String)]]`

**Default**: `Seq.empty`

Defines mappings for adding custom files to the artifact or even override files inside jars.
Target path is considered to be relative to `packageOutputDir`.

```SBT
// copy whole folder recursively to artifact root
packageFileMappings += target.value / "repo" -> "repo/"

// package single file info a jar
packageFileMappings += "resources" / "ILoopWrapperImpl.scala" ->
                            "lib/jps/repl-interface-sources.jar"
                            
// overwrite some file inside already existing jar of the artifact
packageFileMappings +=  "resources" / "META-INF" / "plugin.xml" ->
                            "lib/scalaUltimate.jar!/META-INF/plugin.xml"                            
``` 

#### `packageAdditionalProjects :: SettingKey[Seq[Project]]`

**Default**: `Seq.empty`

By default the plugin builds artifact structure based on internal classpath dependencies
of the projects in an SBT build(`dependsOn(...)`). However, sometimes one may need to package
a project that no other depends upon. This setting is used to explicitly tell the plugin which 
projects to package into the artifact without a need to introduce unwanted classpath dependency.

#### `shadePatterns :: SettingKey[Seq[ShadePattern]]`

**Default**: `Seq.empty`

Class shading patterns to be applied by JarJar library. Used to resolve name clashes with 
libraries from IntelliJ platform such as protobuf.

```SBT
shadePatterns += ShadePattern("com.google.protobuf.**", "zinc.protobuf.@1")
```

#### `bundleScalaLibrary in ThisBuild :: SettingKey[Boolean]`

Trying to load the same classes in your plugin's classloader which have already been loaded by a parent classloader
will result in classloader constraint violation. A vivid example of this scenario is depending on some other plugin,
that bundles scala-library.jar(e.g. Scala plugin for IJ) and still bundling your own.

To workaround this issue `sbt-idea-plugin` tries to automatically detect if your plugin project has dependencies on 
other plugins with Scala and filter out scala-library.jar from the resulting artifact. However, the heuristic cannot
cover all possible cases and thereby this setting is exposed to allow manual control over bundling the scala-library.jar  

#### `instrumentThreadingAnnotations :: SettingKey[Boolean]`

**Default**: `false`

Generate JVM bytecode to assert that a method is called on the correct IDEA thread. The supported annotations are:
1. `com.intellij.util.concurrency.annotations.RequiresBackgroundThread`
2. `com.intellij.util.concurrency.annotations.RequiresEdt`
3. `com.intellij.util.concurrency.annotations.RequiresReadLock`
4. `com.intellij.util.concurrency.annotations.RequiresReadLockAbsence`
5. `com.intellij.util.concurrency.annotations.RequiresWriteLock`

This is the same instrumentation that IntelliJ IDEA applies to its own codebase. Note that, matching IntelliJ IDEA's
behavior, `@RequiresReadLock` generates a call to `ThreadingAssertions.softAssertReadAccess`, which logs an error
instead of throwing an exception when read access is missing.

See: [IntelliJ IDEA ThreadingAssertions.java](https://github.com/JetBrains/intellij-community/blob/5758eb99b4a1971ebe75cda755693cc930949465/platform/core-api/src/com/intellij/util/concurrency/ThreadingAssertions.java)

#### `instrumentNotNullAnnotations :: SettingKey[Boolean]`

**Default**: `false`

Generate JVM bytecode to assert that values of `@NotNull`-annotated method parameters and return values are not null.
A null value passed for an annotated parameter throws an `IllegalArgumentException`; a null value returned from an
annotated method throws an `IllegalStateException`. The set of annotation classes is configured with the
`notNullAnnotations` setting.

This is the same instrumentation that IntelliJ IDEA applies to its own codebase and that the
[IntelliJ Platform Gradle Plugin](https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-tasks.html#instrumentCode)
performs as part of its `instrumentCode` task. Kotlin classes are skipped, as kotlinc generates its own
nullability assertions.

#### `notNullAnnotations :: SettingKey[Seq[String]]`

**Default**: `Seq("org.jetbrains.annotations.NotNull")`

Fully qualified names of the annotation classes instrumented by `instrumentNotNullAnnotations`.

#### `packageOutputDir :: SettingKey[File]`

**Default**: `target.value / "plugin" / intellijPluginName.in(ThisBuild).value.removeSpaces`

Folder to place the assembled artifact into.

#### `packageArtifact :: TaskKey[File]`

Builds unpacked plugin distribution. This task traverses dependency graph of the
build and uses settings described in the section above to create sub-artifact structure 
for each project. By default all child projects' classes are merged into the root project jar,
which is placed into the "lib" folder of the plugin artifact, all library dependencies
including transitive are placed in the "lib" folder as well. 
 
 
#### `packageArtifactZip :: TaskKey[File]`

Produces ZIP file from the artifact produced by `packageArtifact` task.
This is later used by `publishPlugin` as an artifact to upload.

### Utils

#### `findLibraryMapping :: InputKey[Seq[(String, Seq[(ModuleKey, Option[String])])]]`

Returns detailed info about libraries and their mappings by a library substring.
Helps to answer questions such as "Why is this jar in the artifact?" or "Which module introduced this jar?"
Example:
```
sbt:scalaUltimate> show findMapping interface
[info] * (runtimeDependencies,ArrayBuffer((org.scala-sbt:compiler-interface:1.4.0-M12[],Some(lib/jps/compiler-interface.jar)), (org.scala-sbt:util-interface:1.3.0[],Some(lib/jps/sbt-interface.jar))))
[info] * (repackagedZinc,ArrayBuffer((org.scala-sbt:compiler-interface:1.4.0-M12[],Some(*)), (org.scala-sbt:launcher-interface:1.1.3[],Some(*)), (org.scala-sbt:util-interface:1.3.0[],Some(*))))
[info] * (compiler-jps,ArrayBuffer((org.scala-sbt:util-interface:1.3.0[],Some(*)), (org.scala-sbt:compiler-interface:1.4.0-M12[],Some(lib/jps/compiler-interface.jar))))
[info] * (compiler-shared,ArrayBuffer((org.scala-sbt:util-interface:1.3.0[],Some(*)), (org.scala-sbt:compiler-interface:1.4.0-M12[],Some(*))))
```

#### `printProjectGraph :: TaskKey[Unit]`

Prints ASCII graph of currently selected project to console. Useful for debugging complex builds.
![](https://user-images.githubusercontent.com/1345782/65074597-d8172880-d99d-11e9-947e-aa74855e9ff1.png)

## Running the plugin

### From SBT

To run the plugin from SBT simply use [runIDE](#runide-nodebug-suspend-blocking--inputkeyunit) task.
Your plugin will be automatically compiled, an artifact built and attached to new IntelliJ instance.

Debugger can later be attached to the process remotely - the default port is 5005.

### Runtime directory isolation

By default, separate checkouts/worktrees and target IDE builds get separate IntelliJ system/config
directories. This lets tests run in parallel across worktrees without sharing mutable indexes or
caches, and keeps development IDE state separate from tests and searchable-options indexing.

Managed paths under `intellijPluginDirectory` are:

```text
runtime/<product-build>/<checkoutHash>/tests/{system,config}
runtime/<product-build>/<checkoutHash>/dev/{system,config}
runtime/<product-build>/<checkoutHash>/dev/client/{system,config}
runtime/<product-build>/<checkoutHash>/build-index/{system,config}
```

`checkoutHash` is a 10-character SHA-256 prefix of the canonical build-root path; subprojects and
symlink aliases share it. `product-build` comes from the **installed target SDK selected by
`intellijBuild`**: its product code and full build number, independently of the IDE editing the
project. For example, the canonical path `/checkout/with-spaces` hashes to `a0f9d9b86d`, giving:

```text
~/.ScalaPluginIU/runtime/IU-263.12345.67/a0f9d9b86d/tests/system/
~/.ScalaPluginIU/runtime/IU-263.12345.67/a0f9d9b86d/tests/config/
~/.ScalaPluginIU/runtime/IU-263.12345.67/a0f9d9b86d/tests/system/log/
~/.ScalaPluginIU/runtime/IU-263.12345.67/a0f9d9b86d/dev/client/config/
```

Directory resolution reads installed SDK metadata without downloading an SDK or creating runtime
directories. Missing metadata fails rather than falling back to a requested alias such as
`LATEST-EAP-SNAPSHOT`; install the SDK with `updateIntellij` first. For custom SDKs reporting the
same build number, distinguish them explicitly:

```scala
ThisBuild / intellijRuntimeBuildId := "IU-263.12345.67-custom-2"
```

The ID must start with an ASCII letter or digit and contain only letters, digits, `.`, `_`, or `-`.

#### Directory overrides and shared development mode

These `TaskKey[File]` keys select exact paths independently:

| Runtime | System key | Config key |
|---|---|---|
| Tests (sbt and IDEA JUnit) | `intellijTestSystemDir` | `intellijTestConfigDir` |
| Development backend | `intellijDevSystemDir` | `intellijDevConfigDir` |
| Split-mode frontend | `intellijDevClientSystemDir` | `intellijDevClientConfigDir` |

The VM-option builder uses the directory keys, with logs under the selected system directory's
`log/`, overriding conflicting properties in `customIntellijVMOptions`. Fully explicit directory
overrides avoid the build-identity dependency for those paths.

Lower-level sbt overrides remain available, for example:

```scala
Test / javaOptions += "-Didea.system.path=/tmp/custom-test-system"
```

This can override the forked JVM's path without updating the directory keys, generated JUnit
configuration, markers, or cleanup targets. Use the directory keys to keep these consumers aligned.

Development backend and frontend can opt into directories shared across checkouts **and builds**:

```scala
ThisBuild / intellijDevUseSharedDirectories := true
```

Or set its default through the sbt JVM: `sbt -Dsbtidea.dev.sharedDirectories=true`. Shared paths are:

```text
runtime/system-shared/         runtime/config-shared/
runtime/system-client-shared/  runtime/config-client-shared/
```

Each exact directory override wins for that directory; otherwise the sharing flag selects the
development defaults. An explicit sbt assignment to the flag overrides its JVM-property default.
Without either, isolation is enabled. Tests and indexing remain isolated. Shared development mode
assumes sequential use; it does not make concurrent access safe or share SDK-scoped installed plugins.

**Compatibility:** `intellijTestSystemDir` and `intellijTestConfigDir` changed from `SettingKey[File]`
to `TaskKey[File]`, a source and binary incompatible change. Normal `:=` overrides and reads inside
tasks still work; reads inside settings must move to tasks, and compiled consumers may need adaptation
and recompilation. `intellijRuntimeBuildId` is also a task; SDK-selection and custom VM-option keys
retain their existing types.

Regenerate IDEA configurations with `createIDEARunConfiguration` or an sbt refresh after changing
the target build, runtime identity, directories, or sharing mode. Existing JUnit configurations may
need to be recreated from the regenerated template.

#### Runtime path logging

During `runIDE`, standard sbt test-fork preparation, and searchable-options indexing, an INFO summary
shows the selected launch paths, for example:

```text
[info] IntelliJ runtime (tests, my-plugin)
[info]   System: /home/me/.ScalaPluginIU/runtime/IU-263.12345.67/a0f9d9b86d/tests/system
[info]   Config: /home/me/.ScalaPluginIU/runtime/IU-263.12345.67/a0f9d9b86d/tests/config
[info]   Logs: /home/me/.ScalaPluginIU/runtime/IU-263.12345.67/a0f9d9b86d/tests/system/log
```

The test summary reflects the final fork options, including `Test / javaOptions` overrides.
Direct IDEA launches use the generated configurations and do not execute sbt's logging hooks.
Inspecting directory keys does not print a launch summary.

#### Existing data, markers, and cleanup

Development configs start fresh by default. Existing untracked runtime data, including the earlier
local checkout-first layout, is left intact during migration; nothing is copied. Managed test/indexing
data registered by sbt subsequently follows the retention policy below. Regenerate IDEA
configurations to adopt the new paths. To import settings, export them from the old development IDE
with **File | Manage IDE Settings | Export Settings**, then use **Import Settings** in the new instance.
Keep the old config directory until the imported settings have been verified.

Alternatively, point individual keys at legacy locations to reuse them directly, for example:

```scala
ThisBuild / intellijDevConfigDir := intellijPluginDirectory.value / "config"
ThisBuild / intellijDevSystemDir := intellijPluginDirectory.value / "system"
ThisBuild / intellijTestSystemDir := intellijPluginDirectory.value / "test-system"
```

The last example leaves the test config isolated unless overridden separately. Split-mode legacy
locations were `system_client/` and `config_client/`. Exact overrides receive no `README.txt` markers.

Managed test system/config directories get non-overwriting `README.txt` markers identifying their
purpose, canonical checkout, and target build. They are created during test preparation or JUnit
configuration generation, never merely by inspecting paths. `cleanUpTestEnvironment` deletes only
the selected test system/config directories, **including explicit overrides**; it does not remove
checkout roots, other builds, or shared development data. Automatic expiry is separate from this
explicit cleanup task and follows the policy below.

SDKs, plugin dependencies, and downloads retain their shared locations under `intellijPluginDirectory`:

```text
sdk/<intellijBuild>/                 IntelliJ SDK
sdk/<intellijBuild>/plugins/         bundled plugins
sdk/<intellijBuild>/custom-plugins/  installed plugin dependencies
sdk/downloads/                      download cache
```

#### Automatic cached-data cleanup

| Data | Enable setting | Default | Removal policy |
|---|---|---|---|
| Test/indexing runtimes | `autoRemoveOldCachedIntelliJRuntimes` | `true` | Seven days since last sbt use; check at most daily |
| Installed SDKs | `autoRemoveOldCachedIntelliJSDK` | `false` | Existing version/creation-age heuristics during `updateIntellij` |
| Download cache | `autoRemoveOldCachedDownloads` | `false` | Downloads older than 30 days, checked during `updateIntellij` |

Configure runtime expiry with positive finite durations:

```scala
import scala.concurrent.duration._

ThisBuild / autoRemoveOldCachedIntelliJRuntimes := true
ThisBuild / intellijRuntimeCleanupRetention := 7.days
ThisBuild / intellijRuntimeCleanupInterval := 24.hours
```

Runtime cleanup covers automatically managed `tests/{system,config}` and
`build-index/{system,config}` only. Development backend/client data, shared development directories,
explicit overrides (even overrides equal to an old default), and unregistered legacy locations are
excluded. Later overrides withdraw matching prior ownership. The existing `cleanUpTestEnvironment`
task still explicitly deletes the selected test paths, including overrides, regardless of age.

Maintenance resolves explicit test, development backend, and frontend directory overrides before
permitting cleanup; default development tasks remain lazy. It withdraws whole managed directories
that overlap an override, including parent/child overlaps. This covers the unusual case of reusing
an old test/index directory for development settings. Unavailable overrides or unsuccessful ownership
withdrawal defer this build's automatic passes without failing unrelated commands.

Protection follows the current configuration. Keep directory-selection tasks cheap and stable until
reconfiguration, and reload other sbt sessions for the checkout after changing directory assignments.
Avoid conflicting ownership of one path across checkouts; copy/import another checkout's settings
unless its owner is also configured to stop managing that location. Returning a path to ordinary
managed test/index use can make it eligible for cleanup again.

**Last use means sbt activity for a canonical checkout and target SDK build.** Build load/reload
refreshes its usage record; later task activity refreshes at most hourly, and test/indexing
preparation refreshes before launch. Subprojects share the same identity. Switching builds leaves
the previous build's timestamp unchanged. IDEA-only test runs do not refresh it.

Usage records are small files outside the caches. For example:

```text
<intellijPluginDirectory>/
  .sbt-idea-plugin/
    runtime-usage-v1/
      IU-263.7307.200--a0f9d9b86d.properties
      IU-263.7307.200--a0f9d9b86d.lock
    runtime-cleanup-v1.properties
    runtime-cleanup.lock
  runtime/
    IU-263.7307.200/
      a0f9d9b86d/
        tests/system/
        tests/config/
        build-index/system/
        build-index/config/
        dev/                    (excluded from automatic expiry)
```

The record's modification time is last use. Its content records ownership and relative managed paths:

```properties
formatVersion=1
owner=sbt-idea-plugin
checkoutRoot=/checkout/with-spaces
buildId=IU-263.7307.200
automaticCleanup=true
retentionSeconds=604800
test.system=runtime/IU-263.7307.200/a0f9d9b86d/tests/system
test.config=runtime/IU-263.7307.200/a0f9d9b86d/tests/config
index.system=runtime/IU-263.7307.200/a0f9d9b86d/build-index/system
index.config=runtime/IU-263.7307.200/a0f9d9b86d/build-index/config
```

Each record keeps its owner's retention and opt-out policy until refreshed by that owner. A cleanup
started in another checkout cannot shorten that retention or override its opt-out. Disabling runtime
cleanup stops scans initiated by that build and opts its refreshed records out; historical records
keep their last recorded policy. Unknown/malformed formats and unsafe paths are skipped. Recorded
relative paths allow future cleanup versions to retain support for older runtime layouts.

After task completion, sbt cheaply checks whether cleanup is due. A persisted last-attempt timestamp
and plugin-home lock debounce across processes; per-record locks coordinate refresh and deletion.
Only expired owned directories are traversed. Removal runs synchronously during a due pass and
reports successfully removed directory counts and file-data volume; this is not an exact measurement
of reclaimed physical blocks on filesystems with compression, hardlinks, or clones. Partial failures
warn and are retried on a later pass without failing the sbt command.

For example, a record last refreshed on day 0 becomes eligible after day 7. A refresh on day 3 moves
eligibility to after day 10. Deletion waits for a due sbt task completion; no background scheduler runs
when sbt is idle or stopped. Registry registration does not initialize runtime caches. Missing SDK
context skips maintenance without downloading an SDK or failing unrelated commands.

Identifiable Java processes, including generated Java argument files, are checked before deletion; active
or uninspectable candidates are deferred. **This is best effort, not a JVM-lifetime guarantee:** a
process can start after inspection. Long periods of IDEA-only testing can leave sbt usage markers
stale. No Java agent is installed. Regenerate IDEA configurations after runtime directory changes as
described above. See [ADR 0003](docs/adr/0003-expire-unused-managed-runtimes.md) for the tradeoff.

To inspect cleanup state for the selected `intellijPluginDirectory`, run:

```text
sbt showIntellijRuntimeCleanup
```

The report lists registered checkout/build pairs, last sbt use, each owner's retention and opt-out
policy, recorded paths (present or absent), and current expiry/process-protection assessments. It also
shows this build's policy, cached runtime context, last cleanup attempt, and next eligible pass.
Rejected records include a validation reason; omitted paths say "not registered." Development data,
explicit overrides, and untracked legacy locations are not inventoried. Discovery reads only the
registry, with no recursive size scan; expired candidates share one Java-process snapshot.

The report body is read-only: it does not refresh usage, acquire locks, advance cleanup timestamps,
delete data, or evaluate SDK/build-identity tasks. **Normal sbt startup and completion hooks still
apply:** startup setup may install an SDK, and maintenance may refresh usage or remove expired data
before or after the report. This is a current snapshot, not a history of skipped passes or lock
contention, and it cannot guarantee that an eligible runtime will be removed on the next pass.

#### Concurrency

Separate checkouts/worktrees can run tests independently. The sbt and IDEA test runners use the same
paths for one checkout and build, so do not run them simultaneously. Within one sbt server, test
commands run sequentially; this plugin also disables parallel tests. Independent sbt processes,
including non-server runs, still share the selected directories. Concurrent JVMs sharing the same
checkout, build, and purpose are unsupported: indexes and caches can conflict or become corrupted.

For explicit per-JVM test isolation, use `forRuntimeId` or `applyTo` in
[`IntellijTestRuntimeDirectories`](ideaSupport/src/main/scala/org/jetbrains/sbtidea/IntellijTestRuntimeDirectories.scala).
Their signatures and explicit-ID paths (`test-system-<id>`, `test-config-<id>`) are unchanged and
independent of checkout/build defaults. Hash rationale and marker details are in
[`CheckoutRuntimeDirectories`](ideaSupport/src/main/scala/org/jetbrains/sbtidea/CheckoutRuntimeDirectories.scala);
design alternatives are recorded in [ADR 0002](docs/adr/0002-organize-intellij-runtime-directories.md).

### From IDEA

- `sbt-idea-plugin` generates IDEA-readable artifact xml and run configuration on project import
- After artifact and run configuration have been created(they're located in `.idea` folder of the project) you can 
run or debug the new run configuration. This will compile the project, build the artifact and attach it to the
 new IDEA instance
- :exclamation: Note that doing an "SBT Refresh" is required after making changes to your build
that affect the final artifact(i.e. changing `libraryDependencies`), in order to update IDEA configs
- :exclamation: You may need to [manually build the artifact](#plugin-artifact-not-built-when-running-from-idea-after-importing-the-project)
  when running your plugin for the first time
  
## Custom IntelliJ artifacts repo

Under some circumstances using a proxy may be required to access IntelliJ artifacts repo, or there even is a local
artifact mirror set up. To use non-default repository for downloading IntelliJ product distributions set 
`sbtidea.ijrepo` jvm property. Example: `-Dsbtidea.ijrepo=https://proxy.mycompany.com/intellij-repository`
  
## Auto enable the plugin

Sbt-idea-plugin currently breaks scalaJS compilation, and thereby has autoloading disabled.
To enable it either add `enablePlugins(SbtIdeaPlugin)` to project definition. Example:
```scala
lazy val hocon = project.in(file(".")).settings(
  scalaVersion  := "2.12.8",
  version       := "2019.1.2",
  intellijInternalPlugins := Seq("properties"),
  libraryDependencies += "com.novocode" % "junit-interface" % "0.11" % "test",
).enablePlugins(SbtIdeaPlugin)
```

If you with to automatically enable the plugin for all projects in your build, place the following class into top level 
`project` folder of your build.
```scala
import org.jetbrains.sbtidea.AbstractSbtIdeaPlugin

object AutoSbtIdeaPlugin extends AbstractSbtIdeaPlugin {
  override def trigger  = allRequirements
}
``` 
## Grouping with qualified names is available from Scala plugin version 2024.1.4
In the Scala plugin 2024.1.4 a significant change has been made according to modules grouping and their naming. You can read more about this change [here](https://youtrack.jetbrains.com/issue/SCL-21288/Rewrite-deprecated-module-grouping-logic-to-the-new-API-grouping-with-qualified-names-is-now-supported.#focus=Comments-27-8977291.0-0). 
Because of this change, it was necessary to change how the project names are generated in packageMapping tasks (`packageMappings` and `packageMappingsOffline`).
To switch between the new and the old projects naming logic, `grouping.with.qualified.names.enabled` system property has been introduced.
If your Scala plugin version is 2024.1.4 or higher, then in order to generate correct mappings you should set this property to true (`-Dgrouping.with.qualified.names.enabled=true`). 
Otherwise, there is no need to do anything as this value is set to false by default.

## Separate modules for production and test sources are available from Scala plugin version 2024.2.444
From the 2024.2.444 Scala plugin version, the option to create separate modules for production and test sources has been introduced. You can read more about this change [here](https://youtrack.jetbrains.com/issue/SCL-21157/sbt-represent-production-and-test-sources-as-separate-IntelliJ-modules).
Because of this change, it was necessary to change how the project names (in `packageMappings` and `packageMappingsOffline` tasks) and module name (in `createIDEARunConfiguration` task) are generated.
To switch between separate modules for production and test sources and the "old approach" of module generation, `separate.prod.test.sources.enabled` system property has been introduced.
If your Scala plugin version is 2024.2.444 or higher, and you enabled separate modules for production and test sources in `Settings | Build, Execution, Deployment | Build Tools | sbt` 
then to generate correct mappings and IDEA Run Configuration you should set this property to true (`-Dseparate.prod.test.sources.enabled=true`).
Otherwise, there is no need to do anything as this value is set to false by default.

## Source-Level Debugging with `packageArtifactDynamic`

`packageArtifact` packages all project classes into JARs under `lib/`. This is suitable for distribution but prevents IntelliJ's debugger from mapping classes back to source files in dependent modules.

For development, use `packageArtifactDynamic` instead:

```
sbt packageArtifactDynamic
```

This writes class files to a `classes/` directory on disk instead of packaging them into JARs. IntelliJ's debugger can then resolve source files for all modules in the project, enabling **breakpoints across module boundaries** — including external projects loaded via `dependsOn(RootProject(...))`.

| Task | Output | Source-level debugging |
|------|--------|-----------------------|
| `packageArtifact` | JARs in `lib/` | Only within the main plugin module |
| `packageArtifactDynamic` | Class files in `classes/` | Across all modules including external projects |
| `packageArtifactZip` | Distributable `.zip` | For publishing to JetBrains Marketplace |

### External Project Support

`dependsOn(RootProject(...))` works for depending on normal SBT projects that don't use sbt-idea-plugin. External projects' class files are automatically merged into the plugin artifact via `MergeIntoParent()`, and their library dependencies are resolved and included — no `packageFileMappings` or manual `libraryDependencies` pull needed.

`packageLibraryMappings` set on the root plugin project applies as global defaults to all nodes, including external projects. For example, to exclude scala libraries from the entire artifact (including external project dependencies):

```scala
packageLibraryMappings ++= Seq(
  "org.scala-lang" % "scala.*" % ".*" -> None,
  "org.scala-lang.modules" % "scala.*" % ".*" -> None
)
```

Node-specific `packageLibraryMappings` override root mappings when both are set.

## Known Issues and Limitations

### `name` key in projects

Please do not explicitly set the `name` setting key for projects that have `SbtIdeaPlugin` attached.
SBT will automatically set it from the `lazy val`'s name of the project definition.

IDEA cannot correctly handle the sutuation when `name` key and `lazy val`'s name of a project are different,
thus making the generated artifact and run configuration xml's invalid.

Related issue: https://github.com/JetBrains/sbt-idea-plugin/issues/72

### Plugin artifact not built when running from IDEA after importing the project

The generated IDEA run configurations depend on the built artifact of the plugin, so it should be built 
automatically when running or debugging the generated configuration.

However, when the IDEA xml configuration file is created externally, like in the case of `sbt-idea-plugin`,
it is sometimes not picked up immediately and requires an explicit loading.

It is recommended to explicitly invoke `Build | Build Artifacts | Rebuild` from IDEA after importing the project 
for the first time(i.e. when xmls are first generated).

### Development notes
To publish a new version of `sbt-idea-plugin`, just add a new tag in format `vA.B.C` (e.g.`v3.13.4`) and push it to the main branch.
TeamCity will automatically  Build/Test/Deploy it in `sbt-idea-plugin` configuration. \
(works in internal network only)
