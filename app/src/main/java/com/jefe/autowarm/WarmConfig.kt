package com.jefe.autowarm

data class WarmConfig(
    val sessionMinutes: Int,
    val watchMinSec: Int,
    val watchMaxSec: Int,
    val likeEveryMin: Int,
    val likeEveryMax: Int,
    val likeChancePct: Int,
    val autoScroll: Boolean,
    val autoLike: Boolean,
    val humanMode: Boolean,
    val nicheKeyword: String
)
