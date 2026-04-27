package postoffice

import monads.IO

// Scala 3 enum с параметрами. label доступен на каждом варианте.
// PackageClass.Light.label == "Light". Используется в чеке для вывода класса посылки.
enum PackageClass(val label: String):
  case Light  extends PackageClass("Light")
  case Medium extends PackageClass("Medium")
  case Heavy  extends PackageClass("Heavy")

// Тип упаковки. PackagingRule.Envelope.description == "envelope".
enum PackagingRule(val description: String):
  case Envelope extends PackagingRule("envelope")
  case Box      extends PackagingRule("box")
  case Crate    extends PackagingRule("crate")

// Неизменяемая конфигурация — это Env для Reader-монады.
// taxPerKg — тариф за кг. maxWeight — максимальный принимаемый вес.
// storageFeePerDay — стоимость хранения в день.
// weightClasses — список порогов: (максимальный вес, класс), проверяется по порядку.
// packagingRules — словарь: класс посылки → правило упаковки.
final case class PostOfficeConfig(
    taxPerKg:         Double,
    maxWeight:        Double,
    storageFeePerDay: Double,
    weightClasses: List[(Double, PackageClass)],
    packagingRules: Map[PackageClass, PackagingRule]
)

object PostOfficeConfig:
  // Конкретные значения конфига, используемые в App.
  // 50 руб/кг, максимум 30 кг, хранение 10 руб/день.
  // Список weightClasses проверяется слева направо — первый подходящий порог выигрывает.
  val default: PostOfficeConfig = PostOfficeConfig(
    taxPerKg         = 50.0,
    maxWeight        = 30.0,
    storageFeePerDay = 10.0,
    weightClasses = List(
      (5.0,  PackageClass.Light),
      (15.0, PackageClass.Medium),
      (30.0, PackageClass.Heavy)
    ),
    packagingRules = Map(
      PackageClass.Light  -> PackagingRule.Envelope,
      PackageClass.Medium -> PackagingRule.Box,
      PackageClass.Heavy  -> PackagingRule.Crate
    )
  )

// Данные посылки. acceptedDay — номер дня, когда приняли.
// Нужен для подсчёта стоимости хранения: (currentDay - acceptedDay) * storageFeePerDay.
final case class Parcel(
    id:          Int,
    sender:      String,
    recipient:   String,
    weight:      Double,
    acceptedDay: Int
)

// Это S для State-монады — полное состояние системы.
// queue — очередь клиентов по имени.
// acceptedParcels — посылки на хранении, ключ — id.
// issuedParcels — выданные посылки (для итогового отчёта).
// revenue — накопленная выручка.
// currentDay — текущий день симуляции.
// nextId — монотонно растущий счётчик id посылок.
final case class PostOfficeState(
    queue:           List[String],
    acceptedParcels: Map[Int, Parcel],
    issuedParcels:   List[Parcel],
    revenue:         Double,
    currentDay:      Int,
    nextId:          Int
)

object PostOfficeState:
  // Начальное состояние: всё пустое, первый день, id начинается с 1.
  val empty: PostOfficeState = PostOfficeState(
    queue           = List.empty,
    acceptedParcels = Map.empty,
    issuedParcels   = List.empty,
    revenue         = 0.0,
    currentDay      = 1,
    nextId          = 1
  )

// Абстракция пункта меню — знает только как себя показать.
trait MenuOption:
  def show: String

// Абстракция взаимодействия с пользователем через IO.
// handleUserAnswer — обработать ввод и вернуть следующее состояние.
// userInteractionLoop — полный цикл: показать меню, прочитать ввод, обработать, повторить.
trait UserInteraction:
  def handleUserAnswer(answer: String): IO[Unit]
  def userInteractionLoop: IO[Unit]

// Extension-методы для Parcel — добавлены снаружи без наследования.
// Parcel — чистые данные, ничего не наследует.
extension (parcel: Parcel)
  // currentDay передаётся явно — Parcel не знает о текущем состоянии системы.
  def storedDays(currentDay: Int): Int = currentDay - parcel.acceptedDay

  // Краткое описание для вывода в консоль. Используется в issueFlow и финальном отчёте.
  def summary: String =
    s"#${parcel.id}: ${parcel.sender} -> ${parcel.recipient}, ${parcel.weight} kg (day ${parcel.acceptedDay})"
