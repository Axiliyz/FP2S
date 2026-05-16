package postoffice

import cats.data.StateT
import cats.effect.IO
import postoffice.algebras.StateAlgebra

object StateInterpreter extends StateAlgebra[AppF]:
  def acceptParcel(parcel: Parcel, cost: Double): AppF[Unit] =
    StateT.modify(s => s.copy(parcels = s.parcels + (parcel.id -> parcel), revenue = s.revenue + cost))
  def pickupParcel(parcelId: ParcelId, storageCost: Double): AppF[Unit] =
    StateT.modify { s =>
      s.parcels.get(parcelId).fold(s) { p =>
        s.copy(parcels = s.parcels - p.id, issued = s.issued :+ p, revenue = s.revenue + storageCost)
      }
    }
  def allParcels: AppF[List[Parcel]] = StateT.inspect(_.parcels.values.toList)
  def allIssued:  AppF[List[Parcel]] = StateT.inspect(_.issued)
  def revenue:    AppF[Double]       = StateT.inspect(_.revenue)
  def currentDay: AppF[Int]          = StateT.inspect(_.currentDay)
  def nextDay: AppF[Int] = StateT { s =>
    val d = s.currentDay + 1
    IO.pure((s.copy(currentDay = d), d))
  }
