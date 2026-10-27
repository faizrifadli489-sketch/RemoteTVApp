package com.faiz.tvremote.remote

/** Nilai RemoteKeyCode dari remotemessage.proto (di-copy agar tidak tergantung lookup runtime). */
object KeyCodes {
    const val UNKNOWN = 0
    const val HOME = 3
    const val BACK = 4
    const val KEY_0 = 7
    const val KEY_1 = 8
    const val KEY_2 = 9
    const val KEY_3 = 10
    const val KEY_4 = 11
    const val KEY_5 = 12
    const val KEY_6 = 13
    const val KEY_7 = 14
    const val KEY_8 = 15
    const val KEY_9 = 16
    const val DPAD_UP = 19
    const val DPAD_DOWN = 20
    const val DPAD_LEFT = 21
    const val DPAD_RIGHT = 22
    const val DPAD_CENTER = 23
    const val VOLUME_UP = 24
    const val VOLUME_DOWN = 25
    const val POWER = 26
    const val A = 29; const val B = 30; const val C = 31; const val D = 32
    const val E = 33; const val F = 34; const val G = 35; const val H = 36
    const val I = 37; const val J = 38; const val K = 39; const val L = 40
    const val M = 41; const val N = 42; const val O = 43; const val P = 44
    const val Q = 45; const val R = 46; const val S = 47; const val T = 48
    const val U = 49; const val V = 50; const val W = 51; const val X = 52
    const val Y = 53; const val Z = 54
    const val COMMA = 55
    const val PERIOD = 56
    const val SPACE = 62
    const val ENTER = 66
    const val DEL = 67
    const val MENU = 82
    const val SEARCH = 84
    const val MEDIA_PLAY_PAUSE = 85
    const val MEDIA_STOP = 86
    const val MEDIA_NEXT = 87
    const val MEDIA_PREVIOUS = 88
    const val MEDIA_REWIND = 89
    const val MEDIA_FAST_FORWARD = 90
    const val MUTE = 91
    const val MEDIA_PLAY = 126
    const val MEDIA_PAUSE = 127
    const val CHANNEL_UP = 166
    const val CHANNEL_DOWN = 167
    const val TV = 170
    const val SETTINGS = 176
}

/** Arah tombol (dari proto: UNKNOWN=0, START_LONG=1, END_LONG=2, SHORT=3). */
object KeyDirection {
    const val SHORT = 3
    const val START_LONG = 1
    const val END_LONG = 2
}

/** Nama key ala web (data-key / KEY_MAP) -> keycode. */
val KEY_MAP: Map<String, Int> = mapOf(
    "power" to KeyCodes.POWER,
    "home" to KeyCodes.HOME,
    "back" to KeyCodes.BACK,
    "menu" to KeyCodes.MENU,
    "up" to KeyCodes.DPAD_UP,
    "down" to KeyCodes.DPAD_DOWN,
    "left" to KeyCodes.DPAD_LEFT,
    "right" to KeyCodes.DPAD_RIGHT,
    "select" to KeyCodes.DPAD_CENTER,
    "volume_up" to KeyCodes.VOLUME_UP,
    "volume_down" to KeyCodes.VOLUME_DOWN,
    "mute" to KeyCodes.MUTE,
    "play_pause" to KeyCodes.MEDIA_PLAY_PAUSE,
    "play" to KeyCodes.MEDIA_PLAY,
    "pause" to KeyCodes.MEDIA_PAUSE,
    "stop" to KeyCodes.MEDIA_STOP,
    "rewind" to KeyCodes.MEDIA_REWIND,
    "fast_forward" to KeyCodes.MEDIA_FAST_FORWARD,
    "next" to KeyCodes.MEDIA_NEXT,
    "previous" to KeyCodes.MEDIA_PREVIOUS,
    "channel_up" to KeyCodes.CHANNEL_UP,
    "channel_down" to KeyCodes.CHANNEL_DOWN,
    "live_tv" to KeyCodes.TV,
    "settings" to KeyCodes.SETTINGS,
    "search" to KeyCodes.SEARCH,
    "digit_0" to KeyCodes.KEY_0,
    "digit_1" to KeyCodes.KEY_1,
    "digit_2" to KeyCodes.KEY_2,
    "digit_3" to KeyCodes.KEY_3,
    "digit_4" to KeyCodes.KEY_4,
    "digit_5" to KeyCodes.KEY_5,
    "digit_6" to KeyCodes.KEY_6,
    "digit_7" to KeyCodes.KEY_7,
    "digit_8" to KeyCodes.KEY_8,
    "digit_9" to KeyCodes.KEY_9,
    "letter_a" to KeyCodes.A, "letter_b" to KeyCodes.B, "letter_c" to KeyCodes.C,
    "letter_d" to KeyCodes.D, "letter_e" to KeyCodes.E, "letter_f" to KeyCodes.F,
    "letter_g" to KeyCodes.G, "letter_h" to KeyCodes.H, "letter_i" to KeyCodes.I,
    "letter_j" to KeyCodes.J, "letter_k" to KeyCodes.K, "letter_l" to KeyCodes.L,
    "letter_m" to KeyCodes.M, "letter_n" to KeyCodes.N, "letter_o" to KeyCodes.O,
    "letter_p" to KeyCodes.P, "letter_q" to KeyCodes.Q, "letter_r" to KeyCodes.R,
    "letter_s" to KeyCodes.S, "letter_t" to KeyCodes.T, "letter_u" to KeyCodes.U,
    "letter_v" to KeyCodes.V, "letter_w" to KeyCodes.W, "letter_x" to KeyCodes.X,
    "letter_y" to KeyCodes.Y, "letter_z" to KeyCodes.Z,
    "space" to KeyCodes.SPACE,
    "enter" to KeyCodes.ENTER,
    "del" to KeyCodes.DEL,
    "period" to KeyCodes.PERIOD,
    "comma" to KeyCodes.COMMA,
)

/** Deep link aplikasi populer (sama seperti APP_LINKS di server.js). */
val APP_LINKS: Map<String, String> = mapOf(
    "youtube" to "https://www.youtube.com",
    "netflix" to "https://www.netflix.com/title",
    "disney+" to "https://www.disneyplus.com",
    "prime video" to "https://app.primevideo.com",
    "spotify" to "https://open.spotify.com",
)

/** Karakter -> nama key (sama seperti charToKeyName di server.js). */
fun charToKeyName(c: Char): String? {
    val lower = c.lowercaseChar()
    return when {
        lower in 'a'..'z' -> "letter_$lower"
        lower in '0'..'9' -> "digit_$lower"
        c == ' ' -> "space"
        c == '.' -> "period"
        c == ',' -> "comma"
        else -> null
    }
}
