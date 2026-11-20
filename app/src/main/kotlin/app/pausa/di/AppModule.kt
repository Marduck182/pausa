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
import app.pausa.domain.usecase.RuleLockChecker
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
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

    /**
     * One injectable clock, so anything time-dependent can be tested with a fixed one. It asks for the phone's zone
     * every time instead of keeping the one from when it was created: the guard lives for days, and "today" must
     * change when the person travels or the zone changes.
     */
    @Provides
    fun clock(): Clock = object : Clock() {
        override fun getZone(): ZoneId = ZoneId.systemDefault()
        override fun withZone(zone: ZoneId): Clock = system(zone)
        override fun instant(): Instant = Instant.now()
    }

    @Provides
    fun ruleLockChecker(
        rules: RuleRepository,
        usage: UsageRepository,
        settings: SettingsRepository,
        clock: Clock,
    ): RuleLockChecker = RuleLockChecker(rules, usage, settings, clock)
}
