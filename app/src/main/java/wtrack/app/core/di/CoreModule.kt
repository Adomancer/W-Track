package wtrack.app.core.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds
import javax.inject.Singleton
import wtrack.app.core.coroutines.DefaultDispatcherProvider
import wtrack.app.core.coroutines.DispatcherProvider
import wtrack.app.core.time.SystemClock
import wtrack.app.domain.time.Clock
import wtrack.integration.TransactionParser

@Module
@InstallIn(SingletonComponent::class)
object CoreModule {
    @Provides
    @Singleton
    fun provideClock(): Clock = SystemClock()

    @Provides
    @Singleton
    fun provideDispatcherProvider(): DispatcherProvider = DefaultDispatcherProvider()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class IntegrationBindingsModule {
    @Multibinds
    abstract fun bindTransactionParsers(): Set<TransactionParser>
}
