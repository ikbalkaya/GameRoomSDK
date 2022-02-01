package com.ablylabs.ablygamesdk

import io.ably.lib.realtime.AblyRealtime
import io.ably.lib.realtime.CompletionListener
import io.ably.lib.types.AblyException
import io.ably.lib.types.ErrorInfo
import io.ably.lib.types.Message
import kotlinx.coroutines.flow.Flow
import java.lang.Exception
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

sealed class RoomPresenceResult {
    data class Success(val gameRoom: GameRoom, val player: GamePlayer) : RoomPresenceResult()
    data class Failure(val gameRoom: GameRoom, val player: GamePlayer, val exception: Exception?) : RoomPresenceResult()
}

//make result type names a bit more clear
typealias LeaveRoomResult = RoomPresenceResult
typealias EnterRoomResult = RoomPresenceResult

sealed class MessageSentResult {
    data class Success(val toWhom: GamePlayer) : MessageSentResult()
    data class Failed(val toWhom: GamePlayer, val exception: Exception?) : MessageSentResult()
}

data class ReceivedMessage(val from: GamePlayer, val message: String)

sealed class RoomPresenceUpdate {
    data class Enter(val player: GamePlayer) : RoomPresenceUpdate()
    data class Leave(val player: GamePlayer) : RoomPresenceUpdate()
}

interface GameRoomController {
    suspend fun numberOfPeopleInRoom(gameRoom: GameRoom): Int
    suspend fun enter(player: GamePlayer, gameRoom: GameRoom): EnterRoomResult
    suspend fun leave(player: GamePlayer, gameRoom: GameRoom): LeaveRoomResult

    //consider migrating message
    suspend fun sendMessageToPlayer(
        from: GamePlayer,
        to: GamePlayer,
        message: String
    ): MessageSentResult

    suspend fun registerToTextMessage(room: GameRoom, receiver: GamePlayer): Flow<ReceivedMessage>
    suspend fun allPlayers(inWhich: GameRoom): List<GamePlayer>
    suspend fun registerToPresenceEvents(gameRoom: GameRoom): Flow<RoomPresenceUpdate>
    suspend fun unregisterFromPresenceEvents(room: GameRoom)
}

internal const val roomNamespace = "room"
internal const val playerNamespace = "player"
internal fun roomChannel(gameRoom: GameRoom) = "room:${gameRoom.id}"
internal fun bidirectinalPlayerChannel(player1: GamePlayer, player2: GamePlayer): String {
    //this should return thee same regardless of playerIds, so it is a good idea to use them by their order
    val comparisonResult = player1.id.compareTo(player2.id)
    if (comparisonResult > 0) {
        return "player:${player2.id}-${player1.id}"
    }
    return "player:${player1.id}-${player2.id}"
}

internal class GameRoomControllerImpl(private val ably: AblyRealtime) : GameRoomController {
    override suspend fun numberOfPeopleInRoom(gameRoom: GameRoom): Int {
        return suspendCoroutine { continuation ->
            continuation.resume(ably.channels[roomChannel(gameRoom)].presence.get().size)
        }
    }

    override suspend fun enter(player: GamePlayer, gameRoom: GameRoom): EnterRoomResult {
        return suspendCoroutine { continuation ->
            ably.channels[roomChannel(gameRoom)].presence.apply {
                enterClient(player.id, "no_data", object : CompletionListener {
                    override fun onSuccess() {
                        continuation.resume(RoomPresenceResult.Success(gameRoom, player))
                    }

                    override fun onError(reason: ErrorInfo?) {
                        continuation.resume(
                            RoomPresenceResult.Failure(
                                gameRoom,
                                player,
                                AblyException.fromErrorInfo(reason)
                            )
                        )
                    }
                })

            }
        }
    }

    override suspend fun leave(player: GamePlayer, gameRoom: GameRoom): LeaveRoomResult {
        return suspendCoroutine { continuation ->
            ably.channels[roomChannel(gameRoom)].presence.apply {
                leaveClient(player.id, "no_data", object : CompletionListener {
                    override fun onSuccess() {
                        continuation.resume(RoomPresenceResult.Success(gameRoom, player))
                    }

                    override fun onError(reason: ErrorInfo?) {
                        continuation.resume(
                            RoomPresenceResult.Failure(
                                gameRoom,
                                player,
                                AblyException.fromErrorInfo(reason)
                            )
                        )
                    }
                })

            }
        }
    }

    override suspend fun sendMessageToPlayer(from: GamePlayer, to: GamePlayer, messageText: String):
            MessageSentResult {
        //this message name should be derived from message when migrated
        val message = Message("text", messageText, from.id)
        //this should be a bidirectional channel between two players
        val channelId = bidirectinalPlayerChannel(from, to)
        return suspendCoroutine { continuation ->
            ably.channels[channelId]
                .publish(message, object : CompletionListener {
                    override fun onSuccess() {
                        continuation.resume(MessageSentResult.Success(to))
                    }

                    override fun onError(reason: ErrorInfo?) {
                        continuation.resume(MessageSentResult.Failed(to, AblyException.fromErrorInfo(reason)))
                    }
                })
        }

    }

    override suspend fun registerToTextMessage(room: GameRoom, receiver: GamePlayer): Flow<ReceivedMessage> {
        TODO("Not yet implemented")
    }

    override suspend fun allPlayers(inWhich: GameRoom): List<GamePlayer> {
        TODO("Not yet implemented")
    }

    override suspend fun registerToPresenceEvents(gameRoom: GameRoom): Flow<RoomPresenceUpdate> {
        TODO("Not yet implemented")
    }

    override suspend fun unregisterFromPresenceEvents(room: GameRoom) {
        TODO("Not yet implemented")
    }

}