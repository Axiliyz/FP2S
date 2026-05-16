package postoffice

@main def main(): Unit =
  val alg = Interpreters.make(PostConfig.default)
  Program.menu[AppF](using alg.cfg, alg.state, alg.log, alg.console, alg.id)
    .apply(PostState.empty)