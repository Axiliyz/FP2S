package postoffice

import cats.data.StateT
import cats.effect.IO
import postoffice.algebras.LogAlgebra

object LogInterpreter extends LogAlgebra[AppF]:
  private def log(s: String): AppF[Unit] = StateT.liftF(IO(println(s)))
  def logAcceptance(parcel: Parcel, cost: Double): AppF[Unit] =
    log(s"[LOG] Accepted #${parcel.id.value}: ${parcel.sender} -> ${parcel.recipient}, ${parcel.weightKg} kg")
  def logTariffCalc(weightKg: Double, cost: Double): AppF[Unit] =
    log(s"[LOG] Tariff: $weightKg kg = $cost rub.")
  def logRejection(recipient: String, weightKg: Double, reason: String): AppF[Unit] =
    log(s"[LOG] REJECTED recipient=$recipient weight=${weightKg}kg reason=$reason")
  def logStorageCharge(parcelId: ParcelId, days: Int, cost: Double): AppF[Unit] =
    log(s"[LOG] Storage #${parcelId.value}: $days days = $cost rub.")
  def logIssuance(parcel: Parcel, dayIssued: Int): AppF[Unit] =
    log(s"[LOG] Issued #${parcel.id.value} to ${parcel.recipient} (day $dayIssued)")
