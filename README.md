# sbt-idea-plugin

SBT plugin that makes development of IntelliJ IDEA plugins in Scala easier.

## Installation

* Insert into `project/plugins.sbt`:

```Scala
resolvers += Resolver.url("dancingrobot84-bintray",
  url("http://dl.bintray.com/dancingrobot84/sbt-plugins/"))(Resolver.ivyStylePatterns)

addSbtPlugin("com.dancingrobot84" % "sbt-idea-plugin" % "0.3.1")
```

* Insert into `build.sbt`:

```Scala
enablePlugins(SbtIdeaPlugin)
```

* Run SBT and execute `updateIdea` task. It will download IDEA and external plugins used in project

* Start coding

## Available settings

#### `ideaBuild in ThisBuild :: SettingKey[String]`

Default: `LATEST-EAP-SNAPSHOT`

IDEA's build number. Binaries and sources of this build will be downloaded from
https://jetbrains.com and used in compilation and testing. You can find build
number of your IDEA in `Help -> About` dialog. However, it might be incomplete,
so I strongly recommend you to verify it against [available
releases](https://www.jetbrains.com/intellij-repository/releases) and
[available snapshots](https://www.jetbrains.com/intellij-repository/snapshots).

#### `ideaDownloadDirectory in ThisBuild :: SettingKey[File]`

Default: `baseDirectory / "idea"`

Directory where IDEA binaries and sources will be downloaded.

#### `ideaInternalPlugins :: SettingKey[String]`

Default: `Seq.empty`

List of bundled IDEA plugins to depend upon. Their jars will be used in compilation.
Available plugins can be found in `ideaBaseDirectory / "plugins"` directory.

#### `ideaExternalPlugins :: SettingKey[IdeaPlugin]`

Default: `Seq.empty`

List of external IDEA plugins to depend upon. Their zips or jars will be downloaded
and unpacked in `ideaBaseDirectory / "externalPlugins"` directory, each in its own subdirectory. They will be used
in compilation. 

NOTE: plugins consisting of *.class files packed in zip archive ([IntelliJ IDEA Plugin Structure](https://confluence.jetbrains.com/display/IDEADEV/IntelliJ+IDEA+Plugin+Structure), the 2nd way of organizing plugin's content) ARE NOT supported.

## Tasks

#### `updateIdea :: TaskKey[Unit]`

Download IDEA's binaries and sources, put them into
`ideaBaseDirectory` directory. Download external plugins and put
them in `ideaBaseDirectory / "externalPlugins"` directory. Automatically add IDEA's and
plugin's jars into `unmanagedJars in Compile`.

## Notes and best practices

- If you use `sbt-assembly` plugin to produce a fat jar to
  distribute your plugin you should avoid putting IDEA's jars
  into this fat jar of yours. To achieve this insert

  ```Scala
  assemblyExcludedJars in assembly <<= ideaFullJars
  ```

  into your `build.sbt`
- If you depend upon one or more external plugins, add `-Didea.plugins.path=$PROJECT_DIR$/<ideaBaseDirectory>/externalPlugins` to your run configuration's VM options in order for debug IDEA instance to use already downloaded plugins
