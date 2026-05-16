package postoffice

enum PostError:
  case WeightExceedsMax(weightKg: Double)
  case ParcelNotFound(id: Int)
  case InvalidInput(raw: String)

trait ExceedsMaxWeight[E]:
  def exceedsMaxWeight(weightKg: Double): E

trait ParcelNotFoundError[E]:
  def parcelNotFound(id: Int): E

given ExceedsMaxWeight[PostError] with
  def exceedsMaxWeight(weightKg: Double): PostError = PostError.WeightExceedsMax(weightKg)

given ParcelNotFoundError[PostError] with
  def parcelNotFound(id: Int): PostError = PostError.ParcelNotFound(id)

def renderError(e: PostError): String = e match
  case PostError.WeightExceedsMax(w) => s"Rejected: $w kg exceeds maximum weight"
  case PostError.ParcelNotFound(id)  => s"Parcel #$id not found"
  case PostError.InvalidInput(raw)   => s"Invalid input: '$raw'"