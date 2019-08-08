package beams.akka

final case class AkkaNode[+Env](
                                 env: Env,
                                 ref: NodeActor.Ref
                               )