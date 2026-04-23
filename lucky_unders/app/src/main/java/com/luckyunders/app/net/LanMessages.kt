package com.luckyunders.app.net

import com.luckyunders.app.game.Card
import com.luckyunders.app.game.Suit
import com.luckyunders.app.persistence.GameSnapshotStore
import org.json.JSONArray
import org.json.JSONObject

/**
 * Line-delimited JSON messages exchanged between LAN host and clients.
 *
 * Host → client: HELLO, LOBBY, START, STATE, END
 * Client → host: JOIN, MOVE, LEAVE
 */
sealed class LanMessage {
    abstract fun toJson(): JSONObject
    fun encode(): String = toJson().toString()

    data class Join(val displayName: String) : LanMessage() {
        override fun toJson() = JSONObject().apply {
            put("type", "JOIN")
            put("name", displayName)
        }
    }

    data class Hello(val seatId: Int, val hostName: String) : LanMessage() {
        override fun toJson() = JSONObject().apply {
            put("type", "HELLO")
            put("seatId", seatId)
            put("hostName", hostName)
        }
    }

    data class Lobby(val seats: List<LobbySeat>) : LanMessage() {
        override fun toJson() = JSONObject().apply {
            put("type", "LOBBY")
            put("seats", JSONArray().apply {
                seats.forEach { put(JSONObject().apply {
                    put("id", it.id)
                    put("name", it.name)
                    put("kind", it.kind.name)
                }) }
            })
        }
    }

    data object Start : LanMessage() {
        override fun toJson() = JSONObject().apply { put("type", "START") }
    }

    data class State(val stateJson: JSONObject) : LanMessage() {
        override fun toJson() = JSONObject().apply {
            put("type", "STATE")
            put("state", stateJson)
        }
    }

    data class Move(val seatId: Int, val move: GameMove) : LanMessage() {
        override fun toJson() = JSONObject().apply {
            put("type", "MOVE")
            put("seatId", seatId)
            put("move", move.toJson())
        }
    }

    data class Leave(val seatId: Int) : LanMessage() {
        override fun toJson() = JSONObject().apply {
            put("type", "LEAVE")
            put("seatId", seatId)
        }
    }

    data class End(val reason: String) : LanMessage() {
        override fun toJson() = JSONObject().apply {
            put("type", "END")
            put("reason", reason)
        }
    }

    companion object {
        fun decode(line: String): LanMessage? = runCatching {
            val obj = JSONObject(line)
            when (obj.getString("type")) {
                "JOIN" -> Join(obj.getString("name"))
                "HELLO" -> Hello(obj.getInt("seatId"), obj.getString("hostName"))
                "LOBBY" -> Lobby(readSeats(obj.getJSONArray("seats")))
                "START" -> Start
                "STATE" -> State(obj.getJSONObject("state"))
                "MOVE" -> Move(obj.getInt("seatId"), GameMove.fromJson(obj.getJSONObject("move")))
                "LEAVE" -> Leave(obj.getInt("seatId"))
                "END" -> End(obj.getString("reason"))
                else -> null
            }
        }.getOrNull()

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

sealed class GameMove {
    abstract fun toJson(): JSONObject

    data class SwapSetup(val handIndex: Int, val oversIndex: Int) : GameMove() {
        override fun toJson() = JSONObject().apply {
            put("kind", "SWAP")
            put("hand", handIndex)
            put("overs", oversIndex)
        }
    }

    data object FinishSetup : GameMove() {
        override fun toJson() = JSONObject().apply { put("kind", "FINISH_SETUP") }
    }

    data class Play(val cards: List<Card>) : GameMove() {
        override fun toJson() = JSONObject().apply {
            put("kind", "PLAY")
            put("cards", JSONArray().apply {
                cards.forEach {
                    put(JSONObject().apply {
                        put("rank", it.rank)
                        put("suit", it.suit.name)
                    })
                }
            })
        }
    }

    data object PickUp : GameMove() {
        override fun toJson() = JSONObject().apply { put("kind", "PICKUP") }
    }

    data class FlipUnder(val index: Int) : GameMove() {
        override fun toJson() = JSONObject().apply {
            put("kind", "FLIP_UNDER")
            put("index", index)
        }
    }

    companion object {
        fun fromJson(obj: JSONObject): GameMove = when (obj.getString("kind")) {
            "SWAP" -> SwapSetup(obj.getInt("hand"), obj.getInt("overs"))
            "FINISH_SETUP" -> FinishSetup
            "PLAY" -> {
                val arr = obj.getJSONArray("cards")
                val cards = (0 until arr.length()).map {
                    val c = arr.getJSONObject(it)
                    Card(c.getInt("rank"), Suit.valueOf(c.getString("suit")))
                }
                Play(cards)
            }
            "PICKUP" -> PickUp
            "FLIP_UNDER" -> FlipUnder(obj.getInt("index"))
            else -> error("Unknown move: $obj")
        }
    }
}

/** Silences a lint complaint that GameSnapshotStore might otherwise be unused. */
@Suppress("unused") private val keepRef = GameSnapshotStore
