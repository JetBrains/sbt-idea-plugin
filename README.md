# sbt-idea-plugin

SBT plugin that makes development of Intellij IDEA plugins in Scala easier.

## Installation

* Insert into `project/plugins.sbt`:

```Scala
resolvers += Resolver.url("dancingrobot84-bintray",
  url("http://dl.bintray.com/dancingrobot84/sbt-plugins/"))(Resolver.ivyStylePatterns)

addSbtPlugin("com.dancingrobot84" % "sbt-idea-plugin" % "0.1.0")
```

* Insert into `build.sbt`:

```Scala
ideaPluginSettings

ideaBuild := "139.1117.1" // Required. See notes below about IDEA builds
```

* Run SBT and execute `updateIdea` task

* Start coding

## Settings

#### ideaBuild

No default value

IDEA's build number. Binaries and sources of this build will be downloaded from
https://teamcity.jetbrains.com and used in compilation and testing. You can
find build number of your IDEA in `Help -> About` dialog. However, it might be
incomplete, so I strongly recommend you to verify it against
[this list](https://teamcity.jetbrains.com/viewType.html?buildTypeId=bt410&tab=buildTypeHistoryList&branch_IntelliJIdeaCe=__all_branches__).

#### ideaBaseDirectory

Default: `baseDirectory / "idea"`

Directory where IDEA binaries and sources will be unpacked.

#### ideaPlugins

Default: `Seq.empty`

List of IDEA plugins to depend upon. Their jars will be used in compilation.
Available plugins can be found in `ideaBaseDirectory / "plugins"` directory.

## Tasks

#### updateIdea

Download IDEA's binaries and sources, put them into
`ideaBaseDirectory / ideaBuild` directory and automatically add IDEA's and
plugin's jars into `unmanagedJars`.

## Notes and best practices

- If you use `sbt-assembly` plugin to produce a fat jar to
  distribute your plugin you should avoid putting IDEA's jars
  into this fat jar of yours. To achieve this insert

  ```Scala
  assemblyExcludedJars in aassembly <<= ideaFullJars
  ```

  into your `build.sbt`
