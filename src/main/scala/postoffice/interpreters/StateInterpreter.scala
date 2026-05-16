package postoffice

import postoffice.algebras.StateAlgebra

object StateInterpreter extends StateAlgebra[AppF]:
  def acceptParcel(parcel: Parcel, cost: Double): AppF[Unit] =
    stateModify(s => s.copy(parcels = s.parcels + (parcel.id -> parcel), revenue = s.revenue + cost))

  def pickupParcel(parcelId: ParcelId, storageCost: Double): AppF[Unit] =
    stateModify { s =>
      s.parcels.get(parcelId).fold(s) { p =>
        s.copy(parcels = s.parcels - p.id, issued = s.issued :+ p, revenue = s.revenue + storageCost)
      }
    }

  def allParcels: AppF[List[Parcel]] =
    stateInspect(_.parcels.values.toList)

  def allIssued: AppF[List[Parcel]] =
    stateInspect(_.issued)

  def revenue: AppF[Double] =
    stateInspect(_.revenue)

  def currentDay: AppF[Int] =
    stateInspect(_.currentDay)

  def nextDay: AppF[Int] =
    s => {
      val d = s.currentDay + 1
      (s.copy(currentDay = d), d)
    }