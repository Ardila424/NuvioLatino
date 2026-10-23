package com.nuvio.tv.ui.util

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.nuvio.tv.R
import com.nuvio.tv.core.util.withAppLocale

@Composable
fun localizedGenreLabel(genre: String): String {
    val context = LocalContext.current
    return localizedGenreLabel(context, genre)
}

fun localizedGenreLabel(context: Context, genre: String): String {
    // The injected application context follows the device language, not the
    // in-app Appearance language. Without withAppLocale(), English UI shows
    // Spanish genres because the strings resolve against the system locale.
    val localizedContext = context.withAppLocale()
    // Normalize Trakt-style slugs (science-fiction → science fiction) without inventing
    // non-existent genres. Hyphen collapse must keep '&' genres matchable: TMDB
    // "sci-fi & fantasy" becomes "sci fi & fantasy".
    val key = genre.lowercase().trim().replace("-", " ")
    return when (key) {
        "action", "acción", "accion" -> localizedContext.getString(R.string.genre_action)
        "adventure", "aventura" -> localizedContext.getString(R.string.genre_adventure)
        "animation", "animación", "animacion" -> localizedContext.getString(R.string.genre_animation)
        "comedy", "comedia" -> localizedContext.getString(R.string.genre_comedy)
        "crime", "crimen" -> localizedContext.getString(R.string.genre_crime)
        "documentary", "documental" -> localizedContext.getString(R.string.genre_documentary)
        "drama" -> localizedContext.getString(R.string.genre_drama)
        "family", "familia" -> localizedContext.getString(R.string.genre_family)
        "fantasy", "fantasía", "fantasia" -> localizedContext.getString(R.string.genre_fantasy)
        "history", "historia" -> localizedContext.getString(R.string.genre_history)
        "horror", "terror" -> localizedContext.getString(R.string.genre_horror)
        "music", "música", "musica" -> localizedContext.getString(R.string.genre_music)
        "mystery", "misterio" -> localizedContext.getString(R.string.genre_mystery)
        "romance" -> localizedContext.getString(R.string.genre_romance)
        "science fiction", "ciencia ficción", "ciencia ficcion" ->
            localizedContext.getString(R.string.genre_science_fiction)
        "tv movie", "película de tv", "pelicula de tv" -> localizedContext.getString(R.string.genre_tv_movie)
        "thriller", "suspenso" -> localizedContext.getString(R.string.genre_thriller)
        "war", "guerra", "bélica", "belica" -> localizedContext.getString(R.string.genre_war)
        "western" -> localizedContext.getString(R.string.genre_western)
        // Trakt-only genres (from Trakt get-genres movie + show lists)
        "anime" -> localizedContext.getString(R.string.genre_anime)
        "biography", "biografía", "biografia" -> localizedContext.getString(R.string.genre_biography)
        "children", "infantil", "niños", "ninos" -> localizedContext.getString(R.string.genre_children)
        "donghua" -> localizedContext.getString(R.string.genre_donghua)
        "game show" -> localizedContext.getString(R.string.genre_game_show)
        "holiday" -> localizedContext.getString(R.string.genre_holiday)
        "home and garden" -> localizedContext.getString(R.string.genre_home_and_garden)
        "mini series" -> localizedContext.getString(R.string.genre_mini_series)
        "musical" -> localizedContext.getString(R.string.genre_musical)
        "none" -> localizedContext.getString(R.string.genre_none)
        "short" -> localizedContext.getString(R.string.genre_short)
        "special interest" -> localizedContext.getString(R.string.genre_special_interest)
        "sporting event" -> localizedContext.getString(R.string.genre_sporting_event)
        "superhero" -> localizedContext.getString(R.string.genre_superhero)
        "suspense" -> localizedContext.getString(R.string.genre_suspense)
        "talk show" -> localizedContext.getString(R.string.genre_talk)
        // TMDB combined genres (already used by the app; keep after hyphen normalize)
        "action & adventure" -> localizedContext.getString(R.string.genre_action_adventure)
        "kids" -> localizedContext.getString(R.string.genre_kids)
        "news" -> localizedContext.getString(R.string.genre_news)
        "reality" -> localizedContext.getString(R.string.genre_reality)
        "sci fi & fantasy" -> localizedContext.getString(R.string.genre_sci_fi_fantasy)
        "soap" -> localizedContext.getString(R.string.genre_soap)
        "talk" -> localizedContext.getString(R.string.genre_talk)
        "war & politics" -> localizedContext.getString(R.string.genre_war_politics)
        else -> genre
    }
}
