package postoffice

import monads.{IO, Writer}
import monads.Monoid.given
import monads.IO.given

// App отвечает только за бизнес-логику и сборку дерева меню.
// Навигация по меню делегирована MenuNavigator.
object App:

  private val readLine: IO[String]       = IO.getStrLn
  private def print(s: String): IO[Unit] = IO.putStrLn(s)

  private def prompt(msg: String): IO[String] =
    for
      _    <- print(msg)
      line <- readLine
    yield line

  private def promptDouble(msg: String): IO[Double] =
    prompt(msg).map(_.toDouble)

  private def printReceipt(
      parcel:     Parcel,
      cost:       Double,
      cls:        PackageClass,
      rule:       PackagingRule,
      logEntries: Vector[String]
  ): IO[Unit] =
    print(
      s"""
         |============== RECEIPT ================
         |Parcel #${parcel.id}
         |Sender:       ${parcel.sender}
         |Recipient:    ${parcel.recipient}
         |Weight:       ${parcel.weight} kg
         |Class:        ${cls.label}
         |Packaging:    ${rule.description}
         |Cost:         $cost rub.
         |Accepted day: ${parcel.acceptedDay}
         |---------------- LOG ------------------
         |${logEntries.mkString("\n")}
         |=======================================
         |""".stripMargin
    )

  private def printIssueReceipt(
      parcel:      Parcel,
      storageCost: Double,
      dayIssued:   Int,
      logEntries:  Vector[String]
  ): IO[Unit] =
    print(
      s"""
         |=========== ISSUE RECEIPT =============
         |Parcel #${parcel.id}
         |Recipient:    ${parcel.recipient}
         |Stored days:  ${parcel.storedDays(dayIssued)}
         |Storage fee:  $storageCost rub.
         |---------------- LOG ------------------
         |${logEntries.mkString("\n")}
         |=======================================
         |""".stripMargin
    )

  private def doAccept(
      cfg:       PostOfficeConfig,
      st:        PostOfficeState,
      sender:    String,
      recipient: String,
      weight:    Double
  ): IO[PostOfficeState] =
    val cost              = ReaderOps.acceptanceCost(weight).run(cfg)
    val (cls, rule)       = ReaderOps.packageClass(weight).run(cfg)
    val (st1, _)          = StateOps.takeQueueNumber(sender).run(st)
    val (st2, parcel)     = StateOps.acceptParcel(sender, recipient, weight, cost).run(st1)
    val Writer(logEntries, _) =
      for
        _ <- WriterOps.logTariffCalc(weight, cost)
        p <- WriterOps.logAcceptance(parcel)
      yield p
    printReceipt(parcel, cost, cls, rule, logEntries).map(_ => st2)

  private def acceptFlow(cfg: PostOfficeConfig, stRef: StRef): IO[Unit] =
    for
      sender    <- prompt("Sender name: ")
      recipient <- prompt("Recipient name: ")
      weight    <- promptDouble("Parcel weight (kg): ")
      allowed    = ReaderOps.canAccept(weight).run(cfg)
      _         <- if allowed
                   then doAccept(cfg, stRef.get, sender, recipient, weight).map(st => stRef.set(st))
                   else print(s"Rejected: weight $weight kg exceeds max ${cfg.maxWeight} kg.")
    yield ()

  private def issueFlow(cfg: PostOfficeConfig, stRef: StRef): IO[Unit] =
    val st = stRef.get
    if st.acceptedParcels.isEmpty then
      print("No parcels to issue.")
    else
      for
        _          <- print("Available parcels:")
        _          <- IO.delay { st.acceptedParcels.values.foreach(p => println(s"  ${p.summary}")) }
        idStr      <- prompt("Parcel ID to issue: ")
        id          = idStr.trim.toInt
        days        = st.currentDay - st.acceptedParcels.get(id).map(_.acceptedDay).getOrElse(st.currentDay)
        storageFee  = ReaderOps.storageCost(days).run(cfg)
        (st1, opt)  = StateOps.issueParcel(id, storageFee).run(st)
        _          <- opt match
                        case Some(parcel) =>
                          val Writer(logEntries, _) =
                            for
                              _ <- WriterOps.logStorageCharge(parcel.id, days, storageFee)
                              p <- WriterOps.logIssuance(parcel, st.currentDay)
                            yield p
                          printIssueReceipt(parcel, storageFee, st.currentDay, logEntries)
                            .map(_ => stRef.set(st1))
                        case None =>
                          print(s"Parcel #$id not found.")
      yield ()

  private def nextDayFlow(cfg: PostOfficeConfig, stRef: StRef): IO[Unit] =
    val (st1, day) = StateOps.nextDay.run(stRef.get)
    print(s"Day $day started.").map(_ => stRef.set(st1))

  private def summaryFlow(stRef: StRef): IO[Unit] =
    val st = stRef.get
    val showIssued =
      if st.issuedParcels.nonEmpty then
        print(s"Issued parcels: ${st.issuedParcels.map(_.summary).mkString(", ")}")
      else IO.pure(())
    showIssued.flatMap(_ => print(s"Total revenue: ${st.revenue} rub."))

  // Изменяемая ссылка на состояние — нужна чтобы MenuLeaf-замыкания видели актуальный стейт.
  private final class StRef(private var state: PostOfficeState):
    def get: PostOfficeState        = state
    def set(s: PostOfficeState): Unit = state = s

  private def statusTitle(stRef: StRef): String =
    val st = stRef.get
    s"Post Office — Day ${st.currentDay} | Revenue: ${st.revenue} rub. | Stored: ${st.acceptedParcels.size}"

  // Дерево меню — только данные и действия. Никакой навигационной логики.
  // MenuNavigator получает это дерево и сам управляет циклом.
  private def buildMenu(cfg: PostOfficeConfig, stRef: StRef): MenuTreeNode =
    MenuTreeNode(
      title = statusTitle(stRef),
      children = Seq(
        MenuLeaf("Accept parcel", acceptFlow(cfg, stRef)),
        MenuLeaf("Issue parcel",  issueFlow(cfg, stRef)),
        MenuLeaf("Next day",      nextDayFlow(cfg, stRef)),
        MenuLeaf("Summary",       summaryFlow(stRef))
      )
    )

  val program: IO[Unit] =
    val stRef     = StRef(PostOfficeState.empty)
    val cfg       = PostOfficeConfig.default
    // MenuNavigator — единственное место где живёт цикл и роутинг.
    val navigator = MenuNavigator(buildMenu(cfg, stRef))
    for
      _ <- print("=== Post Office (variant 15) ===")
      _ <- navigator.userInteractionLoop
    yield ()

  def main(args: Array[String]): Unit =
    program.unsafeRun()
