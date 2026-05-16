package postoffice.usecases

import postoffice.{PackageClass, PackagingRule, PostConfig}

object Tariff:
  def acceptanceCost(weightKg: Double, taxPerKg: Double): Double =
    weightKg * taxPerKg

  def canAccept(weightKg: Double, maxWeightKg: Double): Boolean =
    weightKg > 0 && weightKg <= maxWeightKg

  def storageCost(days: Int, storageRatePerDay: Double): Double =
    days * storageRatePerDay

  def packageClass(weightKg: Double, cfg: PostConfig): (PackageClass, PackagingRule) =
    val cls  = cfg.weightClasses.find((t, _) => weightKg <= t).map(_(1)).getOrElse(PackageClass.Heavy)
    val rule = cfg.packagingRules.getOrElse(cls, PackagingRule.Crate)
    (cls, rule)
