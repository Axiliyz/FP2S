package postoffice

enum PackagingRule(val description: String):
  case Envelope extends PackagingRule("envelope")
  case Box extends PackagingRule("box")
  case Crate extends PackagingRule("crate")

enum PackageClass(val label: String):
  case Light extends PackageClass("Light")
  case Medium extends PackageClass("Medium")
  case Heavy extends PackageClass("Heavy")

case class PostConfig(
  taxPerKg:          Double,
  maxWeightKg:       Double,
  storageRatePerDay: Double,
  weightClasses:     List[(Double, PackageClass)],
  packagingRules:    Map[PackageClass, PackagingRule])

object PostConfig:
  val default = PostConfig(
    taxPerKg = 50.0,
    maxWeightKg = 30.0,
    storageRatePerDay = 10.0,
    weightClasses = List(
      (5.0, PackageClass.Light),
      (15.0, PackageClass.Medium),
      (30.0, PackageClass.Heavy),
    ),
    packagingRules = Map(
      PackageClass.Light -> PackagingRule.Envelope,
      PackageClass.Medium -> PackagingRule.Box,
      PackageClass.Heavy -> PackagingRule.Crate,
    ),
  )

case class ParcelId(value: Int)

case class Parcel(
  id: ParcelId,
  sender: String,
  recipient: String,
  weightKg: Double,
  pkgClass: PackageClass,
  packaging: PackagingRule,
  acceptedDay: Int,
)

extension (p: Parcel)
  def storedDays(currentDay: Int): Int = currentDay - p.acceptedDay
  def summary: String = s"#${p.id.value}: ${p.sender} -> ${p.recipient}, ${p.weightKg} kg (day ${p.acceptedDay})"

case class PostState(
  parcels: Map[ParcelId, Parcel],
  issued: List[Parcel],
  revenue: Double,
  currentDay: Int,
  nextId: Int,
  logs: List[String],
)

object PostState:
  val empty = PostState(
    parcels = Map.empty,
    issued = List.empty,
    revenue = 0,
    currentDay = 1,
    nextId = 1,
    logs = List.empty,
  )