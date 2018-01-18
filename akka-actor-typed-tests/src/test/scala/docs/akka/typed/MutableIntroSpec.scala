/**
 * Copyright (C) 2014-2018 Lightbend Inc. <https://www.lightbend.com>
 */
package docs.akka.typed

//#imports
import akka.actor.typed._
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.ActorContext
import akka.testkit.typed.TestKit

import scala.concurrent.duration._
import scala.concurrent.Await
//#imports

object MutableIntroSpec {

  //#chatroom-actor
  object ChatRoom {
    //#chatroom-protocol
    sealed trait RoomCommand
    final case class GetSession(screenName: String, replyTo: ActorRef[SessionEvent])
      extends RoomCommand
    //#chatroom-protocol
    //#chatroom-behavior
    private final case class PublishSessionMessage(screenName: String, message: String)
      extends RoomCommand
    //#chatroom-behavior
    //#chatroom-protocol

    sealed trait SessionEvent
    final case class SessionGranted(handle: ActorRef[PostMessage]) extends SessionEvent
    final case class SessionDenied(reason: String) extends SessionEvent
    final case class MessagePosted(screenName: String, message: String) extends SessionEvent

    trait SessionCommand
    final case class PostMessage(message: String) extends SessionCommand
    private final case class NotifyClient(message: MessagePosted)  extends SessionCommand
    //#chatroom-protocol
    //#chatroom-behavior

    def behavior(): Behavior[RoomCommand] =
      Behaviors.mutable[RoomCommand](ctx ⇒ new ChatRoomBehavior(ctx))

    class ChatRoomBehavior(ctx: ActorContext[RoomCommand]) extends Behaviors.MutableBehavior[RoomCommand] {
      private var sessions: List[ActorRef[SessionCommand]] = List.empty

      override def onMessage(msg: RoomCommand): Behavior[RoomCommand] = {
        msg match {
          case GetSession(screenName, client) ⇒
            // create a child actor for further interaction with the client
            val session = ctx.spawn(session(screenName, client), name = screenName)
            client ! SessionGranted(session)
            sessions = session :: sessions
            this
          case PublishSessionMessage(screenName, message) ⇒
            val notification = NotifyClient(MessagePosted(screenName, message))
            sessions foreach (_ ! notification)
            this
        }
      }
    }

    private def session(
                         room: ActorRef[PublishSessionMessage],
                         screenName: String,
                         client: ActorRef[SessionEvent]                         ): Behavior[SessionCommand] =
      Behaviors.immutable { (ctx, msg) =>
        msg match {
          case PostMessage(message) =>
            // from client, publish to others via the room
            room ! PublishSessionMessage(screenName, message)
            Behaviors.same
          case NotifyClient(message) =>
            // published from the room
            client ! message
            Behaviors.same
        }
      }
    //#chatroom-behavior
  }
  //#chatroom-actor

}

class MutableIntroSpec extends TestKit with TypedAkkaSpecWithShutdown {

  import MutableIntroSpec._

  "A chat room" must {
    "chat" in {
      //#chatroom-gabbler
      import ChatRoom._

      val gabbler =
        Behaviors.immutable[SessionEvent] { (_, msg) ⇒
          msg match {
            case SessionDenied(reason) ⇒
              println(s"cannot start chat room session: $reason")
              Behaviors.stopped
            case SessionGranted(handle) ⇒
              handle ! PostMessage("Hello World!")
              Behaviors.same
            case MessagePosted(screenName, message) ⇒
              println(s"message has been posted by '$screenName': $message")
              Behaviors.stopped
          }
        }
      //#chatroom-gabbler

      //#chatroom-main
      val main: Behavior[String] =
        Behaviors.deferred { ctx ⇒
          val chatRoom = ctx.spawn(ChatRoom.behavior(), "chatroom")
          val gabblerRef = ctx.spawn(gabbler, "gabbler")
          ctx.watch(gabblerRef)

          Behaviors.immutablePartial[String] {
            case (_, "go") ⇒
              chatRoom ! GetSession("ol’ Gabbler", gabblerRef)
              Behaviors.same
          } onSignal {
            case (_, Terminated(_)) ⇒
              println("Stopping guardian")
              Behaviors.stopped
          }
        }

      val system = ActorSystem(main, "ChatRoomDemo")
      system ! "go"
      Await.result(system.whenTerminated, 1.second)
      //#chatroom-main
    }
  }
}
