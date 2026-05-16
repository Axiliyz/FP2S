package postoffice.algebras

import postoffice.{Parcel, ParcelId}

trait LogAlgebra[F[_]]:
  def logAcceptance(parcel: Parcel, cost: Double): F[Unit]
  def logTariffCalc(weightKg: Double, cost: Double): F[Unit]
  def logRejection(recipient: String, weightKg: Double, reason: String): F[Unit]
  def logStorageCharge(parcelId: ParcelId, days: Int, cost: Double): F[Unit]
  def logIssuance(parcel: Parcel, dayIssued: Int): F[Unit]
