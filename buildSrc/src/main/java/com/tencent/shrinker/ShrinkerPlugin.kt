package com.tencent.shrinker

import com.android.build.gradle.AppExtension
import com.android.build.gradle.AppPlugin
import com.tencent.shrinker.util.logger
import org.gradle.api.Plugin
import org.gradle.api.Project


/**
 *
 *
 *
 */
class ShrinkerPlugin : Plugin<Project> {

    companion object {
    }

    override fun apply(project: Project) {

        // 只能应用在app工程上
        if (!project.plugins.hasPlugin(AppPlugin::class.java)) {
            throw UnsupportedOperationException("Plugin 'shrinker' can only apply with 'com.android.application'")
        }
        logger.lifecycle("-------------- apply ShrinkerPlugin ----------------")


        // android也是加了一个Extension
        val android = project.extensions.getByType(AppExtension::class.java)

        // 获取自己的配置
        val config = project.extensions.create("shrinkerConfig", ShrinkerConfig::class.java)

//        android.registerTransform(TestTransform())

        // 注册Transform
        android.registerTransform(InlineRTransform(config))
    }


}