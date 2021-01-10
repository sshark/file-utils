package org.teckhooi

package fileutils

import java.io.File
import cats.Monad
import cats.effect.Console.io._
import cats.effect.{Console, ExitCode, IO}
import cats.implicits._
import com.monovore.decline.{Opts, Visibility}
import com.monovore.decline.effect.CommandIOApp
import org.teckhooi.fileutils.domain.{DeleteCommand, ListCommand}

import java.nio.file.Path

object MainApp
    extends CommandIOApp(
      name = "file-utils",
      header = "Sums the bytes used by all the files in the given directory including sub-directories"
    ) {
  override def main: Opts[IO[ExitCode]] = {
    val argOpts: Opts[Path] = Opts.argument[Path]("paths")

    import cats.effect.Console.implicits._
    import org.teckhooi.fileutils.DirsUtilsSyncRequired.implicits._
    import org.teckhooi.fileutils.FilesUtilsSyncRequired.implicits._

    val versionAction: Opts[IO[ExitCode]] =
      Opts
        .flag("version", help = "Show the application version number", visibility = Visibility.Partial)
        .as(Console[IO]
          .putStr(s"${BuildInfo.version}${BuildInfo.gitHeadCommit.fold("")(commit => s" (${commit.substring(0, 7)})")}")
          .as(ExitCode.Success))

    val verboseOpts: Opts[Boolean] =
      Opts.flag("verbose", help = "Display the directory's name and size", "v").orFalse

    val patternOpts: Opts[List[String]] =
      Opts.options[String]("pattern", help = "RE pattern to match files for deletion", "p").orEmpty

    val listOpts: Opts[ListCommand] =
      Opts.subcommand("ls", "List all the files and sub-directories in the directory")(argOpts.map(ListCommand.apply))

    val rmOpts: Opts[DeleteCommand] =
      Opts.subcommand(
        "rm",
        "Remove files and sub-directories in the directory according to the patterns ** NO ACTION WILL BE TAKEN **")(
        (argOpts, patternOpts).mapN(DeleteCommand.apply))

    versionAction orElse (verboseOpts, listOpts orElse rmOpts).mapN {
      case (verbose, ListCommand(dir)) =>
        for {
          rootDirOpt <- DirsUtilsSyncRequired[IO].dirExists(dir.toFile)
          exitCode <- rootDirOpt.fold(putError(dirDoesNotExist(dir.toFile.getName)).as(ExitCode.Error))(rootDirFile =>
            calculateDirSize[IO](rootDirFile, verbose).as(ExitCode.Success))
        } yield exitCode

      case (_, DeleteCommand(dir, patterns)) =>
        for {
          rootDir <- DirsUtilsSyncRequired[IO].dirExists(dir.toFile)
          exitCode <- rootDir.fold(putError(dirDoesNotExist(dir.toFile.getName)).as(ExitCode.Error))(rootDirFile =>
            deleteDir[IO](rootDirFile, patterns))
        } yield exitCode
    }
  }

  private def dirDoesNotExist(dir: String) = s"""Directory "$dir" does not exist"""

  def deleteDir[F[_]: DirsUtilsSyncRequired: FilesUtilsSyncRequired: Console: Monad](
      dir: File,
      patterns: List[String]): F[ExitCode] =
    for {
      fullPath <- FilesUtilsSyncRequired[F].fullPath(dir)
      _        <- DirsUtilsSyncRequired[F].rm(dir, patterns)
      exitCode <- Console[F]
        .putStrLn(
          s"""Deleting files in "$fullPath"${showIfNotEmptyList(patterns)} ** FAKE, NO ACTION WILL BE TAKEN **""")
        .as(ExitCode.Success)
    } yield exitCode

  private def showIfNotEmptyList(xs: List[String]): String =
    if (xs.isEmpty) "" else s" matching ${xs.mkString(", ")}"

  def calculateDirSize[F[_]: Monad: DirsUtilsSyncRequired: FilesUtilsSyncRequired: Console](dir: File,
                                                                                            verbose: Boolean): F[Unit] =
    for {
      fullPath <- FilesUtilsSyncRequired[F].fullPath(dir)
      total    <- DirsUtilsSyncRequired[F].dirSize(dir, verbose)
      _        <- Console[F].putStrLn(s"Total files size for $fullPath is ${prettyPrint(total)} bytes")
    } yield ()
}
