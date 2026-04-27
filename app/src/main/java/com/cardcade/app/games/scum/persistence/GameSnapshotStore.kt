package com.cardcade.app.games.scum.persistence

import android.content.Context
import com.cardcade.app.games.scum.game.GameStateJson
import org.json.JSONObject

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

    private fun encode(snap: GameSnapshot): JSONObject = JSONObject().apply {
        put("version", VERSION)
        put("options", GameStateJson.encodeOptions(snap.options))
        put("state", GameStateJson.encodeState(snap.state))
    }

    private fun decode(root: JSONObject): GameSnapshot = GameSnapshot(
        options = GameStateJson.decodeOptions(root.getJSONObject("options")),
        state = GameStateJson.decodeState(root.getJSONObject("state")),
    )
}
