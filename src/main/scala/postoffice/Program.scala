package postoffice

import postoffice.Monad.*
import postoffice.algebras.*
import postoffice.{PostError, renderError}

object Program:

  def acceptFlow[F[_]](using
    cfg:     ConfigAlgebra[F],
    state:   StateAlgebra[F],
    log:     LogAlgebra[F],
    console: ConsoleAlgebra[F],
    id:      IdSourceAlgebra[F],
  )(using Monad[F]): F[Unit] =
    for
      sender    <- console.putStr("Sender name: ")        >> console.readLine
      recipient <- console.putStr("Recipient name: ")     >> console.readLine
      wStr      <- console.putStr("Parcel weight (kg): ") >> console.readLine
      weight     = wStr.toDoubleOption.getOrElse(0.0)
      ok        <- cfg.canAccept(weight)
      _         <- if ok then acceptValid(sender, recipient, weight)
                   else
                     val err = PostError.WeightExceedsMax(weight)
                     log.logRejection(recipient, weight, "weight exceeds maximum") >>
                     console.putStrLn(renderError(err))
    yield ()

  private def acceptValid[F[_]](
    sender:    String,
    recipient: String,
    weightKg:  Double,
  )(using
    cfg:     ConfigAlgebra[F],
    state:   StateAlgebra[F],
    log:     LogAlgebra[F],
    console: ConsoleAlgebra[F],
    id:      IdSourceAlgebra[F],
  )(using Monad[F]): F[Unit] =
    for
      cost          <- cfg.acceptanceCost(weightKg)
      pkgPair  <- cfg.packageClass(weightKg)
      pkgCls    = pkgPair._1
      pkgRule   = pkgPair._2
      parcelId      <- id.nextId
      day           <- state.currentDay
      parcel         = Parcel(parcelId, sender, recipient, weightKg, pkgCls, pkgRule, day)
      _             <- state.acceptParcel(parcel, cost)
      _             <- log.logTariffCalc(weightKg, cost)
      _             <- log.logAcceptance(parcel, cost)
      _             <- printLogs
      _             <- console.putStrLn(
                         s"""
                            |============== RECEIPT ================
                            |Parcel #${parcelId.value}
                            |Sender:       $sender
                            |Recipient:    $recipient
                            |Weight:       $weightKg kg
                            |Class:        ${pkgCls.label}
                            |Packaging:    ${pkgRule.description}
                            |Cost:         ${"%.2f".format(cost)} rub.
                            |Accepted day: $day
                            |=======================================
                            |""".stripMargin
                       )
    yield ()

  def pickupFlow[F[_]](using
    cfg:     ConfigAlgebra[F],
    state:   StateAlgebra[F],
    log:     LogAlgebra[F],
    console: ConsoleAlgebra[F],
  )(using Monad[F]): F[Unit] =
    for
      parcels <- state.allParcels
      _       <- if parcels.isEmpty then console.putStrLn("No parcels to issue.")
                 else issueFromList(parcels)
    yield ()

  private def issueFromList[F[_]](parcels: List[Parcel])(using
    cfg:     ConfigAlgebra[F],
    state:   StateAlgebra[F],
    log:     LogAlgebra[F],
    console: ConsoleAlgebra[F],
  )(using Monad[F]): F[Unit] =
    for
      _     <- console.putStrLn("Available parcels:")
      _     <- console.putStrLn(parcels.map(p => s"  ${p.summary}").mkString("\n"))
      idStr <- console.putStr("Parcel ID to issue: ") >> console.readLine
      day   <- state.currentDay
      _     <- idStr.trim.toIntOption match
                 case None =>
                   val err = PostError.InvalidInput(idStr)
                   console.putStrLn(renderError(err))
                 case Some(n) =>
                   val pid = ParcelId(n)
                   parcels.find(_.id == pid) match
                     case None =>
                       val err = PostError.ParcelNotFound(n)
                       console.putStrLn(renderError(err))
                     case Some(parcel) =>
                       val days = parcel.storedDays(day)
                       for
                         storageFee <- cfg.storageCost(days)
                         _          <- state.pickupParcel(pid, storageFee)
                         _          <- log.logStorageCharge(pid, days, storageFee)
                         _          <- log.logIssuance(parcel, day)
                         _          <- printLogs
                         _          <- console.putStrLn(
                                         s"""
                                            |=========== ISSUE RECEIPT =============
                                            |Parcel #${parcel.id.value}
                                            |Recipient:    ${parcel.recipient}
                                            |Stored days:  $days
                                            |Storage fee:  ${"%.2f".format(storageFee)} rub.
                                            |=======================================
                                            |""".stripMargin
                                       )
                       yield ()
    yield ()

  def nextDayFlow[F[_]](using
    state:   StateAlgebra[F],
    console: ConsoleAlgebra[F],
  )(using Monad[F]): F[Unit] =
    for
      day <- state.nextDay
      _   <- console.putStrLn(s"Day $day started.")
    yield ()

  def summaryFlow[F[_]](using
    state:   StateAlgebra[F],
    console: ConsoleAlgebra[F],
  )(using Monad[F]): F[Unit] =
    for
      issued  <- state.allIssued
      revenue <- state.revenue
      _       <- console.putStrLn(
                   if issued.nonEmpty then
                     s"Issued parcels: ${issued.map(_.summary).mkString(", ")}\nTotal revenue: ${"%.2f".format(revenue)} rub. Goodbye!"
                   else
                     s"Total revenue: ${"%.2f".format(revenue)} rub. Goodbye!"
                 )
    yield ()

  def statusTitle[F[_]](using state: StateAlgebra[F])(using Monad[F]): F[String] =
    for
      day     <- state.currentDay
      revenue <- state.revenue
      parcels <- state.allParcels
    yield s"Post Office Day $day | Revenue: ${"%.2f".format(revenue)} rub. | Stored: ${parcels.size}"

  private def printLogs[F[_]](using log: LogAlgebra[F], console: ConsoleAlgebra[F])(using Monad[F]): F[Unit] =
    for
      lines <- log.take
      _     <- lines.foldLeft(Monad[F].pure(())) { (acc, line) =>
                 acc >> console.putStrLn(line)
               }
    yield ()

  def buildMenu[F[_]](using
    cfg:     ConfigAlgebra[F],
    state:   StateAlgebra[F],
    log:     LogAlgebra[F],
    console: ConsoleAlgebra[F],
    id:      IdSourceAlgebra[F],
  )(using Monad[F]): MenuTreeNode[F] =
    MenuTreeNode[F](
      titleF = statusTitle[F],
      children = Seq(
        MenuLeaf("Accept parcel", () => acceptFlow),
        MenuLeaf("Issue parcel",  () => pickupFlow),
        MenuLeaf("Next day",      () => nextDayFlow),
        MenuLeaf("Show summary",  () => summaryFlow),
      ),
    )

  def menu[F[_]](using
    cfg:     ConfigAlgebra[F],
    state:   StateAlgebra[F],
    log:     LogAlgebra[F],
    console: ConsoleAlgebra[F],
    id:      IdSourceAlgebra[F],
  )(using Monad[F]): F[Unit] =
    buildMenu[F].userInteractionLoop
