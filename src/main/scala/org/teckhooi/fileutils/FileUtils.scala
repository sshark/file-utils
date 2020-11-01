package org.teckhooi.fileutils

import java.io.File

import cats.Parallel
import cats.effect.{ContextShift, IO, Sync}
import cats.implicits._
import ch.qos.logback.classic.Logger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger

trait FileUtils[F[_]] {
  def ls(rootDir: String, verbose: Boolean = false): F[Long]
  def rm[A](rootDir: String, patterns: List[String], verbose: Boolean = false): F[Boolean]
}

object FileUtils {
  def apply[F[_]](implicit F: FileUtils[F]): FileUtils[F] = F

  object implicits {
    implicit def ioFileUtils(implicit cs: ContextShift[IO]): FileUtils[IO] = new FileUtilsImpl[IO]
  }
}

class FileUtilsImpl[F[_]: Sync: Parallel] extends FileUtils[F] {
  override def ls(rootDir: String, verbose: Boolean): F[Long] =
    for {
      _ <- if (verbose)
        Sync[F].delay(
          org.slf4j.LoggerFactory
            .getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)
            .asInstanceOf[Logger]
            .setLevel(ch.qos.logback.classic.Level.DEBUG))
      else Sync[F].unit
      logger               <- Slf4jLogger.create[F]
      dir                  <- Sync[F].delay(new File(rootDir))
      (totalSize, subDirs) <- findDirsAndSize(dir)
      _                    <- logger.debug(s"${dir.getCanonicalPath} => ${prettyPrint(totalSize)} bytes")
      sum                  <- subDirs.parTraverse(oneDir => ls(oneDir.getCanonicalPath)).map(_.sum + totalSize)
    } yield sum

  private def findDirsAndSize(baseDir: File): F[(Long, List[File])] =
    Sync[F].delay(
      Option(baseDir.listFiles())
        .map { xs =>
          xs.map(f => if (f.isFile) f.length.asLeft[File] else f.asRight[Long]).foldLeft((0L, List.empty[File])) {
            case ((fs, xs), x) => x.fold(newLen => (newLen + fs, xs), newFile => (fs, newFile :: xs))
          }
        }
        .getOrElse((0, Nil)))

  override def rm[A](rootDir: String, patterns: List[String], verbose: Boolean): F[Boolean] = Sync[F].pure(true)
}
