import java.io.File
import java.nio.file.{Files, StandardCopyOption, Paths}

import sbt.TupleSyntax.t2ToTable2

val copyNativeImageConfigs = taskKey[Unit]("Copy native-image configurations to target")

copyNativeImageConfigs := ((baseDirectory, target) map { (base, trg) =>
  {
    Option(new File(trg, "native-image/META-INF/native-image"))
      .filterNot(_.exists())
      .map(_.toPath)
      .filterNot(p => Files.isDirectory(p))
      .foreach(p => Files.createDirectories(p))

    new File(base, "META-INF/native-image")
      .listFiles()
      .foreach(
        file =>
          Files.copy(file.toPath,
                     new File(trg, s"native-image/META-INF/native-image/${file.getName}").toPath,
                     StandardCopyOption.REPLACE_EXISTING)
      )
  }
}).value

addCompilerPlugin("org.typelevel" %% "kind-projector"     % "0.11.0" cross CrossVersion.full)
addCompilerPlugin("com.olegpy"    %% "better-monadic-for" % "0.3.1")

lazy val root = (project in file("."))
  .enablePlugins(GitVersioning, BuildInfoPlugin, NativeImagePlugin)
  .settings(
    name := "file-utils",
    organization := "org.teckhooi",
    scalaVersion := "2.13.3",
    fork in Test := true,
    buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion, git.baseVersion, git.gitHeadCommit),
    buildInfoPackage := "org.teckhooi.fileutils",
    buildInfoUsePackageAsPath := true,
    libraryDependencies ++= Seq(
      "io.chrisdavenport" %% "log4cats-slf4j" % "1.1.1",
      "ch.qos.logback"    % "logback-classic" % "1.3.0-alpha5",
      "dev.profunktor"    %% "console4cats"   % "0.8.1",
      "com.monovore"      %% "decline-effect" % "1.3.0",
      "com.lihaoyi"       %% "os-lib"         % "0.7.1"
    ),
    Compile / mainClass := Some("org.teckhooi.fileutils.Main"),
    nativeImageOptions ++= List(
      "--no-fallback",
      "-H:+AddAllCharsets",
      "-H:ReflectionConfigurationFiles=META-INF/native-image/reflect-config.json",
      "-H:IncludeResources=logback.xml"
    ),
    nativeImage := (nativeImage dependsOn copyNativeImageConfigs).value
  )
