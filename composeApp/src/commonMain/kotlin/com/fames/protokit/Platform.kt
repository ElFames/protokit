package com.fames.protokit

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform