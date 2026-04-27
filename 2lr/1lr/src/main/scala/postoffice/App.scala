package postoffice

import monads.{IO, Writer, State, Monad}
import monads.Monoid.given
import monads.IO.given

// UserInteraction — весь main теперь живёт здесь как дерево MenuTreeNode.
// Никаких case "1" / case "2" — меню масштабируется добавлением узлов в дерево.
object App extends UserInteraction:

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
    val receipt =
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
    print(receipt)

  private def printIssueReceipt(
      parcel:      Parcel,
      storageCost: Double,
      dayIssued:   Int,
      logEntries:  Vector[String]
  ): IO[Unit] =
    val receipt =
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
    print(receipt)

  private def doAccept(
      cfg:       PostOfficeConfig,
      st:        PostOfficeState,
      sender:    String,
      recipient: String,
      weight:    Double
  ): IO[PostOfficeState] =
    val cost        = ReaderOps.acceptanceCost(weight).run(cfg)
    val (cls, rule) = ReaderOps.packageClass(weight).run(cfg)
    val (st1, _)    = StateOps.takeQueueNumber(sender).run(st)
    val (st2, parcel) = StateOps.acceptParcel(sender, recipient, weight, cost).run(st1)
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
        _     <- print("Available parcels:")
        _     <- IO.delay { st.acceptedParcels.values.foreach(p => println(s"  ${p.summary}")) }
        idStr <- prompt("Parcel ID to issue: ")
        id     = idStr.trim.toInt
        days   = st.currentDay - st.acceptedParcels.get(id).map(_.acceptedDay).getOrElse(st.currentDay)
        storageFee = ReaderOps.storageCost(days).run(cfg)
        (st1, opt) = StateOps.issueParcel(id, storageFee).run(st)
        _ <- opt match
               case Some(parcel) =>
                 val w = for
                   _ <- WriterOps.logStorageCharge(parcel.id, days, storageFee)
                   p <- WriterOps.logIssuance(parcel, st.currentDay)
                 yield p
                 val Writer(logEntries, _) = w
                 printIssueReceipt(parcel, storageFee, st.currentDay, logEntries)
                   .map(_ => stRef.set(st1))
               case None =>
                 print(s"Parcel #$id not found.")
      yield ()

  private def nextDayFlow(cfg: PostOfficeConfig, stRef: StRef): IO[Unit] =
    val (st1, day) = StateOps.nextDay.run(stRef.get)
    print(s"Day $day started.").map(_ => stRef.set(st1))

  private def exitFlow(stRef: StRef): IO[Unit] =
    val st = stRef.get
    val showIssued =
      if st.issuedParcels.nonEmpty then
        print(s"Issued parcels: ${st.issuedParcels.map(_.summary).mkString(", ")}")
      else IO.pure(())
    showIssued.flatMap(_ => print(s"Total revenue: ${st.revenue} rub. Goodbye!"))

  // Изменяемая ссылка на состояние — обёртка для передачи в MenuLeaf-замыкания.
  // Scala — функциональный язык, но для дерева меню нам нужен общий изменяемый стейт,
  // иначе каждый MenuLeaf получит копию состояния на момент построения дерева.
  private final class StRef(private var state: PostOfficeState):
    def get: PostOfficeState      = state
    def set(s: PostOfficeState): Unit = state = s

  // Строит заголовок статус-бара для меню — показывает текущий день и выручку.
  private def statusTitle(stRef: StRef): String =
    val st = stRef.get
    s"Post Office Day ${st.currentDay} | Revenue: ${st.revenue} rub. | Stored: ${st.acceptedParcels.size}"

  // Дерево меню строится один раз. Добавить новый пункт = добавить MenuLeaf.
  // Нет ни одного case "N" — нумерация автоматическая в MenuTreeNode.
  private def buildMenu(cfg: PostOfficeConfig, stRef: StRef): MenuTreeNode =
    MenuTreeNode(
      title = statusTitle(stRef),
      children = Seq(
        MenuLeaf("Accept parcel",  acceptFlow(cfg, stRef)),
        MenuLeaf("Issue parcel",   issueFlow(cfg, stRef)),
        MenuLeaf("Next day",       nextDayFlow(cfg, stRef)),
        MenuLeaf("Show summary",   exitFlow(stRef))
      )
    )

  // handleUserAnswer и userInteractionLoop делегируют в buildMenu.
  // App сам является UserInteraction — как и требует доска.
  def handleUserAnswer(answer: String): IO[Unit] =
    val stRef = StRef(PostOfficeState.empty)
    buildMenu(PostOfficeConfig.default, stRef).handleUserAnswer(answer)

  def userInteractionLoop: IO[Unit] =
    val stRef = StRef(PostOfficeState.empty)
    val cfg   = PostOfficeConfig.default
    // Цикл с обновляемым заголовком: каждую итерацию пересобираем заголовок из stRef.
    def loop: IO[Unit] =
      val menu = buildMenu(cfg, stRef)
      for
        _      <- IO.putStrLn(s"\n=== ${statusTitle(stRef)} ===")
        _      <- IO.delay {
                    menu.children.zipWithIndex.foreach { (opt, i) =>
                      println(s"${i + 1}) ${opt.show}")
                    }
                    println("0) Exit")
                  }
        answer <- IO.getStrLn
        _      <- answer.trim match
                    case "0" | "" => exitFlow(stRef)
                    case s =>
                      s.toIntOption match
                        case Some(n) if n >= 1 && n <= menu.children.size =>
                          menu.children(n - 1) match
                            case leaf: MenuLeaf     => leaf.action.flatMap(_ => loop)
                            case node: MenuTreeNode => node.userInteractionLoop.flatMap(_ => loop)
                            case other              => IO.putStrLn(s"Unhandled: ${other.show}").flatMap(_ => loop)
                        case _ =>
                          IO.putStrLn(s"Unknown command: '$s'.").flatMap(_ => loop)
      yield ()
    loop

  val program: IO[Unit] =
    for
      _ <- print("=== Post Office (variant 15) ===")
      _ <- userInteractionLoop
    yield ()

  def main(args: Array[String]): Unit =
    program.unsafeRun()
