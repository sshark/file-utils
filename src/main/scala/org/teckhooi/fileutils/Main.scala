package org.teckhooi

package fileutils

import java.io.File

import cats.Parallel
import cats.effect.Console.io._
import cats.effect.{Console, ExitCode, IO, Sync}
import cats.implicits._
import ch.qos.logback.classic.Logger
import com.monovore.decline.Opts
import com.monovore.decline.effect.CommandIOApp
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import org.teckhooi.fileutils.domain.{DeleteCommand, ListCommand}

object Main
    extends CommandIOApp(name = "file-utils",
                         header = "Sums the bytes used by the files in the directory including sub-directories") {
  override def main: Opts[IO[ExitCode]] = {
    val argOpts: Opts[String] = Opts.argument[String]("paths")

    val verboseOpts: Opts[Boolean] =
      Opts.flag("verbose", help = "Display the directory's name and size", short = "v").orFalse

    val patternOpts: Opts[List[String]] =
      Opts.options[String]("pattern", help = "RE pattern to match files for deletion", short = "p").orEmpty

    val listOpts: Opts[ListCommand] =
      Opts.subcommand("ls", "List all the files and sub-directories in the directory")(argOpts.map(ListCommand.apply))

    val rmOpts: Opts[DeleteCommand] =
      Opts.subcommand(
        "rm",
        "Remove files and sub-directories in the directory according to the patterns ** NOT IMPLEMENTED **")(
        (argOpts, patternOpts).mapN(DeleteCommand.apply))

    import cats.effect.Console.implicits._

    (verboseOpts, listOpts orElse rmOpts).mapN {
      case (verbose, ListCommand(dir)) =>
        for {
          rootDir <- IO(new File(dir))
          exitCode <- IO(rootDir.exists()).flatMap(
            exists =>
              if (exists) calculateDirSize[IO](rootDir, verbose).as(ExitCode.Success)
              else putError(s"$rootDir does not exists").as(ExitCode.Error))
        } yield exitCode
      case (verbose, DeleteCommand(dir, patterns)) =>
        putStrLn(s"""Deleting files in "$dir"${showIfNotEmptyList(patterns)}""").as(ExitCode.Success)
    }
  }

  private def showIfNotEmptyList(xs: List[String]): String = if (xs.isEmpty) "" else s" matching ${xs.mkString(", ")}"

  def calculateDirSize[F[_]: Sync: Parallel: Console](dir: File, verbose: Boolean): F[Unit] =
    for {
      total <- ls[F](dir, verbose)
      _     <- Console[F].putStrLn(s"Total $dir size is ${prettyPrint(total)} bytes")
    } yield ()

  private def prettyPrint(i: Long): String = java.text.NumberFormat.getIntegerInstance.format(i)

  def ls[F[_]: Sync: Parallel](dir: File, verbose: Boolean): F[Long] =
    for {
      _ <- if (verbose)
        Sync[F].delay(
          org.slf4j.LoggerFactory
            .getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)
            .asInstanceOf[Logger]
            .setLevel(ch.qos.logback.classic.Level.DEBUG))
      else Sync[F].unit
      logger               <- Slf4jLogger.create[F]
      (totalSize, subDirs) <- findDirsAndSize(dir)
      _                    <- logger.debug(s"${dir.getAbsolutePath} => ${prettyPrint(totalSize)} bytes")
      sum                  <- subDirs.parTraverse(oneDir => ls(oneDir, verbose)).map(_.sum + totalSize)
    } yield sum

  def findDirsAndSize[F[_]: Sync](baseDir: File): F[(Long, List[File])] =
    Sync[F].delay(
      Option(baseDir.listFiles())
        .map { xs =>
          xs.map(f => if (f.isFile) f.length.asLeft[File] else f.asRight[Long]).foldLeft((0L, List.empty[File])) {
            case ((fs, xs), x) => x.fold(newLen => (newLen + fs, xs), newFile => (fs, newFile :: xs))
          }
        }
        .getOrElse((0, Nil)))
}
