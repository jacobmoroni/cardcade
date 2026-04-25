package com.cardcade.app.games.scum.persistence

import android.content.Context
import com.cardcade.app.games.scum.game.SeriesConfig
import com.cardcade.app.games.scum.game.SeriesFormat
import com.cardcade.app.games.scum.game.SessionMode
import com.cardcade.app.games.scum.game.SetupOptions

/** SharedPreferences-backed store for Scum's Start Game remembered selections. */
object UserPreferences {
    private const val PREFS = "scum_user_prefs"

    private const val KEY_TOTAL_PLAYERS = "total_players"
    private const val KEY_MODE = "mode"
    private const val KEY_HUMAN_COUNT = "human_count"
    private const val KEY_ROYALTY_TIERS = "royalty_tiers"
    private const val KEY_TOP_TIER_SWAPS = "top_tier_swaps"
    private const val KEY_JOKER_COUNT = "joker_count"
    private const val KEY_JOKERS_UNSWAPPABLE = "jokers_unswappable"
    private const val KEY_JOKER_BEATS_ALL = "joker_beats_all"
    private const val KEY_EXTRA_SUITS = "extra_suits"
    private const val KEY_SERIES_FORMAT = "series_format"
    private const val KEY_TARGET_SCORE = "target_score"
    private const val KEY_FIXED_ROUNDS = "fixed_rounds"
    private const val KEY_RAPID_PLAY = "rapid_play"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun getRapidPlay(context: Context): Boolean =
        prefs(context).getBoolean(KEY_RAPID_PLAY, false)

    fun setRapidPlay(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_RAPID_PLAY, enabled).apply()
    }

    fun getSetupOptions(context: Context, defaults: SetupOptions = SetupOptions()): SetupOptions {
        val p = prefs(context)
        return SetupOptions(
            totalPlayers = p.getInt(KEY_TOTAL_PLAYERS, defaults.totalPlayers),
            mode = SessionMode.valueOf(
                p.getString(KEY_MODE, defaults.mode.name) ?: defaults.mode.name,
            ),
            humanCount = p.getInt(KEY_HUMAN_COUNT, defaults.humanCount),
            royaltyTiers = p.getInt(KEY_ROYALTY_TIERS, defaults.royaltyTiers),
            topTierSwaps = p.getInt(KEY_TOP_TIER_SWAPS, defaults.topTierSwaps),
            jokerCount = p.getInt(KEY_JOKER_COUNT, defaults.jokerCount),
            jokersUnswappable = p.getBoolean(KEY_JOKERS_UNSWAPPABLE, defaults.jokersUnswappable),
            jokerBeatsAll = p.getBoolean(KEY_JOKER_BEATS_ALL, defaults.jokerBeatsAll),
            extraSuits = p.getInt(KEY_EXTRA_SUITS, defaults.extraSuits),
            seriesConfig = SeriesConfig(
                format = SeriesFormat.valueOf(
                    p.getString(KEY_SERIES_FORMAT, defaults.seriesConfig.format.name)
                        ?: defaults.seriesConfig.format.name,
                ),
                targetScore = p.getInt(KEY_TARGET_SCORE, defaults.seriesConfig.targetScore),
                fixedRounds = p.getInt(KEY_FIXED_ROUNDS, defaults.seriesConfig.fixedRounds),
            ),
        )
    }

    fun setSetupOptions(context: Context, opts: SetupOptions) {
        prefs(context).edit()
            .putInt(KEY_TOTAL_PLAYERS, opts.totalPlayers)
            .putString(KEY_MODE, opts.mode.name)
            .putInt(KEY_HUMAN_COUNT, opts.humanCount)
            .putInt(KEY_ROYALTY_TIERS, opts.royaltyTiers)
            .putInt(KEY_TOP_TIER_SWAPS, opts.topTierSwaps)
            .putInt(KEY_JOKER_COUNT, opts.jokerCount)
            .putBoolean(KEY_JOKERS_UNSWAPPABLE, opts.jokersUnswappable)
            .putBoolean(KEY_JOKER_BEATS_ALL, opts.jokerBeatsAll)
            .putInt(KEY_EXTRA_SUITS, opts.extraSuits)
            .putString(KEY_SERIES_FORMAT, opts.seriesConfig.format.name)
            .putInt(KEY_TARGET_SCORE, opts.seriesConfig.targetScore)
            .putInt(KEY_FIXED_ROUNDS, opts.seriesConfig.fixedRounds)
            .apply()
    }
}
