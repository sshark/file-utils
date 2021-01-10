package org.teckhooi.fileutils

import cats.Parallel
import cats.effect.{ContextShift, IO, Sync}
import cats.implicits._
import ch.qos.logback.classic.Logger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger

import java.io.File

trait DirsUtils[F[_]] {
  def dirSize(rootDir: String, verbose: Boolean = false): F[Long]
  def rm[A](rootDir: String, patterns: List[String], verbose: Boolean = false): F[Boolean]
  def dirExists(dir: String): F[Option[String]]
}

trait DirsUtilsSyncRequired[F[_]] {
  def dirSize(path: File, verbose: Boolean = false): F[Long]
  def rm[A](path: File, patterns: List[String], verbose: Boolean = false): F[Boolean]
  def dirExists(path: File): F[Option[File]]
}

object DirsUtilsSyncRequired {
  def apply[F[_]](implicit F: DirsUtilsSyncRequired[F]): DirsUtilsSyncRequired[F] = F

  object implicits {
    import FilesUtilsSyncRequired.implicits._

    implicit def ioDirsUtils(implicit cs: ContextShift[IO]): DirsUtilsSyncRequired[IO] = new SyncDirsUtils[IO]
  }
}

class SyncDirsUtils[F[_]: Sync: Parallel: FilesUtilsSyncRequired] extends DirsUtilsSyncRequired[F] {
  override def dirExists(path: File): F[Option[File]] =
    for {
      exists  <- Sync[F].delay(path.exists())
    } yield Option.when(exists)(path)

  override def dirSize(path: File, verbose: Boolean): F[Long] =
    for {
      _ <- if (verbose)
        Sync[F].delay(
          org.slf4j.LoggerFactory
            .getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)
            .asInstanceOf[Logger]
            .setLevel(ch.qos.logback.classic.Level.DEBUG))
      else Sync[F].unit
      logger               <- Slf4jLogger.create[F]
      (totalSize, subDirs) <- findDirsAndSize(path)
      fullPath             <- FilesUtilsSyncRequired[F].fullPath(path)
      _                    <- logger.debug(s"$fullPath => ${prettyPrint(totalSize)} bytes")
      sum                  <- subDirs.parTraverse(oneDir => dirSize(oneDir)).map(_.sum + totalSize)
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

  override def rm[A](rootDir: File, patterns: List[String], verbose: Boolean): F[Boolean] = Sync[F].pure(true)
}
