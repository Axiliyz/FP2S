package postoffice.algebras

import postoffice.{Parcel, ParcelId}

trait StateAlgebra[F[_]]:
  def acceptParcel(parcel: Parcel, cost: Double): F[Unit]
  def pickupParcel(parcelId: ParcelId, storageCost: Double): F[Unit]
  def allParcels: F[List[Parcel]]
  def allIssued: F[List[Parcel]]
  def revenue: F[Double]
  def currentDay: F[Int]
  def nextDay: F[Int]
