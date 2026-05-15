package postoffice

import cats.data.{State, StateT}
import cats.effect.IO
import cats.implicits.*
import postoffice.algebras.*

type AppF[A]      = StateT[IO, PostState, A]
type ScriptedF[A] = State[ScriptedState, A]

case class ScriptedState(
  post:   PostState,
  log:    Vector[String],
  inputs: List[String],
)

object ScriptedState:
  def initial(inputs: List[String]): ScriptedState =
    ScriptedState(PostState.empty, Vector.empty, inputs)

given Monad[AppF] with
  def pure[A](a: A): AppF[A]                               = StateT.pure(a)
  def flatMap[A, B](fa: AppF[A])(f: A => AppF[B]): AppF[B] = fa.flatMap(f)

given Monad[ScriptedF] with
  def pure[A](a: A): ScriptedF[A]                                    = State.pure(a)
  def flatMap[A, B](fa: ScriptedF[A])(f: A => ScriptedF[B]): ScriptedF[B] = fa.flatMap(f)

final class IOConfigInterpreter(cfg: PostConfig) extends ConfigAlgebra[AppF]:
  def acceptanceCost(weightKg: Double): AppF[Double]  = StateT.pure(weightKg * cfg.taxPerKg)
  def canAccept(weightKg: Double): AppF[Boolean]      = StateT.pure(weightKg > 0 && weightKg <= cfg.maxWeightKg)
  def storageCost(days: Int): AppF[Double]            = StateT.pure(days * cfg.storageRatePerDay)
  def packageClass(weightKg: Double): AppF[(PackageClass, PackagingRule)] = StateT.pure {
    val cls  = cfg.weightClasses.find((t, _) => weightKg <= t).map(_(1)).getOrElse(PackageClass.Heavy)
    val rule = cfg.packagingRules.getOrElse(cls, PackagingRule.Crate)
    (cls, rule)
  }

final class ScriptedConfigInterpreter(cfg: PostConfig) extends ConfigAlgebra[ScriptedF]:
  def acceptanceCost(weightKg: Double): ScriptedF[Double]  = State.pure(weightKg * cfg.taxPerKg)
  def canAccept(weightKg: Double): ScriptedF[Boolean]      = State.pure(weightKg > 0 && weightKg <= cfg.maxWeightKg)
  def storageCost(days: Int): ScriptedF[Double]            = State.pure(days * cfg.storageRatePerDay)
  def packageClass(weightKg: Double): ScriptedF[(PackageClass, PackagingRule)] = State.pure {
    val cls  = cfg.weightClasses.find((t, _) => weightKg <= t).map(_(1)).getOrElse(PackageClass.Heavy)
    val rule = cfg.packagingRules.getOrElse(cls, PackagingRule.Crate)
    (cls, rule)
  }

object IOLogInterpreter extends LogAlgebra[AppF]:
  private def println(s: String): AppF[Unit] = StateT.liftF(IO(Predef.println(s)))
  def logAcceptance(parcel: Parcel, cost: Double): AppF[Unit] =
    println(s"[LOG] Accepted #${parcel.id.value}: ${parcel.sender} -> ${parcel.recipient}, ${parcel.weightKg} kg")
  def logTariffCalc(weightKg: Double, cost: Double): AppF[Unit] =
    println(s"[LOG] Tariff: $weightKg kg = $cost rub.")
  def logRejection(recipient: String, weightKg: Double, reason: String): AppF[Unit] =
    println(s"[LOG] REJECTED recipient=$recipient weight=${weightKg}kg reason=$reason")
  def logStorageCharge(parcelId: ParcelId, days: Int, cost: Double): AppF[Unit] =
    println(s"[LOG] Storage #${parcelId.value}: $days days = $cost rub.")
  def logIssuance(parcel: Parcel, dayIssued: Int): AppF[Unit] =
    println(s"[LOG] Issued #${parcel.id.value} to ${parcel.recipient} (day $dayIssued)")

object IOStateInterpreter extends StateAlgebra[AppF]:
  def nextId: AppF[ParcelId] = StateT { s =>
    IO.pure((s.copy(nextId = s.nextId + 1), ParcelId(s.nextId)))
  }
  def acceptParcel(parcel: Parcel, cost: Double): AppF[Unit] =
    StateT.modify(s => s.copy(parcels = s.parcels + (parcel.id -> parcel), revenue = s.revenue + cost))
  def pickupParcel(parcelId: ParcelId, storageCost: Double): AppF[Option[Parcel]] =
    StateT { s =>
      val found = s.parcels.get(parcelId)
      val next  = found.fold(s) { p =>
        s.copy(parcels = s.parcels - p.id, issued = s.issued :+ p, revenue = s.revenue + storageCost)
      }
      IO.pure((next, found))
    }
  def allParcels: AppF[List[Parcel]] = StateT.inspect(_.parcels.values.toList)
  def allIssued:  AppF[List[Parcel]] = StateT.inspect(_.issued)
  def revenue:    AppF[Double]       = StateT.inspect(_.revenue)
  def currentDay: AppF[Int]          = StateT.inspect(_.currentDay)
  def nextDay: AppF[Int] = StateT { s =>
    val d = s.currentDay + 1
    IO.pure((s.copy(currentDay = d), d))
  }

object IOConsole extends ConsoleAlgebra[AppF]:
  def readLine: AppF[String]          = StateT.liftF(IO(Option(scala.io.StdIn.readLine()).getOrElse("")))
  def putStr(s: String): AppF[Unit]   = StateT.liftF(IO(print(s)))
  def putStrLn(s: String): AppF[Unit] = putStr(s + "\n")

object ScriptedLogInterpreter extends LogAlgebra[ScriptedF]:
  private def append(entry: String): ScriptedF[Unit] =
    State.modify(s => s.copy(log = s.log :+ entry))
  def logAcceptance(parcel: Parcel, cost: Double): ScriptedF[Unit] =
    append(s"Accepted #${parcel.id.value}: ${parcel.sender} -> ${parcel.recipient}, ${parcel.weightKg} kg")
  def logTariffCalc(weightKg: Double, cost: Double): ScriptedF[Unit] =
    append(s"Tariff: $weightKg kg = $cost rub.")
  def logRejection(recipient: String, weightKg: Double, reason: String): ScriptedF[Unit] =
    append(s"REJECTED recipient=$recipient weight=${weightKg}kg reason=$reason")
  def logStorageCharge(parcelId: ParcelId, days: Int, cost: Double): ScriptedF[Unit] =
    append(s"Storage #${parcelId.value}: $days days = $cost rub.")
  def logIssuance(parcel: Parcel, dayIssued: Int): ScriptedF[Unit] =
    append(s"Issued #${parcel.id.value} to ${parcel.recipient} (day $dayIssued)")

object ScriptedStateInterpreter extends StateAlgebra[ScriptedF]:
  def nextId: ScriptedF[ParcelId] = State { s =>
    val ps = s.post
    (s.copy(post = ps.copy(nextId = ps.nextId + 1)), ParcelId(ps.nextId))
  }
  def acceptParcel(parcel: Parcel, cost: Double): ScriptedF[Unit] =
    State.modify { s =>
      val ps = s.post
      s.copy(post = ps.copy(parcels = ps.parcels + (parcel.id -> parcel), revenue = ps.revenue + cost))
    }
  def pickupParcel(parcelId: ParcelId, storageCost: Double): ScriptedF[Option[Parcel]] = State { s =>
    val ps    = s.post
    val found = ps.parcels.get(parcelId)
    val newPs = found.fold(ps) { p =>
      ps.copy(parcels = ps.parcels - p.id, issued = ps.issued :+ p, revenue = ps.revenue + storageCost)
    }
    (s.copy(post = newPs), found)
  }
  def allParcels: ScriptedF[List[Parcel]] = State.inspect(_.post.parcels.values.toList)
  def allIssued:  ScriptedF[List[Parcel]] = State.inspect(_.post.issued)
  def revenue:    ScriptedF[Double]       = State.inspect(_.post.revenue)
  def currentDay: ScriptedF[Int]          = State.inspect(_.post.currentDay)
  def nextDay: ScriptedF[Int] = State { s =>
    val d = s.post.currentDay + 1
    (s.copy(post = s.post.copy(currentDay = d)), d)
  }

object ScriptedConsole extends ConsoleAlgebra[ScriptedF]:
  def readLine: ScriptedF[String] = State {
    case s if s.inputs.nonEmpty => (s.copy(inputs = s.inputs.tail), s.inputs.head)
    case s                      => (s, "")
  }
  def putStr(str: String): ScriptedF[Unit]   = State.pure(())
  def putStrLn(str: String): ScriptedF[Unit] = putStr(str + "\n")

final case class Algebras[F[_]](
  cfg:     ConfigAlgebra[F],
  log:     LogAlgebra[F],
  state:   StateAlgebra[F],
  console: ConsoleAlgebra[F],
)

object Interpreters:
  def makeIO(cfg: PostConfig): Algebras[AppF] =
    Algebras(
      cfg     = IOConfigInterpreter(cfg),
      log     = IOLogInterpreter,
      state   = IOStateInterpreter,
      console = IOConsole,
    )

  def makeScripted(cfg: PostConfig): Algebras[ScriptedF] =
    Algebras(
      cfg     = ScriptedConfigInterpreter(cfg),
      log     = ScriptedLogInterpreter,
      state   = ScriptedStateInterpreter,
      console = ScriptedConsole,
    )
