package com.cardcade.app.games.scum.net

import com.cardcade.app.games.scum.game.Card
import com.cardcade.app.games.scum.game.GameStateJson
import com.cardcade.app.games.scum.game.SetupOptions
import com.cardcade.app.games.scum.game.Suit
import org.json.JSONArray
import org.json.JSONObject

/**
 * Line-delimited JSON messages for Scum LAN play.
 *
 * HOST → client : HELLO, LOBBY, START, STATE, END
 * Client → HOST : JOIN, MOVE, LEAVE
 */
sealed class LanMessage {
    abstract fun toJson(): JSONObject
    fun encode(): String = toJson().toString()

    /** Client → host: initial connection with player display name. */
    data class Join(val displayName: String) : LanMessage() {
        override fun toJson() = JSONObject().apply {
            put("type", "JOIN")
            put("name", displayName)
        }
    }

    /** Host → specific client: you are seatId; host's display name. */
    data class Hello(val seatId: Int, val hostName: String) : LanMessage() {
        override fun toJson() = JSONObject().apply {
            put("type", "HELLO")
            put("seatId", seatId)
            put("hostName", hostName)
        }
    }

    /** Host → all clients: current lobby seat list. */
    data class Lobby(val seats: List<LobbySeat>) : LanMessage() {
        override fun toJson() = JSONObject().apply {
            put("type", "LOBBY")
            put("seats", JSONArray().apply {
                seats.forEach {
                    put(JSONObject().apply {
                        put("id", it.id)
                        put("name", it.name)
                        put("kind", it.kind.name)
                    })
                }
            })
        }
    }

    /** Host → all clients: game is starting; includes full SetupOptions. */
    data class Start(val optsJson: JSONObject) : LanMessage() {
        override fun toJson() = JSONObject().apply {
            put("type", "START")
            put("opts", optsJson)
        }
    }

    /** Host → specific client: authoritative game state after any move. */
    data class State(val stateJson: JSONObject) : LanMessage() {
        override fun toJson() = JSONObject().apply {
            put("type", "STATE")
            put("state", stateJson)
        }
    }

    /** Client → host: a player action (play, pass, or trade). */
    data class Move(val seatId: Int, val action: MoveAction) : LanMessage() {
        override fun toJson() = JSONObject().apply {
            put("type", "MOVE")
            put("seatId", seatId)
            put("action", action.toJson())
        }
    }

    /** Synthetic message emitted when a client's socket closes. */
    data class Leave(val clientId: Int) : LanMessage() {
        override fun toJson() = JSONObject().apply {
            put("type", "LEAVE")
            put("clientId", clientId)
        }
    }

    /** Host → all clients: session ended (e.g. host quit). */
    data class End(val reason: String) : LanMessage() {
        override fun toJson() = JSONObject().apply {
            put("type", "END")
            put("reason", reason)
        }
    }

    companion object {
        fun decode(line: String): LanMessage? {
            if (line == LEAVE_SENTINEL) return null  // handled by LanSession internally
            return runCatching {
                val obj = JSONObject(line)
                when (obj.getString("type")) {
                    "JOIN" -> Join(obj.getString("name"))
                    "HELLO" -> Hello(obj.getInt("seatId"), obj.getString("hostName"))
                    "LOBBY" -> Lobby(readSeats(obj.getJSONArray("seats")))
                    "START" -> Start(obj.getJSONObject("opts"))
                    "STATE" -> State(obj.getJSONObject("state"))
                    "MOVE" -> Move(obj.getInt("seatId"), MoveAction.fromJson(obj.getJSONObject("action")))
                    "LEAVE" -> Leave(obj.getInt("clientId"))
                    "END" -> End(obj.getString("reason"))
                    else -> null
                }
            }.getOrNull()
        }

        private fun readSeats(arr: JSONArray): List<LobbySeat> =
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                LobbySeat(
                    id = o.getInt("id"),
                    name = o.getString("name"),
                    kind = LobbySeat.Kind.valueOf(o.getString("kind")),
                )
            }
    }
}

data class LobbySeat(val id: Int, val name: String, val kind: Kind) {
    enum class Kind { HOST, REMOTE, CPU, OPEN }
}

sealed class MoveAction {
    abstract fun toJson(): JSONObject

    data class Play(val cards: List<Card>) : MoveAction() {
        override fun toJson() = JSONObject().apply {
            put("kind", "PLAY")
            put("cards", GameStateJson.encodeCards(cards))
        }
    }

    data object Pass : MoveAction() {
        override fun toJson() = JSONObject().apply { put("kind", "PASS") }
    }

    data class Trade(val cards: List<Card>) : MoveAction() {
        override fun toJson() = JSONObject().apply {
            put("kind", "TRADE")
            put("cards", GameStateJson.encodeCards(cards))
        }
    }

    companion object {
        fun fromJson(obj: JSONObject): MoveAction = when (obj.getString("kind")) {
            "PLAY" -> Play(GameStateJson.decodeCards(obj.getJSONArray("cards")))
            "PASS" -> Pass
            "TRADE" -> Trade(GameStateJson.decodeCards(obj.getJSONArray("cards")))
            else -> error("Unknown move kind: $obj")
        }
    }
}

/** Helper to build a Start message from SetupOptions. */
fun SetupOptions.toLanStart(): LanMessage.Start =
    LanMessage.Start(GameStateJson.encodeOptions(this))

/** Sentinel emitted by LanSession when a client socket closes. */
const val LEAVE_SENTINEL = "__LEAVE__"
