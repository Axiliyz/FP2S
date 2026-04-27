package postoffice

import postoffice.Monad.*
import postoffice.algebras.ConsoleAlgebra

sealed trait MenuOption:
  def show: String

trait UserInteraction[F[_]]:
  def handleUserAnswer(answer: String): F[Unit]
  def userInteractionLoop: F[Unit]

final case class MenuLeaf[F[_]](title: String, action: F[Unit]) extends MenuOption:
  def show: String = title

// titleF — эффект, вычисляющий заголовок меню перед каждым показом.
// Это позволяет заголовку отражать актуальное состояние (день, выручку и т.д.)
// без того чтобы MenuTreeNode знал о StateAlgebra.
final case class MenuTreeNode[F[_]: Monad](
  titleF:   F[String],
  children: Seq[MenuOption],
)(using console: ConsoleAlgebra[F])
  extends MenuOption with UserInteraction[F]:

  def show: String = "menu"

  private def printMenu: F[Unit] =
    for
      title <- titleF
      lines  = children.zipWithIndex.map { (opt, i) => s"${i + 1}) ${opt.show}" }
      body   = (lines :+ "0) Exit").mkString("\n")
      _     <- console.putStrLn(s"\n=== $title ===\n$body")
    yield ()

  def handleUserAnswer(answer: String): F[Unit] =
    answer.trim match
      case "0" | "" =>
        Monad[F].pure(())
      case s =>
        s.toIntOption match
          case Some(n) if n >= 1 && n <= children.size =>
            children(n - 1) match
              case leaf: MenuLeaf[F @unchecked]     => leaf.action >> userInteractionLoop
              case node: MenuTreeNode[F @unchecked] => node.userInteractionLoop >> userInteractionLoop
          case _ =>
            console.putStrLn(s"Unknown command: '$s'") >> userInteractionLoop

  def userInteractionLoop: F[Unit] =
    for
      _      <- printMenu
      answer <- console.readLine
      _      <- handleUserAnswer(answer)
    yield ()
