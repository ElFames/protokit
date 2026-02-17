package com.fames.protokit.sdk.models

enum class Platform {
    ANDROID, IOS, DESKTOP
}

expect fun platform(): Platform