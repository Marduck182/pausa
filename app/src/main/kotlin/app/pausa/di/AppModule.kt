package app.pausa.di

import android.content.Context
import app.pausa.data.DataGraph
import app.pausa.domain.engine.RuleEngine
import app.pausa.domain.repository.DataEraser
import app.pausa.domain.repository.InstalledAppsRepository
import app.pausa.domain.repository.RuleRepository
import app.pausa.domain.repository.SettingsRepository
import app.pausa.domain.repository.StatsRepository
import app.pausa.domain.repository.UsageRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun dataGraph(@ApplicationContext context: Context): DataGraph = DataGraph(context)

    @Provides
    fun ruleRepository(graph: DataGraph): RuleRepository = graph.rules

    @Provides
    fun usageRepository(graph: DataGraph): UsageRepository = graph.usage

    @Provides
    fun statsRepository(graph: DataGraph): StatsRepository = graph.stats

    @Provides
    fun settingsRepository(graph: DataGraph): SettingsRepository = graph.settings

    @Provides
    fun dataEraser(graph: DataGraph): DataEraser = graph.eraser

    @Provides
    fun installedAppsRepository(graph: DataGraph): InstalledAppsRepository = graph.installedApps

    @Provides
    @Singleton
    fun ruleEngine(): RuleEngine = RuleEngine()

    /** One injectable clock, so anything time-dependent can be tested with a fixed one. */
    @Provides
    fun clock(): Clock = Clock.systemDefaultZone()
}
