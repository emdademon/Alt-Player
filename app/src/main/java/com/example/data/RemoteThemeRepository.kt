package com.example.data

import android.content.Context
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * The small, deliberately limited color document fetched from GitHub. It contains no media
 * information and is kept separate from the local media repositories.
 */
data class AppThemeConfig(
    val themeName: String,
    val background: String,
    val surface: String,
    val border: String,
    val primaryAccent: String,
    val secondaryAccent: String,
    val textPrimary: String,
    val textSecondary: String
) {
    companion object {
        const val DEFAULT_JSON = """
{
  "themeName": "Midnight Indigo",
  "background": "#090A0F",
  "surface": "#12151E",
  "border": "#1E2433",
  "primaryAccent": "#6366F1",
  "secondaryAccent": "#38BDF8",
  "textPrimary": "#F8FAFC",
  "textSecondary": "#94A3B8"
}
"""

        val DEFAULT = AppThemeConfig(
            themeName = "Midnight Indigo",
            background = "#090A0F",
            surface = "#12151E",
            border = "#1E2433",
            primaryAccent = "#6366F1",
            secondaryAccent = "#38BDF8",
            textPrimary = "#F8FAFC",
            textSecondary = "#94A3B8"
        )

        private val hexColorPattern = Regex("^#[0-9A-Fa-f]{6}([0-9A-Fa-f]{2})?$")

        /** Returns the built-in palette if a downloaded document is malformed. */
        fun fromJson(rawJson: String?): AppThemeConfig =
            runCatching { parse(rawJson.orEmpty()) }.getOrElse { DEFAULT }

        /** Strict parser used before a remote document is persisted. */
        fun parse(rawJson: String): AppThemeConfig {
            val json = JSONObject(rawJson)
            fun requiredName(key: String): String {
                val value = json.optString(key).trim()
                require(value.isNotEmpty()) { "Missing theme field: $key" }
                return value
            }

            fun requiredColor(key: String): String {
                val value = requiredName(key).uppercase()
                require(hexColorPattern.matches(value)) { "Invalid theme color: $key" }
                return value
            }

            return AppThemeConfig(
                themeName = requiredName("themeName"),
                background = requiredColor("background"),
                surface = requiredColor("surface"),
                border = requiredColor("border"),
                primaryAccent = requiredColor("primaryAccent"),
                secondaryAccent = requiredColor("secondaryAccent"),
                textPrimary = requiredColor("textPrimary"),
                textSecondary = requiredColor("textSecondary")
            )
        }
    }
}

data class ThemePreferences(
    val useRemoteTheme: Boolean = true,
    val themeJson: String = AppThemeConfig.DEFAULT_JSON,
    val lastError: String? = null
) {
    val activeTheme: AppThemeConfig
        get() = if (useRemoteTheme) AppThemeConfig.fromJson(themeJson) else AppThemeConfig.DEFAULT
}

private val Context.altPlayerThemeDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "altplayer_theme_prefs"
)

/**
 * Fetches only theme.json through the GitHub Contents API. No local media URI, filename, or
 * playback data is sent to the network.
 */
class RemoteThemeRepository(private val context: Context) {
    private object Keys {
        val USE_REMOTE_THEME = booleanPreferencesKey("use_remote_theme")
        val THEME_JSON = stringPreferencesKey("theme_json")
        val LAST_ERROR = stringPreferencesKey("theme_last_error")
    }

    val preferencesFlow: Flow<ThemePreferences> = context.altPlayerThemeDataStore.data.map { prefs ->
        ThemePreferences(
            useRemoteTheme = prefs[Keys.USE_REMOTE_THEME] ?: true,
            themeJson = prefs[Keys.THEME_JSON] ?: AppThemeConfig.DEFAULT_JSON,
            lastError = prefs[Keys.LAST_ERROR]
        )
    }

    suspend fun setRemoteThemeEnabled(enabled: Boolean) {
        context.altPlayerThemeDataStore.edit { prefs ->
            prefs[Keys.USE_REMOTE_THEME] = enabled
            if (!enabled) prefs.remove(Keys.LAST_ERROR)
        }
    }

    suspend fun fetchAndStoreRemoteTheme(): Result<AppThemeConfig> {
        return try {
            val rawJson = withContext(Dispatchers.IO) { downloadThemeDocument() }
            val theme = AppThemeConfig.parse(rawJson)
            context.altPlayerThemeDataStore.edit { prefs ->
                prefs[Keys.THEME_JSON] = rawJson
                prefs.remove(Keys.LAST_ERROR)
            }
            Result.success(theme)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            val message = error.message?.takeIf { it.isNotBlank() } ?: "Theme sync failed"
            context.altPlayerThemeDataStore.edit { prefs ->
                prefs[Keys.LAST_ERROR] = message.take(160)
            }
            Result.failure(error)
        }
    }

    private fun downloadThemeDocument(): String {
        val connection = (URL(THEME_API_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = NETWORK_TIMEOUT_MS
            readTimeout = NETWORK_TIMEOUT_MS
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "Alt-Player-Theme-Sync")
        }

        return try {
            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                throw IOException("GitHub theme request failed ($responseCode)")
            }
            require(connection.url.protocol == "https" && connection.url.host == "api.github.com") {
                "Theme request redirected outside GitHub"
            }
            val apiResponse = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val encodedContent = JSONObject(apiResponse).optString("content").replace("\\s".toRegex(), "")
            require(encodedContent.isNotBlank()) { "GitHub returned no theme content" }
            String(Base64.decode(encodedContent, Base64.DEFAULT), Charsets.UTF_8)
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private const val THEME_API_URL =
            "https://api.github.com/repos/emdademon/Alt-Player/contents/theme.json?ref=main"
        private const val NETWORK_TIMEOUT_MS = 8_000
    }
}
