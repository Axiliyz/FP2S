package postoffice

import monads.{Writer, Monoid}
// Этот импорт вносит в контекст given vectorMonoid.
// Без него flatMap у Writer не найдёт Monoid[Log] и не скомпилируется.
import monads.Monoid.given

// Log — псевдоним типа. Все функции работают с Writer[Vector[String], A].
// Это и есть "Logged" монада — Writer, специализированный под строковый лог.
type Log = Vector[String]

object WriterOps:

  // Приватный хелпер: оборачивает одну строку в Vector и вызывает Writer.tell.
  // Существует чтобы не писать Vector(...) в каждой функции.
  private def tell(msg: String): Writer[Log, Unit] =
    Writer.tell(Vector(msg))

  // for здесь — это tell(...).map(_ => parcel).
  // _ <- tell(...) — записать строку в лог, результат () игнорируется.
  // yield parcel — вернуть parcel как значение Writer.
  def logAcceptance(parcel: Parcel): Writer[Log, Parcel] =
    for
      _ <- tell(s"Accepted parcel #${parcel.id}: ${parcel.sender} -> ${parcel.recipient}, ${parcel.weight} kg")
    yield parcel

  // Аналогично — записывает строку в лог, возвращает cost как значение.
  def logTariffCalc(weight: Double, cost: Double): Writer[Log, Double] =
    for
      _ <- tell(s"Tariff calc: weight $weight kg x rate = $cost rub.")
    yield cost

  def logStorageCharge(parcelId: Int, days: Int, cost: Double): Writer[Log, Double] =
    for
      _ <- tell(s"Storage parcel #$parcelId: $days days x rate = $cost rub.")
    yield cost

  def logIssuance(parcel: Parcel, dayIssued: Int): Writer[Log, Parcel] =
    for
      _ <- tell(s"Issued parcel #${parcel.id} to ${parcel.recipient} (day $dayIssued)")
    yield parcel
