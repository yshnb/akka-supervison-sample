package jp.yshnb.samples

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

import akka.actor.{Actor, Props, ActorSystem, OneForOneStrategy}
import akka.actor.SupervisorStrategy._

object AkkaSupervisionApp extends App {
  val system = ActorSystem("supervision-sample")

  val supervisor = system.actorOf(Props[Supervisor], "supervisor")
}

class Supervisor extends Actor {
  import Supervisor._

  var supervisorCountUp = 0

  override val supervisorStrategy = OneForOneStrategy() {
    case _: Worker.Disabled =>
      println(s"${sender.path.name} say disabled, but supervisor reply Resume.")
      Resume // continue
    case _: Worker.CompletelyDisabled =>
      supervisorCountUp += 1
      if (supervisorCountUp < 4) {
        println(s"${sender.path.name} say disabled, so supervisor reply Restart.")
        Restart // Restart sender actor
      } else {
        println(s"${sender.path.name} say disabled, so supervisor reply Stop.")
        self ! Shutdown
        Stop // Stop sender actor
      }
  }

  val workers = (1 to 3).map { n =>
    context.actorOf(Props[Worker], name = "worker" + n)
  }.foreach { worker =>
    context.system.scheduler.schedule(Duration.Zero, 1 second, worker, Worker.Continue)
  }

  def receive = {
    case Shutdown  =>
      Thread.sleep(500)
      context.system.shutdown()
    case _ =>
      println("unexpected")
  }

}

object Supervisor {
  trait Protocol
  case object Shutdown extends Protocol
}

class Worker extends Actor {
  import Worker._

  var retry = 0
  var counter = 0

  def receive = {
    case Continue =>
      counter += 1
      println(s"${self.path.name} is working, count is ${counter}")
      counter match {
        case c: Int if (counter > 10)               => self ! Completed
        case c: Int if (counter > 3 && counter < 8) =>
          retry += 1
          if (retry < 3) throw new Disabled
          else throw new CompletelyDisabled
        case _ => None
      }
    case Completed =>
      context.system.shutdown()
  }
}

object Worker {
  trait Protocol
  class Disabled extends RuntimeException with Protocol
  class CompletelyDisabled extends RuntimeException with Protocol
  case object Continue extends Protocol
  case object Completed extends Protocol
}

