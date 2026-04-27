package postoffice

import cats.effect.{IO, Ref}
import postoffice.algebras.*

given Monad[IO] with
  def pure[A](a: A): IO[A]                           = IO.pure(a)
  def flatMap[A, B](fa: IO[A])(f: A => IO[B]): IO[B] = fa.flatMap(f)

final class ConfigInterpreter(cfg: PostConfig) extends ConfigAlgebra[IO]:
  def acceptanceCost(weightKg: Double): IO[Double] =
    IO.pure(weightKg * cfg.taxPerKg)
  def canAccept(weightKg: Double): IO[Boolean] =
    IO.pure(weightKg > 0 && weightKg <= cfg.maxWeightKg)
  def storageCost(days: Int): IO[Double] =
    IO.pure(days * cfg.storageRatePerDay)
  def packageClass(weightKg: Double): IO[(PackageClass, PackagingRule)] =
    IO.pure {
      val cls  = cfg.weightClasses
        .find((threshold, _) => weightKg <= threshold)
        .map(_(1))
        .getOrElse(PackageClass.Heavy)
      val rule = cfg.packagingRules.getOrElse(cls, PackagingRule.Crate)
      (cls, rule)
    }

final class LogInterpreter(logRef: Ref[IO, Vector[String]]) extends LogAlgebra[IO]:
  private def append(entry: String): IO[Unit] = logRef.update(_ :+ entry)

  def logAcceptance(parcel: Parcel, cost: Double): IO[Unit] =
    append(s"Accepted parcel #${parcel.id.value}: ${parcel.sender} -> ${parcel.recipient}, ${parcel.weightKg} kg")

  def logTariffCalc(weightKg: Double, cost: Double): IO[Unit] =
    append(s"Tariff calc: weight $weightKg kg x rate = $cost rub.")

  def logRejection(recipient: String, weightKg: Double, reason: String): IO[Unit] =
    append(s"REJECTED recipient=$recipient weight=${weightKg}kg reason=$reason")

  def logStorageCharge(parcelId: ParcelId, days: Int, cost: Double): IO[Unit] =
    append(s"Storage parcel #${parcelId.value}: $days days x rate = $cost rub.")

  def logIssuance(parcel: Parcel, dayIssued: Int): IO[Unit] =
    append(s"Issued parcel #${parcel.id.value} to ${parcel.recipient} (day $dayIssued)")

  def getLog: IO[Vector[String]] = logRef.get

final class StateInterpreter(stateRef: Ref[IO, PostState]) extends StateAlgebra[IO]:
  def nextId: IO[ParcelId] =
    stateRef.modify { s => (s.copy(nextId = s.nextId + 1), ParcelId(s.nextId)) }

  def acceptParcel(parcel: Parcel, cost: Double): IO[Unit] =
    stateRef.update { s =>
      s.copy(
        parcels = s.parcels + (parcel.id -> parcel),
        revenue = s.revenue + cost,
      )
    }

  def pickupParcel(parcelId: ParcelId, storageCost: Double): IO[Option[Parcel]] =
    stateRef.modify { s =>
      val found = s.parcels.get(parcelId)
      val next  = found.fold(s) { p =>
        s.copy(
          parcels = s.parcels - p.id,
          issued  = s.issued :+ p,
          revenue = s.revenue + storageCost,
        )
      }
      (next, found)
    }

  def allParcels: IO[List[Parcel]]  = stateRef.get.map(_.parcels.values.toList)
  def allIssued:  IO[List[Parcel]]  = stateRef.get.map(_.issued)
  def revenue:    IO[Double]        = stateRef.get.map(_.revenue)
  def currentDay: IO[Int]           = stateRef.get.map(_.currentDay)

  def nextDay: IO[Int] =
    stateRef.modify { s =>
      val d = s.currentDay + 1
      (s.copy(currentDay = d), d)
    }

val ConsoleInterpreter: ConsoleAlgebra[IO] = new ConsoleAlgebra[IO]:
  def readLine: IO[String]          = IO(Option(scala.io.StdIn.readLine()).getOrElse(""))
  def putStrLn(s: String): IO[Unit] = IO(println(s))
  def putStr(s: String): IO[Unit]   = IO(print(s))

final case class Algebras[F[_]](
  cfg:     ConfigAlgebra[F],
  log:     LogAlgebra[F],
  state:   StateAlgebra[F],
  console: ConsoleAlgebra[F],
)

object Interpreters:
  def make(cfg: PostConfig): IO[Algebras[IO]] =
    for
      logRef   <- Ref.of[IO, Vector[String]](Vector.empty)
      stateRef <- Ref.of[IO, PostState](PostState.empty)
    yield Algebras(
      cfg     = ConfigInterpreter(cfg),
      log     = LogInterpreter(logRef),
      state   = StateInterpreter(stateRef),
      console = ConsoleInterpreter,
    )
