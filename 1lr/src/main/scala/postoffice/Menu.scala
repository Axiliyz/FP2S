package postoffice

import monads.IO
import monads.IO.given

// Листовой пункт меню — просто метка и действие, которое выполняется при выборе.
// action — IO[Unit], описывает что произойдёт, не выполняет сразу.
final case class MenuLeaf(
    title: String,
    action: IO[Unit]
) extends MenuOption:
  def show: String = title

// Узел дерева меню — заголовок и список дочерних пунктов (листья или другие узлы).
// extends MenuOption with UserInteraction: сам является и пунктом, и обработчиком.
// children нумеруются автоматически — никакого хардкода "1", "2", "3".
final case class MenuTreeNode(
    title:    String,
    children: Seq[MenuOption]
) extends MenuOption with UserInteraction:

  def show: String = title

  // Выводит пронумерованное меню. zipWithIndex — (элемент, индекс), +1 чтобы начинать с 1.
  private def printMenu: IO[Unit] =
    val lines = children.zipWithIndex.map { (opt, i) => s"${i + 1}) ${opt.show}" }
    val body  = (lines :+ "0) Exit").mkString("\n")
    IO.putStrLn(s"\n=== $title ===\n$body")

  // Обрабатывает строку ввода пользователя.
  // "0" или пустая строка — выход (return IO.pure(())).
  // Число в диапазоне — выбрать дочерний пункт и выполнить его или войти в подменю.
  // Всё остальное — ошибка, вернуться в тот же цикл.
  def handleUserAnswer(answer: String): IO[Unit] =
    answer.trim match
      case "0" | "" =>
        IO.pure(())
      case s =>
        s.toIntOption match
          case Some(n) if n >= 1 && n <= children.size =>
            children(n - 1) match
              case leaf: MenuLeaf =>
                leaf.action.flatMap(_ => userInteractionLoop)
              case node: MenuTreeNode =>
                node.userInteractionLoop.flatMap(_ => userInteractionLoop)
              case other =>
                IO.putStrLn(s"Unhandled menu option: ${other.show}")
                  .flatMap(_ => userInteractionLoop)
          case _ =>
            IO.putStrLn(s"Unknown command: '$s'.")
              .flatMap(_ => userInteractionLoop)

  // Полный цикл: показать меню → прочитать ввод → обработать (handleUserAnswer рекурсивно продолжает).
  def userInteractionLoop: IO[Unit] =
    for
      _      <- printMenu
      answer <- IO.getStrLn
      _      <- handleUserAnswer(answer)
    yield ()
