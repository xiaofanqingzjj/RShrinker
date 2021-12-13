package com.tencent.shrinker

import com.android.build.gradle.AppExtension
import com.android.build.gradle.AppPlugin
import com.tencent.shrinker.util.logger
import org.gradle.api.Plugin
import org.gradle.api.Project


/**
 * 一个可以inline lib库中r文件的插件
 */
class ShrinkerPlugin : Plugin<Project> {

    companion object {
    }

    override fun apply(project: Project) {

        // 只能应用android app工程上
        if (!project.plugins.hasPlugin(AppPlugin::class.java)) {
            throw UnsupportedOperationException("Plugin 'shrinker' can only apply with 'com.android.application'")
        }
        logger.lifecycle("-------------- apply ShrinkerPlugin ----------------")

        // 获取android对象
        val android = project.extensions.getByType(AppExtension::class.java)

        //添加一个extensions，并返回创建好的对象，这个对象可以在build.gradle直接使用，并在其中赋值
        val config = project.extensions.create("shrinkerConfig", ShrinkerConfig::class.java)

        // 注册Transform
        android.registerTransform(InlineRTransform(config))
    }


}