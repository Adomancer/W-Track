package wtrack.app.data.di

import android.content.Context
import dagger.Lazy
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import wtrack.app.core.coroutines.DispatcherProvider
import wtrack.app.core.logging.WtrackLogger
import wtrack.app.data.backup.BackupService
import wtrack.app.data.backup.CsvExporter
import wtrack.app.data.backup.M3aSampleExporter
import wtrack.app.data.backup.WtrackBackupAndDiagnosticsPort
import wtrack.app.data.crypto.AndroidKeyStoreWrappingCipher
import wtrack.app.data.crypto.DatabaseKeyManager
import wtrack.app.data.crypto.KeyWrappingCipher
import wtrack.app.data.crypto.WrappedKeyStore
import wtrack.app.data.crypto.WtrackDatabaseHolder
import wtrack.app.data.ingestion.AllowlistCache
import wtrack.app.data.ingestion.CaptureStatusTracker
import wtrack.app.data.ingestion.CustomParserRuleCache
import wtrack.app.data.ingestion.EventRecoveryCoordinator
import wtrack.app.data.ingestion.HeartbeatMonitor
import wtrack.app.data.ingestion.IngestionPipeline
import wtrack.app.data.ingestion.SmartParserRegistry
import wtrack.app.data.local.WtrackDatabase
import wtrack.app.data.local.preferences.AppSettingsDataStore
import wtrack.app.data.notification.AndroidStatusAlertNotifier
import wtrack.app.data.repository.AccountRepositoryImpl
import wtrack.app.data.repository.BudgetRepositoryImpl
import wtrack.app.data.repository.CaptureStatusRepositoryImpl
import wtrack.app.data.repository.CategoryRepositoryImpl
import wtrack.app.data.repository.CustomParserRuleRepositoryImpl
import wtrack.app.data.repository.DatabaseHealthRepositoryImpl
import wtrack.app.data.repository.MerchantRepositoryImpl
import wtrack.app.data.repository.NeedsReviewRepositoryImpl
import wtrack.app.data.repository.RuleRepositoryImpl
import wtrack.app.data.repository.SettingsRepositoryImpl
import wtrack.app.data.repository.SourceAndEventRepositoryImpl
import wtrack.app.data.repository.TagRepositoryImpl
import wtrack.app.data.repository.TransactionRepositoryImpl
import wtrack.app.data.repository.TransferRepositoryImpl
import wtrack.app.data.worker.RetentionPurgeRunner
import wtrack.app.domain.parser.ParserRegistry
import wtrack.app.domain.repository.AccountRepository
import wtrack.app.domain.repository.BackupAndDiagnosticsPort
import wtrack.app.domain.repository.BudgetRepository
import wtrack.app.domain.repository.CaptureStatusRepository
import wtrack.app.domain.repository.CategoryRepository
import wtrack.app.domain.repository.CustomParserRuleRepository
import wtrack.app.domain.repository.DatabaseHealthRepository
import wtrack.app.domain.repository.MerchantRepository
import wtrack.app.domain.repository.NeedsReviewRepository
import wtrack.app.domain.repository.RuleRepository
import wtrack.app.domain.repository.SettingsRepository
import wtrack.app.domain.repository.SourceAndEventRepository
import wtrack.app.domain.repository.StatusAlertNotifier
import wtrack.app.domain.repository.TagRepository
import wtrack.app.domain.repository.TransactionRepository
import wtrack.app.domain.repository.TransferRepository
import wtrack.app.domain.time.Clock
import wtrack.integration.TransactionParser
import wtrack.integration.providers.ProviderParsers

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object DataFoundationModule {
    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(dispatcherProvider: DispatcherProvider): CoroutineScope =
        CoroutineScope(SupervisorJob() + dispatcherProvider.io)

    @Provides
    @Singleton
    fun provideWtrackLogger(
        @ApplicationContext context: Context,
        clock: Clock,
    ): WtrackLogger = WtrackLogger(
        logFile = File(File(context.filesDir, "logs"), "wtrack.log"),
        clock = clock,
    )

    @Provides
    @Singleton
    fun provideKeyWrappingCipher(): KeyWrappingCipher = AndroidKeyStoreWrappingCipher()

    @Provides
    @Singleton
    fun provideWrappedKeyStore(@ApplicationContext context: Context): WrappedKeyStore =
        WrappedKeyStore(File(File(context.filesDir, "keystore"), "db_key.bin"))

    @Provides
    @Singleton
    fun provideDatabaseKeyManager(
        @ApplicationContext context: Context,
        wrappedKeyStore: WrappedKeyStore,
        wrappingCipher: KeyWrappingCipher,
    ): DatabaseKeyManager = DatabaseKeyManager(
        dbFile = context.getDatabasePath(WtrackDatabase.DATABASE_NAME),
        wrappedKeyStore = wrappedKeyStore,
        wrappingCipher = wrappingCipher,
    )

    @Provides
    @Singleton
    fun provideWtrackDatabaseHolder(
        @ApplicationContext context: Context,
        keyManager: DatabaseKeyManager,
    ): WtrackDatabaseHolder = WtrackDatabaseHolder(
        context = context,
        keyManager = keyManager,
    )

    @Provides
    @Singleton
    fun provideAppSettingsDataStore(@ApplicationContext context: Context): AppSettingsDataStore =
        AppSettingsDataStore(context)

    @Provides
    @Singleton
    fun provideAllowlistCache(
        holder: WtrackDatabaseHolder,
        @ApplicationScope applicationScope: CoroutineScope,
    ): AllowlistCache = AllowlistCache(holder = holder, scope = applicationScope)

    @Provides
    @Singleton
    fun provideCustomParserRuleCache(
        holder: WtrackDatabaseHolder,
        @ApplicationScope applicationScope: CoroutineScope,
    ): CustomParserRuleCache = CustomParserRuleCache(holder = holder, scope = applicationScope)

    @Provides
    @Singleton
    fun provideParserRegistry(
        boundParsers: Set<@JvmSuppressWildcards TransactionParser>,
        customParserRuleCache: CustomParserRuleCache,
    ): ParserRegistry = SmartParserRegistry(
        builtInParsers = ProviderParsers.registered() + boundParsers,
        customRulesProvider = { sourceId -> customParserRuleCache.rulesForSource(sourceId) },
        customSourceIdsProvider = { customParserRuleCache.enabledSourceIds() },
        enableGenericFallback = true,
    )

    @Provides
    @Singleton
    fun provideCaptureStatusTracker(
        @ApplicationContext context: Context,
        holder: WtrackDatabaseHolder,
        clock: Clock,
        logger: WtrackLogger,
    ): CaptureStatusTracker = CaptureStatusTracker(
        context = context,
        holder = holder,
        clock = clock,
        logger = logger,
    )

    @Provides
    @Singleton
    fun provideHeartbeatMonitor(
        holder: WtrackDatabaseHolder,
        settingsStore: AppSettingsDataStore,
        clock: Clock,
        logger: WtrackLogger,
    ): HeartbeatMonitor = HeartbeatMonitor(
        holder = holder,
        settingsStore = settingsStore,
        clock = clock,
        logger = logger,
    )

    @Provides
    @Singleton
    fun provideStatusAlertNotifier(
        @ApplicationContext context: Context,
        settingsStore: AppSettingsDataStore,
    ): StatusAlertNotifier = AndroidStatusAlertNotifier(context = context, settingsStore = settingsStore)

    @Provides
    @Singleton
    fun provideIngestionPipeline(
        holder: WtrackDatabaseHolder,
        allowlistCache: AllowlistCache,
        parserRegistry: ParserRegistry,
        settingsStore: AppSettingsDataStore,
        heartbeatMonitor: HeartbeatMonitor,
        clock: Clock,
        logger: WtrackLogger,
        statusAlertNotifier: StatusAlertNotifier,
    ): IngestionPipeline = IngestionPipeline(
        holder = holder,
        allowlistCache = allowlistCache,
        parserRegistry = parserRegistry,
        settingsStore = settingsStore,
        heartbeatMonitor = heartbeatMonitor,
        clock = clock,
        logger = logger,
        statusAlertNotifier = statusAlertNotifier,
    )

    @Provides
    @Singleton
    fun provideEventRecoveryCoordinator(
        holder: WtrackDatabaseHolder,
        pipeline: IngestionPipeline,
        clock: Clock,
        logger: WtrackLogger,
    ): EventRecoveryCoordinator = EventRecoveryCoordinator(
        holder = holder,
        pipeline = pipeline,
        clock = clock,
        logger = logger,
    )

    @Provides
    @Singleton
    fun provideRetentionPurgeRunner(
        holder: WtrackDatabaseHolder,
        sourceAndEventRepository: SourceAndEventRepository,
        heartbeatMonitor: HeartbeatMonitor,
        logger: WtrackLogger,
        clock: Clock,
        settingsStore: AppSettingsDataStore,
        statusAlertNotifier: StatusAlertNotifier,
    ): RetentionPurgeRunner = RetentionPurgeRunner(
        holder = holder,
        sourceAndEventRepository = sourceAndEventRepository,
        heartbeatMonitor = heartbeatMonitor,
        logger = logger,
        clock = clock,
        settingsStore = settingsStore,
        statusAlertNotifier = statusAlertNotifier,
    )

    @Provides
    @Singleton
    fun provideAccountRepository(
        holder: WtrackDatabaseHolder,
        clock: Clock,
        captureStatusTracker: CaptureStatusTracker,
        parserRegistry: ParserRegistry,
    ): AccountRepository = AccountRepositoryImpl(
        holder = holder,
        clock = clock,
        listenerConnectedFlow = captureStatusTracker.isListenerConnected,
        parserRegistry = parserRegistry,
        permissionGrantedProvider = { captureStatusTracker.isNotificationAccessGranted() },
    )

    @Provides
    @Singleton
    fun provideTransactionRepository(
        holder: WtrackDatabaseHolder,
        settingsStore: AppSettingsDataStore,
        clock: Clock,
    ): TransactionRepository = TransactionRepositoryImpl(holder, settingsStore, clock)

    @Provides
    @Singleton
    fun provideNeedsReviewRepository(
        holder: WtrackDatabaseHolder,
        settingsStore: AppSettingsDataStore,
        clock: Clock,
        pipelineLazy: Lazy<IngestionPipeline>,
    ): NeedsReviewRepository = NeedsReviewRepositoryImpl(
        holder = holder,
        settingsStore = settingsStore,
        clock = clock,
        onRetryRequested = { eventId -> pipelineLazy.get().processEvent(eventId) },
    )

    @Provides
    @Singleton
    fun provideTransferRepository(
        holder: WtrackDatabaseHolder,
        clock: Clock,
    ): TransferRepository = TransferRepositoryImpl(holder, clock)

    @Provides
    @Singleton
    fun provideCategoryRepository(
        holder: WtrackDatabaseHolder,
        clock: Clock,
    ): CategoryRepository = CategoryRepositoryImpl(holder, clock)

    @Provides
    @Singleton
    fun provideMerchantRepository(
        holder: WtrackDatabaseHolder,
        clock: Clock,
    ): MerchantRepository = MerchantRepositoryImpl(holder, clock)

    @Provides
    @Singleton
    fun provideRuleRepository(
        holder: WtrackDatabaseHolder,
        clock: Clock,
    ): RuleRepository = RuleRepositoryImpl(holder, clock)

    @Provides
    @Singleton
    fun provideCustomParserRuleRepository(
        holder: WtrackDatabaseHolder,
        customParserRuleCache: CustomParserRuleCache,
        clock: Clock,
        pipelineLazy: Lazy<IngestionPipeline>,
    ): CustomParserRuleRepository = CustomParserRuleRepositoryImpl(
        holder = holder,
        ruleCache = customParserRuleCache,
        clock = clock,
        onRetryEvent = { eventId -> pipelineLazy.get().processEvent(eventId) },
    )

    @Provides
    @Singleton
    fun provideTagRepository(
        holder: WtrackDatabaseHolder,
        clock: Clock,
    ): TagRepository = TagRepositoryImpl(holder, clock)

    @Provides
    @Singleton
    fun provideBudgetRepository(
        holder: WtrackDatabaseHolder,
        clock: Clock,
    ): BudgetRepository = BudgetRepositoryImpl(holder, clock)

    @Provides
    @Singleton
    fun provideSourceAndEventRepository(
        holder: WtrackDatabaseHolder,
        clock: Clock,
    ): SourceAndEventRepository = SourceAndEventRepositoryImpl(holder, clock)

    @Provides
    @Singleton
    fun provideCaptureStatusRepository(
        holder: WtrackDatabaseHolder,
        settingsStore: AppSettingsDataStore,
        captureStatusTracker: CaptureStatusTracker,
    ): CaptureStatusRepository = CaptureStatusRepositoryImpl(
        holder = holder,
        settingsStore = settingsStore,
        permissionGrantedProvider = { captureStatusTracker.isNotificationAccessGranted() },
        listenerConnectedFlow = captureStatusTracker.isListenerConnected,
        onRefreshStatus = { captureStatusTracker.onPermissionStateChecked() },
    )

    @Provides
    @Singleton
    fun provideSettingsRepository(settingsStore: AppSettingsDataStore): SettingsRepository =
        SettingsRepositoryImpl(settingsStore)

    @Provides
    @Singleton
    fun provideDatabaseHealthRepository(
        holder: WtrackDatabaseHolder,
        settingsStore: AppSettingsDataStore,
    ): DatabaseHealthRepository = DatabaseHealthRepositoryImpl(holder, settingsStore)

    @Provides
    @Singleton
    fun provideBackupService(
        holder: WtrackDatabaseHolder,
        settingsStore: AppSettingsDataStore,
        allowlistCache: AllowlistCache,
        captureStatusTracker: CaptureStatusTracker,
        clock: Clock,
        logger: WtrackLogger,
        customParserRuleCache: CustomParserRuleCache,
    ): BackupService = BackupService(
        holder = holder,
        settingsStore = settingsStore,
        allowlistCache = allowlistCache,
        captureStatusTracker = captureStatusTracker,
        clock = clock,
        logger = logger,
        customParserRuleCache = customParserRuleCache,
    )

    @Provides
    @Singleton
    fun provideCsvExporter(): CsvExporter = CsvExporter()

    @Provides
    @Singleton
    fun provideM3aSampleExporter(clock: Clock): M3aSampleExporter = M3aSampleExporter(clock)

    @Provides
    @Singleton
    fun provideBackupAndDiagnosticsPort(
        holder: WtrackDatabaseHolder,
        backupService: BackupService,
        csvExporter: CsvExporter,
        m3aSampleExporter: M3aSampleExporter,
        accountRepository: AccountRepository,
        transactionRepository: TransactionRepository,
        categoryRepository: CategoryRepository,
        databaseHealthRepository: DatabaseHealthRepository,
        logger: WtrackLogger,
        allowlistCache: AllowlistCache,
        parserRegistry: ParserRegistry,
        ingestionPipeline: IngestionPipeline,
        clock: Clock,
        captureStatusTracker: CaptureStatusTracker,
        customParserRuleCache: CustomParserRuleCache,
    ): BackupAndDiagnosticsPort = WtrackBackupAndDiagnosticsPort(
        holder = holder,
        backupService = backupService,
        csvExporter = csvExporter,
        m3aSampleExporter = m3aSampleExporter,
        accountRepository = accountRepository,
        transactionRepository = transactionRepository,
        categoryRepository = categoryRepository,
        databaseHealthRepository = databaseHealthRepository,
        logger = logger,
        allowlistCache = allowlistCache,
        parserRegistry = parserRegistry,
        ingestionPipeline = ingestionPipeline,
        clock = clock,
        captureStatusTracker = captureStatusTracker,
        customParserRuleCache = customParserRuleCache,
    )
}
