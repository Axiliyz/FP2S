package postoffice.algebras

import postoffice.{PackageClass, PackagingRule}

trait ConfigAlgebra[F[_]]:
  def acceptanceCost(weightKg: Double): F[Double]
  def canAccept(weightKg: Double): F[Boolean]
  def storageCost(days: Int): F[Double]
  def packageClass(weightKg: Double): F[(PackageClass, PackagingRule)]
