package org.teckhooi

package fileutils

import java.io.File

import cats.Monad
import cats.effect.Console.io._
import cats.effect.{Console, ExitCode, IO, Sync}
import cats.implicits._
import com.monovore.decline.Opts
import com.monovore.decline.effect.CommandIOApp
import org.teckhooi.fileutils.domain.{DeleteCommand, ListCommand}

object Main
    extends CommandIOApp(
      name = "file-utils",
      header = "Sums the bytes used by all the files in the given directory including sub-directories",
      version = s"${BuildInfo.version}${BuildInfo.gitHeadCommit.fold("")(commit => s" (${commit.substring(0, 7)})")}"
    ) {
  override def main: Opts[IO[ExitCode]] = {
    val argOpts: Opts[String] = Opts.argument[String]("paths")

    val verboseOpts: Opts[Boolean] =
      Opts.flag("verbose", help = "Display the directory's name and size").orFalse

    val patternOpts: Opts[List[String]] =
      Opts.options[String]("pattern", help = "RE pattern to match files for deletion", short = "p").orEmpty

    val listOpts: Opts[ListCommand] =
      Opts.subcommand("ls", "List all the files and sub-directories in the directory")(argOpts.map(ListCommand.apply))

    val rmOpts: Opts[DeleteCommand] =
      Opts.subcommand(
        "rm",
        "Remove files and sub-directories in the directory according to the patterns ** NO ACTION WILL BE TAKEN **")(
        (argOpts, patternOpts).mapN(DeleteCommand.apply))

    import cats.effect.Console.implicits._
    import org.teckhooi.fileutils.FileUtils.implicits._

    (verboseOpts, listOpts orElse rmOpts).mapN {
      case (verbose, ListCommand(dir)) =>
        for {
          rootDirOpt <- dirExists[IO](dir)
          exitCode <- rootDirOpt.fold(putError(dirDoesNotExist(dir)).as(ExitCode.Error))(rootDirFile =>
            calculateDirSize[IO](rootDirFile, verbose).as(ExitCode.Success))
        } yield exitCode

      case (verbose, DeleteCommand(dir, patterns)) =>
        for {
          rootDir <- dirExists[IO](dir)
          exitCode <- rootDir.fold(putError(dirDoesNotExist(dir)).as(ExitCode.Error))(rootDirFile =>
            deleteDir[IO](rootDirFile, patterns))
        } yield exitCode
    }
  }

  private def dirDoesNotExist(dir: String) = s"""Directory "$dir" does not exist"""

  def dirExists[F[_]: Sync](dir: String): F[Option[String]] =
    for {
      rootDir <- Sync[F].delay(new File(dir))
      exists  <- Sync[F].delay(rootDir.exists())
    } yield Option.when(exists)(dir)

  def deleteDir[F[_]: FileUtils: Console: Monad](dir: String, patterns: List[String]): F[ExitCode] =
    for {
      _ <- FileUtils[F].rm(dir, patterns)
      exitCode <- Console[F]
        .putStrLn(
          s"""Deleting files in "${new File(dir).getCanonicalPath}"${showIfNotEmptyList(patterns)} ** FAKE, NO ACTION WILL BE TAKEN **""")
        .as(ExitCode.Success)
    } yield exitCode

  private def showIfNotEmptyList(xs: List[String]): String =
    if (xs.isEmpty) "" else s" matching ${xs.mkString(", ")}"

  def calculateDirSize[F[_]: Monad: FileUtils: Console](dir: String, verbose: Boolean): F[Unit] =
    for {
      total <- FileUtils[F].ls(dir, verbose)
      _     <- Console[F].putStrLn(s"Total files size for ${new File(dir).getCanonicalPath} is ${prettyPrint(total)} bytes")
    } yield ()
}
