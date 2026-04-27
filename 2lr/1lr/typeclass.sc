// Функтор - это трейт?
trait BadFunctor[T]:
  def map[A](f: T => A): BadFunctor[A]

sealed trait MyBadOption[T] extends BadFunctor[T]:
  override def map[A](f: T => A): BadFunctor[A] = ???

// Какая-то коробка
final case class Box[A](value: A) extends BadFunctor[A]:
  def map[B](f: A => B): BadFunctor[B] =
    Box(f(value))

val box: Box[Int] = Box(42)

val mapped1: BadFunctor[String] =
  box.map(_.toString)

// Проблема в том, что map возвращает НЕ Box[String], а BadFunctor[String].

// То есть в месте вызова мы знаем, что там на самом деле Box, но тип был обощён, а информация потеряна.

// Значит рано или поздно, пришлось бы кастить:
val mapped2: Box[String] =
  box.map(_.toString).asInstanceOf[Box[String]]

// Но можно то же самое сделать как тайп-класс.
trait Functor[F[_]]:
  def map[A, B](fa: F[A])(f: A => B): F[B]

trait MyNumeric[T]:
  def times(t:T, x:Double):T
  def sum(x:T, y:T):T

final case class Vec2(x:Double, y:Double)

val vec2Numeric = new MyNumeric[Vec2]:
  override def times(t: Vec2, x: Double): Vec2 = Vec2(t.x*x, t.y*x)

  override def sum(x: Vec2, y: Vec2): Vec2 = Vec2(x.x + y.x, x.y + y.y)

val intNumeric = new MyNumeric[Int]:
  override def times(t: Int, x: Double): Int = scala.math.floor(t*x).toInt

  override def sum(x: Int, y: Int): Int = x + y

def f[N](x:N, y:N)(ev:MyNumeric[N]) =
  ev.sum(x, y)

f( Vec2(1,2),Vec2(3,4))(vec2Numeric)
f(1,2)(intNumeric)

// ВАЖНО: тот факт, что в TF и здесь используется одна и та-же фича языка (type-level polymorphism)
// ещё не значит, что это одно и то-же. Это разные вещи. Не путайте их.

// Теперь сам контейнер вообще ни от чего не обязан наследоваться.
final case class GoodBox[A](value: A)

// Например, здесь мы говорим: "существует реализация Functor[GoodBox]".
given Functor[GoodBox] with
  def map[A, B](fa: GoodBox[A])(f: A => B): GoodBox[B] =
    GoodBox(f(fa.value))

given Functor[Option] with
  def map[A, B](fa: Option[A])(f: A => B): Option[B] =
    fa match
      case Some(a) => Some(f(a))
      case None    => None

val goodBox: GoodBox[Int] = GoodBox(42)

// `summon` — это способ запросить этот самый объект по-умолчанию из контекста.
// Здесь мы достаём Functor[GoodBox] и пользуемся его методом map.
// По сути мы использовали тип вместо имени.
val goodMapped: GoodBox[String] =
  summon[Functor[GoodBox]].map(goodBox)(_.toString)

val optionMapped: Option[String] =
  summon[Functor[Option]].map(Option(10))(n => s"n = $n")

// Если посмотреть на типы, видно, что наш новый подход с тайп-классами сохранил инормацию о типе.
// Правда, проблема: голый typeclass неудобен.

val x1 =
  summon[Functor[GoodBox]].map(GoodBox(100))(_ + 1)

val x2 =
  summon[Functor[Option]].map(Option(100))(_ + 1)

// Хотелось бы писать как метод, Option(100).map

// `given` — это объявление некоторого объекта - в каком-то смысле, объектом по-умолчанию. (в контексте)
// summon - вытаскивание из контекста объекта по-умолчанию
// using - использование по-умолчанию объекта по умолчанию

given Int = 42

val answer = summon[Int]

// Результат зависит от контекста
def theAnswerToLifeDeathTheUniverseAndEverything(using a:Int) =
  a

theAnswerToLifeDeathTheUniverseAndEverything

object E {
  given Int = 43
  val x = theAnswerToLifeDeathTheUniverseAndEverything
}

E.x

// И немного неочевидная передача из контекста в контекст
def anotherAnswer(using Int) =
  summon[Int]

// `extension` позволяет "добавить метод" типу,
// не меняя сам тип и не используя наследование.
//
// То есть ниже мы НЕ открываем класс GoodBox заново,
// а просто говорим:
// "у GoodBox[A] теперь будет доступен метод mapBox(...)".
extension [A](box: GoodBox[A])
  def mapBox[B](f: A => B): GoodBox[B] =
    GoodBox(f(box.value))

val extBox1: GoodBox[String] =
  GoodBox(123).mapBox(n => s"box = $n")

// Для Option, List и любого другого контейнера надо писать заново.

// С extensions можно делать много разного, но это не тема курса.
// Здесь одни нам нужны для достижения пика удобства и практичности.
// Мы говорим:
// "Если для F есть Functor[F],
//  то у любого F[A] появляется удобный метод mapF".
extension [F[_], A](fa: F[A])
  def map[B](f: A => B)(using functor: Functor[F]): F[B] =
    functor.map(fa)(f)

val extBox2: GoodBox[String] =
  GoodBox(999).map(n => s"goodBox = $n")

val extOpt: Option[String] =
  Option(555).map(n => s"option = $n")

case class AnotherBox[T](x:T)

given Functor[AnotherBox] with
  override def map[A, B](fa: AnotherBox[A])(f: A => B): AnotherBox[B] =
    AnotherBox(f(fa.x))
  
val l = AnotherBox(10).map(_.toString)

// Вопросы?

// Ещё немного про given.
// Есть парный к given using: Если в контексте есть given нужного типа, то будет использован именно он.
// Здесь творится просто чернющая магия, по поверьте мне, оно работает.
// Здесь (using Functor[F]) выступает доказательством того, что из контекста будет захвачен экземпляр функтора.
def stringify2[F[_], A](fa: F[A])(using Functor[F]): F[String] =
  // А внутри его можно использовать как given.
  summon[Functor[F]].map(fa)(_.toString)
  // Так тоже будет работать, так как у нас есть extension, но если его не будет - и работать не будет, очевидно.
  fa.map(_.toString)

val s1: Option[String] =
  stringify2(Option(42))

val s2: GoodBox[String] =
  stringify2(GoodBox(42))



// В Scala есть более красивая запись того же самого.
// Вместо `(using Functor[F])` можно написать `[F[_]: Functor]`.
def stringify3[F[_]: Functor, A](fa: F[A]): F[String] =
  // Логика точь-в-точь как и
  summon[Functor[F]].map(fa)(_.toString)
  // Так тоже будет работать, так как у нас есть extension, но если его не будет - и работать не будет, очевидно.
  fa.map(_.toString)

// Это ровно тот же смысл.
// Просто запись компактнее.

val s3: Option[String] =
  stringify3(Option(100))

val s4: GoodBox[String] =
  stringify3(GoodBox(100))


