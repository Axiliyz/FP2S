package postoffice

import postoffice.algebras.ConfigAlgebra
import postoffice.usecases.Tariff

final class ConfigInterpreter(cfg: PostConfig) extends ConfigAlgebra[AppF]:
  def acceptanceCost(weightKg: Double): AppF[Double] =
    statePure(Tariff.acceptanceCost(weightKg, cfg.taxPerKg))

  def canAccept(weightKg: Double): AppF[Boolean]     =
    statePure(Tariff.canAccept(weightKg, cfg.maxWeightKg))

  def storageCost(days: Int): AppF[Double]           =
    statePure(Tariff.storageCost(days, cfg.storageRatePerDay))

  def packageClass(weightKg: Double): AppF[(PackageClass, PackagingRule)] =
    statePure(Tariff.packageClass(weightKg, cfg))