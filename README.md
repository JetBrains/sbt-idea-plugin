# sbt-idea-plugin

SBT plugin for developers writing plugins for Intellij IDEA in Scala and Java. WIP.

# Usage

* Insert into `project/plugins.sbt`:

```Scala
resolvers += Resolver.url("dancingrobot84-bintray",
  url("http://dl.bintray.com/dancingrobot84/sbt-plugins/"))(Resolver.ivyStylePatterns)

addSbtPlugin("com.dancingrobot84" % "sbt-idea-plugin" % "0.0.1")
```

* Insert into `build.sbt`:

```
ideaPluginSettings
```

* Download IDEA binaries and sources by running `updateIdea` command in SBT REPL

* Start coding
