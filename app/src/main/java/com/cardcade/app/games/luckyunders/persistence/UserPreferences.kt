package com.cardcade.app.games.luckyunders.persistence

import android.content.Context

/**
 * Tiny SharedPreferences-backed store for per-user UI preferences that should
 * survive app restarts (Rapid Play toggle, Start Game selections, etc.).
 */
object UserPreferences {
    private const val PREFS = "lucky_unders_user_prefs"

    private const val KEY_RAPID_PLAY = "rapid_play_enabled"

    private const val KEY_START_TOTAL_PLAYERS = "start_total_players"
    private const val KEY_START_MODE = "start_mode"
    private const val KEY_START_PASS_PLAY_HUMANS = "start_pass_play_humans"
    private const val KEY_START_SERIES_FORMAT = "start_series_format"
    private const val KEY_START_TARGET_SCORE = "start_target_score"
    private const val KEY_START_ORDER_RULE = "start_order_rule"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun getRapidPlay(context: Context): Boolean =
        prefs(context).getBoolean(KEY_RAPID_PLAY, false)

    fun setRapidPlay(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_RAPID_PLAY, enabled).apply()
    }

    data class StartSelections(
        val totalPlayers: Int,
        val mode: String,
        val passPlayHumans: Int,
        val seriesFormat: String,
        val targetScore: Int,
        val orderRule: String,
    )

    fun getStartSelections(
        context: Context,
        defaults: StartSelections,
    ): StartSelections {
        val p = prefs(context)
        return StartSelections(
            totalPlayers = p.getInt(KEY_START_TOTAL_PLAYERS, defaults.totalPlayers),
            mode = p.getString(KEY_START_MODE, defaults.mode) ?: defaults.mode,
            passPlayHumans = p.getInt(KEY_START_PASS_PLAY_HUMANS, defaults.passPlayHumans),
            seriesFormat = p.getString(KEY_START_SERIES_FORMAT, defaults.seriesFormat)
                ?: defaults.seriesFormat,
            targetScore = p.getInt(KEY_START_TARGET_SCORE, defaults.targetScore),
            orderRule = p.getString(KEY_START_ORDER_RULE, defaults.orderRule)
                ?: defaults.orderRule,
        )
    }

    fun setStartSelections(context: Context, selections: StartSelections) {
        prefs(context).edit()
            .putInt(KEY_START_TOTAL_PLAYERS, selections.totalPlayers)
            .putString(KEY_START_MODE, selections.mode)
            .putInt(KEY_START_PASS_PLAY_HUMANS, selections.passPlayHumans)
            .putString(KEY_START_SERIES_FORMAT, selections.seriesFormat)
            .putInt(KEY_START_TARGET_SCORE, selections.targetScore)
            .putString(KEY_START_ORDER_RULE, selections.orderRule)
            .apply()
    }
}
