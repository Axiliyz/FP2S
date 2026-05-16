import java.util.UUID
import scala.io.StdIn
import scala.util.{Failure, Success, Try}

// Это у нас файл про то, как писать приложения на макро-уровне.

// Для начала задачка:
// Написать кусочек приложения, который регистрирует пользователя через консоль.
// Нужно уметь искать пользователя по имени и по email

// (Очень) наивная реализация
final case class NaiveUser(name:String, email:String)

def naiveRegister():NaiveUser =
  val name = StdIn.readLine("Your name:")
  val email = StdIn.readLine("Your email:")
  NaiveUser(name, email)

def naiveMenu(
    // Заголовок меню
    header:String,
    // Это пары из названия пункта и того, что он делает
    options:Seq[(String, String => Unit)]
):Unit =
  val optionsText = options.zipWithIndex.map { (opt, i) => s"$i. ${opt._1}" }

  println(s"$header \n\n${optionsText.mkString("\n")}")
  val input = StdIn.readLine().split(" ").filter(_.nonEmpty).toSeq
  val tryHandleInput = Try {
    input match
      case Seq(i) =>
        val iOpt = i.toIntOption
        iOpt match
          case Some(value) =>
            options(value)._2("")
          case None =>
            throw new NumberFormatException("wrong int format")
      case Seq(i, arg) =>
        val iOpt = i.toIntOption
        iOpt match
          case Some(value) =>
            options(value)._2(arg)
          case None =>
            throw new NumberFormatException("wrong int format")
      case _ => throw new Exception("Wrong format")
  }
  tryHandleInput match
    case Failure(exception) =>
      println(s"Error: ${exception.getMessage}")
      naiveMenu(header, options)
    case Success(value) => ()

case class UserRepository(
    // Email -> User
    users:Map[String, NaiveUser]
):
  def saved(user: NaiveUser): UserRepository =
    if users.keySet.contains(user.email) then
      throw new Exception("User already exist")
    UserRepository(users + (user.email -> user))

  def findByEmail(email:String):Option[NaiveUser] =
    users.get(email)

  def findByName(name:String):Iterable[NaiveUser] =
    users.values.filter(_.name == name)

def naiveRegisterLoop(userRepository: UserRepository): UserRepository =
  println("User registration")
  val user = naiveRegister()
  Try(userRepository.saved(user)) match
    case Failure(exception) =>
      println(exception.getMessage)
      println("Try again")
      naiveRegisterLoop(userRepository)
    case Success(value) => value

def menuLoop(repo: UserRepository): Unit = naiveMenu(
  "User repo cli. \nType a number to select an option",
  Seq(
    "Register" -> { _ =>
      val newRepo = naiveRegisterLoop(repo)
      menuLoop(newRepo)
    },
    "Find by email" -> { email =>
      repo.findByEmail(email) match
        case Some(value) => println(s"User found: $value")
        case None        => println("No user with such email")
      menuLoop(repo)
    },
    "Find by name" -> { name =>
      val found = repo.findByName(name)
      if found.isEmpty
      then println("No user with such name")
      else println(s"Users with that name: \n${found.mkString("\n")}")
      menuLoop(repo)
    },
    "Exit" -> { _ => () }
  )
)

@main def run():Unit =
  menuLoop(UserRepository(Map.empty))


// Данные, домен
// Это у нас непосредственно типы из предметной области
// Можно ли сделать их алгеброй?
final case class UserId(value: UUID)
final case class UserName private (value: String)
final case class Email private (value: String)


// Можно, а зачем?

object UserName:
  def fromString(raw: String): UserName =
    UserName(raw)

object Email:
  // На самом деле, это тоже довольно хрупкая конструкция.
  def fromString(raw:String):Either[String, Email] =
    if raw.contains("@") then Right(Email(raw))
    else Left(s"Invalid email: $raw")

// val e = Email("sdfg") - не скомпилируется

// зачем: если тип создан, его инварианты уже соблюдены, проверка живёт рядом с типом
// цена: простые обёртки перестают создаваться "одним apply",
//       приходится явно звать конструкторы в companion object


final case class User(
    id: UserId,
    name: UserName,
    email: Email
)

// Данные, искусственность

// Типичный вариант решения через ADT
// Цена: проблемы расширяемости.
enum RegistrationError:
  case InvalidEmail(reason: String)
  case UserAlreadyExists(email: Email)


// Можно и так, и это действительно даст нам некую гибкость.
trait InvalidEmail[E]:
  def invalidEmail(reason: String): E

trait UserAlreadyExists[E]:
  def userAlreadyExists(email: Email): E

// Но всё равно, придётся писать какую-то реализацию, зато теперь для нового куска приложения
// можно использовать не только её, если захочется

enum AppError:
  case InvalidEmail(reason: String)
  case UserAlreadyExists(email: Email)


given InvalidEmail[AppError] with
  def invalidEmail(reason: String): AppError =
    AppError.InvalidEmail(reason)

given UserAlreadyExists[AppError] with
  def userAlreadyExists(email: Email): AppError =
    AppError.UserAlreadyExists(email)

// зачем: гибкость реализации, возможность подбирать реализации
// цена: лишний код, более сложная абстракция


// Следующий шаг: так же явно назвать внешние зависимости приложения.
// В наивной версии они были прямо в коде через StdIn, println, UUID.randomUUID и Map.

trait UI[F[_]]:
  def getUserInput(prompt: String): F[String]
  def showMessage(text: String): F[Unit]

trait UserRepo[F[_]]:
  def save(user: User): F[Unit]
  def findByEmail(email: Email): F[Option[User]]
  def findByName(name: UserName): F[List[User]]

trait IdSource[F[_]]:
  def nextUserId(): F[UserId]

// зачем: бизнес-логика больше не знает, где именно живут пользователи и как устроен ввод-вывод
// цена: теперь приложение нужно "собирать" из алгебр и их интерпретаторов


// Сырые данные живут только на границе приложения.
// Как только мы создали доменные типы, дальше работаем уже только с ними.
// зачем: use case не размазывает по себе правила валидации, а опирается на доменные конструкторы
// цена: для каждого "умного" типа нужно поддерживать отдельную точку создания


// Нужна минимальная абстракция, чтобы описать последовательность шагов в приложении.
trait Monad[F[_]]:
  extension [A](fa: F[A])
    def flatMap[B](f: A => F[B]): F[B]
    def map[B](f: A => B): F[B]

  def pure[A](a: A): F[A]


// регистрация
def register[F[_]:Monad, E](
    rawName: String,
    rawEmail: String
)(using
    ids: IdSource[F],
    users: UserRepo[F],
    invalidEmail: InvalidEmail[E],
    userAlreadyExists: UserAlreadyExists[E]
): F[Either[E, User]] =
  val F = summon[Monad[F]]
  import F.*

  val name = UserName.fromString(rawName)
  Email.fromString(rawEmail).left.map(invalidEmail.invalidEmail) match
    case Left(error) =>
      F.pure(Left(error))
    case Right(email) =>
      users.findByEmail(email).flatMap {
        case Some(_) =>
          F.pure(Left(userAlreadyExists.userAlreadyExists(email)))
        case None =>
          ids.nextUserId().flatMap { id =>
            val user = User(id, name, email)
            users.save(user).map(_ => Right(user))
          }
      }

// зачем: сценарий регистрации теперь можно тестировать и переиспользовать без консоли и без конкретной базы
// цена: сигнатуры становятся тяжелее, потому что зависимости приходится объявлять явно


// Поверх use case можно собрать программу, которая уже знает про конкретный сценарий общения с пользователем.
def registrationProgram[F[_]:Monad, E](using
    ui: UI[F],
    ids: IdSource[F],
    users: UserRepo[F],
    invalidEmail: InvalidEmail[E],
    userAlreadyExists: UserAlreadyExists[E]
): F[Unit] =
  val F = summon[Monad[F]]
  import F.*

  for
    _       <- ui.showMessage("User registration")
    rawName <- ui.getUserInput("Your name:")
    rawEmail <- ui.getUserInput("Your email:")
    result  <- register[F, E](rawName, rawEmail)
    _ <- result match
      case Left(error) =>
        ui.showMessage(s"Registration failed: $error")
      case Right(user) =>
        ui.showMessage(s"Registered: $user")
  yield ()

// зачем: можно отдельно менять бизнес-логику и отдельно менять способ взаимодействия с пользователем
// цена: простой линейный сценарий теперь выражен через несколько слоёв абстракции


type Id[A] = A

given Monad[Id] with
  extension [A](fa: Id[A])
    def flatMap[B](f: A => Id[B]): Id[B] = f(fa)
    def map[B](f: A => B): Id[B] = f(fa)

  def pure[A](a: A): Id[A] = a

given UI[Id] with
  def getUserInput(prompt: String): String = StdIn.readLine(prompt)
  def showMessage(text: String): Unit      = println(text)

given IdSource[Id] with
  def nextUserId(): UserId = UserId(UUID.randomUUID())

final class InMemoryUserRepo extends UserRepo[Id]:
  private var storage: Map[Email, User] = Map.empty

  def save(user: User): Unit =
    storage = storage + (user.email -> user)

  def findByEmail(email: Email): Option[User] =
    storage.get(email)

  def findByName(name: UserName): List[User] =
    storage.values.filter(_.name == name).toList

given UserRepo[Id] = InMemoryUserRepo()

def runBetter(): Unit =
  registrationProgram[Id, AppError]

// зачем: мутабельность, консоль и генерация id никуда не исчезли, но теперь они вытеснены на край системы
// цена: вместо одной наивной функции мы получили набор отдельных сущностей, которые надо понимать и собирать
