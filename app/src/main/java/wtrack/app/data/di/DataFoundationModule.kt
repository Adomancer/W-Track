package wtrack.app.data.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * M0 data-layer DI marker module.
 *
 * Encrypted Room + SQLCipher persistence (M1 / ADR-005) and NotificationListenerService ingestion
 * bindings (M2 / ADR-003) will be wired here after their respective milestone approvals.
 */
@Module
@InstallIn(SingletonComponent::class)
object DataFoundationModule
