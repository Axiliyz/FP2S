package postoffice.algebras

import postoffice.ParcelId

trait IdSourceAlgebra[F[_]]:
  def nextId: F[ParcelId]
