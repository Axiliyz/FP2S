package postoffice

import postoffice.algebras.LogAlgebra

object LogInterpreter extends LogAlgebra[AppF]:
  def add(message: String): AppF[Unit] =
    stateModify(s => s.copy(logs = s.logs :+ message))

  def take: AppF[List[String]] =
    s => (s.copy(logs = List.empty), s.logs)

  def logAcceptance(parcel: Parcel, cost: Double): AppF[Unit] =
    add(s"[LOG] Accepted #${parcel.id.value}: ${parcel.sender} -> ${parcel.recipient}, ${parcel.weightKg} kg")
  def logTariffCalc(weightKg: Double, cost: Double): AppF[Unit] =
    add(s"[LOG] Tariff: $weightKg kg = $cost rub.")
  def logRejection(recipient: String, weightKg: Double, reason: String): AppF[Unit] =
    add(s"[LOG] REJECTED recipient=$recipient weight=${weightKg}kg reason=$reason")
  def logStorageCharge(parcelId: ParcelId, days: Int, cost: Double): AppF[Unit] =
    add(s"[LOG] Storage #${parcelId.value}: $days days = $cost rub.")
  def logIssuance(parcel: Parcel, dayIssued: Int): AppF[Unit] =
    add(s"[LOG] Issued #${parcel.id.value} to ${parcel.recipient} (day $dayIssued)")