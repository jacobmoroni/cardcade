package com.luckyunders.app.persistence

import android.content.Context
import com.luckyunders.app.game.Card
import com.luckyunders.app.game.GameState
import com.luckyunders.app.game.Player
import com.luckyunders.app.game.PlayerOrderRule
import com.luckyunders.app.game.SeriesConfig
import com.luckyunders.app.game.SeriesFormat
import com.luckyunders.app.game.SessionMode
import com.luckyunders.app.game.Suit
import org.json.JSONArray
import org.json.JSONObject

/**
 * A persisted snapshot of the user's current match — enough context to let the
 * Continue Game option resume exactly where the last close happened.
 */
data class GameSnapshot(
    val state: GameState,
    val mode: SessionMode,
    val seriesConfig: SeriesConfig,
    val cumulativeScores: List<Int>,
    val lastRoundPoints: List<Int>,
    val roundOrder: List<Int>,
    val matchOver: Boolean,
    val roundScored: Boolean,
)

/** Simple JSON-backed save slot using SharedPreferences. */
object GameSnapshotStore {
    private const val PREFS = "lucky_unders_prefs"
    private const val KEY_SNAPSHOT = "current_game_snapshot"
    private const val VERSION = 1

    fun save(context: Context, snapshot: GameSnapshot) {
        val json = encode(snapshot).toString()
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SNAPSHOT, json)
            .apply()
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_SNAPSHOT)
            .apply()
    }

    fun hasSnapshot(context: Context): Boolean = load(context) != null

    fun load(context: Context): GameSnapshot? {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_SNAPSHOT, null) ?: return null
        return runCatching { decode(JSONObject(raw)) }.getOrNull()
    }

    private fun encode(s: GameSnapshot): JSONObject = JSONObject().apply {
        put("version", VERSION)
        put("mode", s.mode.name)
        put("series", seriesJson(s.seriesConfig))
        put("cumulativeScores", JSONArray(s.cumulativeScores))
        put("lastRoundPoints", JSONArray(s.lastRoundPoints))
        put("roundOrder", JSONArray(s.roundOrder))
        put("matchOver", s.matchOver)
        put("roundScored", s.roundScored)
        put("state", stateJson(s.state))
    }

    private fun decode(obj: JSONObject): GameSnapshot {
        val mode = SessionMode.valueOf(obj.getString("mode"))
        val series = seriesFromJson(obj.getJSONObject("series"))
        return GameSnapshot(
            state = stateFromJson(obj.getJSONObject("state")),
            mode = mode,
            seriesConfig = series,
            cumulativeScores = obj.getJSONArray("cumulativeScores").toIntList(),
            lastRoundPoints = obj.getJSONArray("lastRoundPoints").toIntList(),
            roundOrder = obj.getJSONArray("roundOrder").toIntList(),
            matchOver = obj.getBoolean("matchOver"),
            roundScored = obj.getBoolean("roundScored"),
        )
    }

    private fun seriesJson(c: SeriesConfig): JSONObject = JSONObject().apply {
        put("format", c.format.name)
        put("targetScore", c.targetScore)
        put("orderRule", c.orderRule.name)
    }

    private fun seriesFromJson(obj: JSONObject): SeriesConfig = SeriesConfig(
        format = SeriesFormat.valueOf(obj.getString("format")),
        targetScore = obj.getInt("targetScore"),
        orderRule = PlayerOrderRule.valueOf(obj.getString("orderRule")),
    )

    private fun stateJson(s: GameState): JSONObject = JSONObject().apply {
        put("currentPlayer", s.currentPlayer)
        put("pile", cardsJson(s.pile))
        put("drawPile", cardsJson(s.drawPile))
        put("winnerOrder", JSONArray(s.winnerOrder))
        put("log", JSONArray(s.log))
        put("pendingFlippedUnder", s.pendingFlippedUnder?.let { cardJson(it) } ?: JSONObject.NULL)
        put("setupPlayer", s.setupPlayer ?: JSONObject.NULL)
        put("setupSwapsDone", s.setupSwapsDone)
        put("firstPlayer", s.firstPlayer)
        put("players", JSONArray().apply {
            s.players.forEach { put(playerJson(it)) }
        })
    }

    private fun stateFromJson(obj: JSONObject): GameState {
        val playersArr = obj.getJSONArray("players")
        val players = (0 until playersArr.length()).map { playerFromJson(playersArr.getJSONObject(it)) }
        val pendingObj = obj.opt("pendingFlippedUnder")
        val pending = if (pendingObj is JSONObject) cardFromJson(pendingObj) else null
        val setupPlayer = if (obj.isNull("setupPlayer")) null else obj.getInt("setupPlayer")
        return GameState(
            players = players,
            currentPlayer = obj.getInt("currentPlayer"),
            pile = cardsFromJson(obj.getJSONArray("pile")),
            drawPile = cardsFromJson(obj.getJSONArray("drawPile")),
            winnerOrder = obj.getJSONArray("winnerOrder").toIntList(),
            log = obj.getJSONArray("log").toStringList(),
            pendingFlippedUnder = pending,
            setupPlayer = setupPlayer,
            setupSwapsDone = obj.getInt("setupSwapsDone"),
            firstPlayer = obj.getInt("firstPlayer"),
        )
    }

    private fun playerJson(p: Player): JSONObject = JSONObject().apply {
        put("id", p.id)
        put("name", p.name)
        put("isHuman", p.isHuman)
        put("hand", cardsJson(p.hand))
        put("overs", cardsJson(p.overs))
        put("unders", cardsJson(p.unders))
    }

    private fun playerFromJson(obj: JSONObject): Player = Player(
        id = obj.getInt("id"),
        name = obj.getString("name"),
        isHuman = obj.getBoolean("isHuman"),
        hand = cardsFromJson(obj.getJSONArray("hand")),
        overs = cardsFromJson(obj.getJSONArray("overs")),
        unders = cardsFromJson(obj.getJSONArray("unders")),
    )

    private fun cardsJson(cards: List<Card>): JSONArray = JSONArray().apply {
        cards.forEach { put(cardJson(it)) }
    }

    private fun cardsFromJson(arr: JSONArray): List<Card> =
        (0 until arr.length()).map { cardFromJson(arr.getJSONObject(it)) }

    private fun cardJson(c: Card): JSONObject = JSONObject().apply {
        put("rank", c.rank)
        put("suit", c.suit.name)
    }

    private fun cardFromJson(obj: JSONObject): Card =
        Card(obj.getInt("rank"), Suit.valueOf(obj.getString("suit")))

    private fun JSONArray.toIntList(): List<Int> =
        (0 until length()).map { getInt(it) }

    private fun JSONArray.toStringList(): List<String> =
        (0 until length()).map { getString(it) }
}
