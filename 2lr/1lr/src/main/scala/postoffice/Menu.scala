package postoffice

import monads.IO
import monads.IO.given

// Листовой пункт меню — только метка и действие.
final case class MenuLeaf(
    title:  String,
    action: IO[Unit]
) extends MenuOption:
  def show: String = title

// Узел дерева меню — только данные: заголовок и дочерние пункты.
// SRP: не знает ничего про отображение, ввод и навигацию.
final case class MenuTreeNode(
    title:    String,
    children: Seq[MenuOption]
) extends MenuOption:
  def show: String = title

// Отвечает только за отображение: печатает пронумерованные пункты узла.
// SRP: не читает ввод, не маршрутизирует.
object MenuRenderer:
  def render(node: MenuTreeNode): IO[Unit] =
    val lines = node.children.zipWithIndex.map { (opt, i) => s"${i + 1}) ${opt.show}" }
    val body  = (lines :+ "0) Back / Exit").mkString("\n")
    IO.putStrLn(s"\n=== ${node.title} ===\n$body")

// Отвечает только за цикл взаимодействия: читает ввод, парсит, маршрутизирует.
// SRP: не знает про бизнес-логику, только про навигацию по дереву.
// Реализует UserInteraction — именно здесь находится userInteractionLoop.
final class MenuNavigator(root: MenuTreeNode) extends UserInteraction:

  def userInteractionLoop: IO[Unit] = loopNode(root)

  private def loopNode(node: MenuTreeNode): IO[Unit] =
    for
      _      <- MenuRenderer.render(node)
      answer <- IO.getStrLn
      _      <- route(node, answer.trim)
    yield ()

  // Чистый роутинг: парсит строку и решает что делать дальше.
  private def route(node: MenuTreeNode, answer: String): IO[Unit] =
    answer match
      case "0" | "" =>
        IO.pure(())
      case s =>
        s.toIntOption match
          case Some(n) if n >= 1 && n <= node.children.size =>
            dispatch(node, node.children(n - 1))
          case _ =>
            IO.putStrLn(s"Unknown command: '$s'.").flatMap(_ => loopNode(node))

  // Dispatch: решает как исполнить выбранный пункт, затем возвращает в текущий узел.
  private def dispatch(current: MenuTreeNode, selected: MenuOption): IO[Unit] =
    selected match
      case leaf: MenuLeaf     => leaf.action.flatMap(_ => loopNode(current))
      case node: MenuTreeNode => loopNode(node).flatMap(_ => loopNode(current))
