package monads

// Writer[Log, A] несёт пару: накопленный лог Log и значение A.
// Лог write-only: внутри цепочки можно только добавлять, читать нельзя.
// Log в проекте = Vector[String] — это "Logged" монада.
final case class Writer[Log, A](log: Log, value: A):

  // Применяет f только к value. Лог при этом не меняется — map не добавляет записей.
  def map[B](f: A => B): Writer[Log, B] =
    Writer(log, f(value))

  // IE (Implicit Evidence): using ev: Monoid[Log] — доказательство, что Log можно объединять.
  // Без этого доказательства скомбинировать логи невозможно — метод не скомпилируется.
  // Логика: применить f к value, получить (log2, b), новый лог = combine(старый, log2).
  def flatMap[B](f: A => Writer[Log, B])(using ev: Monoid[Log]): Writer[Log, B] =
    val Writer(log2, b) = f(value)
    Writer(ev.combine(log, log2), b)

// Алгебраический интерфейс — предусловие для работы Writer.
// empty — нейтральный элемент: combine(empty, x) == x и combine(x, empty) == x.
// combine — ассоциативная операция объединения.
trait Monoid[A]:
  def empty: A
  def combine(x: A, y: A): A

object Monoid:
  // given — конкретная реализация Monoid для Vector[T].
  // Нейтральный элемент: пустой вектор. Операция: конкатенация (++).
  // Этот given подхватывается компилятором везде, где нужен Monoid[Vector[?]].
  given vectorMonoid[T]: Monoid[Vector[T]] with
    def empty: Vector[T]                           = Vector.empty
    def combine(x: Vector[T], y: Vector[T]): Vector[T] = x ++ y

object Writer:

  // Единственный способ записать в лог.
  // value = () — ничего содержательного, весь смысл в добавлении l.
  // В for-comprehension: _ <- Writer.tell(...) — добавить запись, продолжить.
  def tell[Log](l: Log): Writer[Log, Unit] = Writer(l, ())

  // Нейтральный элемент: лог пустой (ev.empty = Vector()), значение — a.
  // "Вычисление без записей в лог".
  def pure[Log, A](a: A)(using ev: Monoid[Log]): Writer[Log, A] =
    Writer(ev.empty, a)

  // Условный given: instance Monad для Writer[Log, ?] существует ТОЛЬКО если
  // существует Monoid[Log]. Это IE — компилятор проверяет предусловие при компиляции.
  // Без import monads.Monoid.given в App.scala этот given не будет найден.
  given writerMonad[Log](using ev: Monoid[Log]): Monad[[A] =>> Writer[Log, A]] with
    def pure[A](a: A): Writer[Log, A] = Writer.pure(a)
    def flatMap[A, B](ma: Writer[Log, A])(f: A => Writer[Log, B]): Writer[Log, B] =
      ma.flatMap(f)
