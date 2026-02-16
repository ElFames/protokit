package com.fames.protokit.runtime

enum class Platform {
    ANDROID, IOS, DESKTOP
}

expect fun platform(): Platform