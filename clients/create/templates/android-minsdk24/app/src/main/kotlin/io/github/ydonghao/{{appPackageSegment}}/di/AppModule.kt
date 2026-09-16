package io.github.ydonghao.{{appPackageSegment}}.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.ydonghao.{{appPackageSegment}}.BuildConfig
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    /** 环境注入位（client-shared 三-5）：环境切换换的是这里/构建变体，不动业务代码。 */
    @Provides
    @Singleton
    @Named("apiBaseUrl")
    fun provideApiBaseUrl(): String = BuildConfig.API_BASE_URL
}
