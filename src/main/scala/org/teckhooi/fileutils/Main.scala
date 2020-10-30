package org.teckhooi

package fileutils

import java.io.File

import cats.Parallel
import cats.effect.{Console, ExitCode, IO, Sync}
import cats.implicits._
import com.monovore.decline.Opts
import com.monovore.decline.effect.CommandIOApp
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import cats.effect.Console.io._
import org.teckhooi.fileutils.domain.{DeleteCommand, ListCommand}

object Main extends CommandIOApp(name = "file-utils", header = "Files and sub-directories supervisor") {
  override def main: Opts[IO[ExitCode]] = {
    val argOpts: Opts[String] = Opts.argument[String]("paths")

    val listOpts: Opts[ListCommand] =
      Opts.subcommand("ls", "List all the files and sub-directories in the directory")(argOpts.map(ListCommand.apply))

    val rmOpts: Opts[DeleteCommand] =
      Opts.subcommand("rm", "Remove files and sub-directories in the directory according to pattern")(
        argOpts.map(DeleteCommand.apply))

    import cats.effect.Console.implicits._

    (listOpts orElse rmOpts).map {
      case ListCommand(dir) =>
        for {
          rootDir <- IO(new File(dir))
          exitCode <- IO(rootDir.exists()).flatMap(
            exists =>
              if (exists) calculateDirSize[IO](rootDir).as(ExitCode.Success)
              else putError(s"$rootDir does not exists").as(ExitCode.Error))
        } yield exitCode
      case DeleteCommand(dir) => putStrLn(s"Deleting files in $dir (DRY RUN ONLY)").as(ExitCode.Success)
    }
  }

  def calculateDirSize[F[_]: Sync: Parallel: Console](dir: File): F[Unit] =
    for {
      total <- ls[F](dir)
      _ <- Console[F].putStrLn(s"Total $dir size is ${prettyPrint(total)} bytes")
    } yield ()

  private def prettyPrint(i: Long): String = java.text.NumberFormat.getIntegerInstance.format(i)

  def ls[F[_]: Sync: Parallel](dir: File): F[Long] =
    for {
      logger <- Slf4jLogger.create[F]
      (totalSize, subDirs) <- findDirsAndSize(dir)
      _ <- logger.debug(s"${dir.getAbsolutePath} => ${prettyPrint(totalSize)} bytes")
      sum <- subDirs.parTraverse(oneDir => ls(oneDir)).map(_.sum + totalSize)
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
