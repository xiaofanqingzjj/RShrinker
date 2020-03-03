package com.tencent.shrinker

/**
 *
 * 配置文件
 *
 */
open  class ShrinkerConfig {
    var enableShrink = true
    var shrinkBuildType: MutableList<String> = mutableListOf()
}