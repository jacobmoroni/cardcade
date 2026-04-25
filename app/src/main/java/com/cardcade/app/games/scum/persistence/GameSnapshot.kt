package com.cardcade.app.games.scum.persistence

import android.content.Context
import com.cardcade.app.games.scum.game.Card
import com.cardcade.app.games.scum.game.CompletedTrade
import com.cardcade.app.games.scum.game.GameState
import com.cardcade.app.games.scum.game.Phase
import com.cardcade.app.games.scum.game.Player
import com.cardcade.app.games.scum.game.Role
import com.cardcade.app.games.scum.game.SeriesConfig
import com.cardcade.app.games.scum.game.SeriesFormat
import com.cardcade.app.games.scum.game.SessionMode
import com.cardcade.app.games.scum.game.SetupOptions
import com.cardcade.app.games.scum.game.Suit
import com.cardcade.app.games.scum.game.TradeSlot
import com.cardcade.app.games.scum.game.TrickPile
import org.json.JSONArray
import org.json.JSONObject

/** Serializable snapshot of an in-progress Scum match. */
data class GameSnapshot(
    val state: GameState,
    val options: SetupOptions,
)

/** JSON-backed save slot; mirrors Lucky Unders' layout but scoped to "scum_". */
object GameSnapshotStore {
    private const val PREFS = "scum_snapshot_prefs"
    private const val KEY_SNAPSHOT = "current_snapshot"
    private const val VERSION = 1

    fun save(context: Context, snapshot: GameSnapshot) {
        val json = encode(snapshot).toString()
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_SNAPSHOT, json).apply()
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().remove(KEY_SNAPSHOT).apply()
    }

    fun hasSnapshot(context: Context): Boolean = load(context) != null

    fun load(context: Context): GameSnapshot? {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_SNAPSHOT, null) ?: return null
        return runCatching { decode(JSONObject(raw)) }.getOrNull()
    }

    // ---- Encode ------------------------------------------------------------------

    private fun encode(snap: GameSnapshot): JSONObject = JSONObject().apply {
        put("version", VERSION)
        put("options", encodeOptions(snap.options))
        put("state", encodeState(snap.state))
    }

    private fun encodeOptions(o: SetupOptions): JSONObject = JSONObject().apply {
        put("totalPlayers", o.totalPlayers)
        put("mode", o.mode.name)
        put("humanCount", o.humanCount)
        put("royaltyTiers", o.royaltyTiers)
        put("topTierSwaps", o.topTierSwaps)
        put("jokerCount", o.jokerCount)
        put("jokersUnswappable", o.jokersUnswappable)
        put("jokerBeatsAll", o.jokerBeatsAll)
        put("extraSuits", o.extraSuits)
        put("seriesFormat", o.seriesConfig.format.name)
        put("targetScore", o.seriesConfig.targetScore)
        put("fixedRounds", o.seriesConfig.fixedRounds)
    }

    private fun encodeState(s: GameState): JSONObject = JSONObject().apply {
        put("phase", s.phase.name)
        put("currentSeat", s.currentSeat)
        put("trickLeader", s.trickLeader)
        put("round", s.round)
        put("pile", encodePile(s.pile))
        put("players", JSONArray().apply { s.players.forEach { put(encodePlayer(it)) } })
        put("finishOrder", JSONArray(s.finishOrder))
        put("log", JSONArray(s.log))
        put("cumulativeScores", JSONArray(s.cumulativeScores))
        put("lastRoundPoints", JSONArray(s.lastRoundPoints))
        put("pendingTrades", JSONArray().apply { s.pendingTrades.forEach { put(encodeTrade(it)) } })
        put("completedTrades", JSONArray().apply {
            s.completedTrades.forEach {
                put(
                    JSONObject().apply {
                        put("slot", encodeTrade(it.slot))
                        put("cards", encodeCards(it.cards))
                    },
                )
            }
        })
        put("previousRoles", JSONArray().apply { s.previousRoles.forEach { put(it.name) } })
    }

    private fun encodePlayer(p: Player): JSONObject = JSONObject().apply {
        put("id", p.id)
        put("name", p.name)
        put("isHuman", p.isHuman)
        put("passedThisTrick", p.passedThisTrick)
        put("isOut", p.isOut)
        put("hand", encodeCards(p.hand))
    }

    private fun encodePile(t: TrickPile): JSONObject = JSONObject().apply {
        put("cards", encodeCards(t.cards))
        put("topRank", t.topRank)
        put("setSize", t.setSize)
        put("leaderSeat", t.leaderSeat)
    }

    private fun encodeTrade(s: TradeSlot): JSONObject = JSONObject().apply {
        put("fromSeat", s.fromSeat)
        put("toSeat", s.toSeat)
        put("required", s.required)
        put("mustPickLowest", s.mustPickLowest)
        put("jokerLocked", s.jokerLocked)
    }

    private fun encodeCards(cards: List<Card>): JSONArray = JSONArray().apply {
        cards.forEach { c ->
            put(
                JSONObject().apply {
                    put("rank", c.rank)
                    put("suit", c.suit?.name ?: JSONObject.NULL)
                    put("copy", c.copy)
                },
            )
        }
    }

    // ---- Decode ------------------------------------------------------------------

    private fun decode(root: JSONObject): GameSnapshot {
        val opts = decodeOptions(root.getJSONObject("options"))
        val state = decodeState(root.getJSONObject("state"))
        return GameSnapshot(state = state, options = opts)
    }

    private fun decodeOptions(o: JSONObject): SetupOptions = SetupOptions(
        totalPlayers = o.getInt("totalPlayers"),
        mode = SessionMode.valueOf(o.getString("mode")),
        humanCount = o.getInt("humanCount"),
        royaltyTiers = o.getInt("royaltyTiers"),
        topTierSwaps = o.getInt("topTierSwaps"),
        jokerCount = o.getInt("jokerCount"),
        jokersUnswappable = o.getBoolean("jokersUnswappable"),
        jokerBeatsAll = o.optBoolean("jokerBeatsAll", false),
        extraSuits = o.getInt("extraSuits"),
        seriesConfig = SeriesConfig(
            format = SeriesFormat.valueOf(o.getString("seriesFormat")),
            targetScore = o.getInt("targetScore"),
            fixedRounds = o.getInt("fixedRounds"),
        ),
    )

    private fun decodeState(s: JSONObject): GameState {
        val playersArr = s.getJSONArray("players")
        val players = (0 until playersArr.length()).map { decodePlayer(playersArr.getJSONObject(it)) }
        return GameState(
            players = players,
            phase = Phase.valueOf(s.getString("phase")),
            currentSeat = s.getInt("currentSeat"),
            trickLeader = s.getInt("trickLeader"),
            round = s.getInt("round"),
            pile = decodePile(s.getJSONObject("pile")),
            finishOrder = s.getJSONArray("finishOrder").toIntList(),
            log = s.getJSONArray("log").toStringList(),
            cumulativeScores = s.getJSONArray("cumulativeScores").toIntList(),
            lastRoundPoints = s.getJSONArray("lastRoundPoints").toIntList(),
            pendingTrades = s.getJSONArray("pendingTrades").toTrades(),
            completedTrades = s.getJSONArray("completedTrades").toCompletedTrades(),
            previousRoles = s.getJSONArray("previousRoles").toRoles(),
        )
    }

    private fun decodePlayer(p: JSONObject): Player = Player(
        id = p.getInt("id"),
        name = p.getString("name"),
        isHuman = p.getBoolean("isHuman"),
        passedThisTrick = p.getBoolean("passedThisTrick"),
        isOut = p.getBoolean("isOut"),
        hand = decodeCards(p.getJSONArray("hand")),
    )

    private fun decodePile(t: JSONObject): TrickPile = TrickPile(
        cards = decodeCards(t.getJSONArray("cards")),
        topRank = t.getInt("topRank"),
        setSize = t.getInt("setSize"),
        leaderSeat = t.getInt("leaderSeat"),
    )

    private fun decodeCards(arr: JSONArray): List<Card> =
        (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            Card(
                rank = o.getInt("rank"),
                suit = if (o.isNull("suit")) null else Suit.valueOf(o.getString("suit")),
                copy = o.getInt("copy"),
            )
        }

    private fun JSONArray.toIntList(): List<Int> = (0 until length()).map { getInt(it) }
    private fun JSONArray.toStringList(): List<String> = (0 until length()).map { getString(it) }
    private fun JSONArray.toTrades(): List<TradeSlot> = (0 until length()).map {
        val o = getJSONObject(it)
        TradeSlot(
            fromSeat = o.getInt("fromSeat"),
            toSeat = o.getInt("toSeat"),
            required = o.getInt("required"),
            mustPickLowest = o.getBoolean("mustPickLowest"),
            jokerLocked = o.getBoolean("jokerLocked"),
        )
    }
    private fun JSONArray.toRoles(): List<Role> =
        (0 until length()).map { Role.valueOf(getString(it)) }
    private fun JSONArray.toCompletedTrades(): List<CompletedTrade> = (0 until length()).map {
        val o = getJSONObject(it)
        val slotJson = o.getJSONObject("slot")
        CompletedTrade(
            slot = TradeSlot(
                fromSeat = slotJson.getInt("fromSeat"),
                toSeat = slotJson.getInt("toSeat"),
                required = slotJson.getInt("required"),
                mustPickLowest = slotJson.getBoolean("mustPickLowest"),
                jokerLocked = slotJson.getBoolean("jokerLocked"),
            ),
            cards = decodeCards(o.getJSONArray("cards")),
        )
    }
}
