package wtrack.app.core.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import wtrack.app.domain.repository.AccountRepository
import wtrack.app.domain.repository.BackupAndDiagnosticsPort
import wtrack.app.domain.repository.NeedsReviewRepository
import wtrack.app.domain.repository.RuleRepository
import wtrack.app.domain.repository.TransactionRepository
import wtrack.app.domain.repository.TransferRepository
import wtrack.app.domain.time.Clock
import wtrack.app.domain.usecase.AggregateCalculator
import wtrack.app.domain.usecase.BalanceCheckpointEvaluator
import wtrack.app.domain.usecase.CorrectBalanceUseCase
import wtrack.app.domain.usecase.CreateManualTransactionUseCase
import wtrack.app.domain.usecase.CreateRuleWithBackfillUseCase
import wtrack.app.domain.usecase.DeleteManualTransactionUseCase
import wtrack.app.domain.usecase.GateG1Evaluator
import wtrack.app.domain.usecase.LinkRefundUseCase
import wtrack.app.domain.usecase.LinkTransferUseCase
import wtrack.app.domain.usecase.ResolveNeedsReviewUseCase
import wtrack.app.domain.usecase.UnlinkTransferUseCase
import wtrack.app.domain.usecase.UpdateTransactionUseCase
import wtrack.app.domain.usecase.VoidRestoreTransactionUseCase
import wtrack.app.domain.usecase.WipeAllDataUseCase

@Module
@InstallIn(SingletonComponent::class)
object DomainUseCaseModule {
    @Provides
    @Singleton
    fun provideAggregateCalculator(): AggregateCalculator = AggregateCalculator()

    @Provides
    @Singleton
    fun provideBalanceCheckpointEvaluator(): BalanceCheckpointEvaluator = BalanceCheckpointEvaluator()

    @Provides
    @Singleton
    fun provideGateG1Evaluator(): GateG1Evaluator = GateG1Evaluator()

    @Provides
    @Singleton
    fun provideCorrectBalanceUseCase(
        accountRepository: AccountRepository,
        transactionRepository: TransactionRepository,
        clock: Clock,
    ): CorrectBalanceUseCase = CorrectBalanceUseCase(accountRepository, transactionRepository, clock)

    @Provides
    @Singleton
    fun provideLinkTransferUseCase(
        transactionRepository: TransactionRepository,
        transferRepository: TransferRepository,
    ): LinkTransferUseCase = LinkTransferUseCase(transactionRepository, transferRepository)

    @Provides
    @Singleton
    fun provideUnlinkTransferUseCase(
        transferRepository: TransferRepository,
    ): UnlinkTransferUseCase = UnlinkTransferUseCase(transferRepository)

    @Provides
    @Singleton
    fun provideCreateRuleWithBackfillUseCase(
        ruleRepository: RuleRepository,
        transactionRepository: TransactionRepository,
    ): CreateRuleWithBackfillUseCase = CreateRuleWithBackfillUseCase(ruleRepository, transactionRepository)

    @Provides
    @Singleton
    fun provideCreateManualTransactionUseCase(
        transactionRepository: TransactionRepository,
    ): CreateManualTransactionUseCase = CreateManualTransactionUseCase(transactionRepository)

    @Provides
    @Singleton
    fun provideUpdateTransactionUseCase(
        transactionRepository: TransactionRepository,
        transferRepository: TransferRepository,
    ): UpdateTransactionUseCase = UpdateTransactionUseCase(transactionRepository, transferRepository)

    @Provides
    @Singleton
    fun provideVoidRestoreTransactionUseCase(
        transactionRepository: TransactionRepository,
    ): VoidRestoreTransactionUseCase = VoidRestoreTransactionUseCase(transactionRepository)

    @Provides
    @Singleton
    fun provideDeleteManualTransactionUseCase(
        transactionRepository: TransactionRepository,
    ): DeleteManualTransactionUseCase = DeleteManualTransactionUseCase(transactionRepository)

    @Provides
    @Singleton
    fun provideLinkRefundUseCase(
        transactionRepository: TransactionRepository,
    ): LinkRefundUseCase = LinkRefundUseCase(transactionRepository)

    @Provides
    @Singleton
    fun provideResolveNeedsReviewUseCase(
        needsReviewRepository: NeedsReviewRepository,
        accountRepository: AccountRepository,
    ): ResolveNeedsReviewUseCase = ResolveNeedsReviewUseCase(needsReviewRepository, accountRepository)

    @Provides
    @Singleton
    fun provideWipeAllDataUseCase(
        backupAndDiagnosticsPort: BackupAndDiagnosticsPort,
    ): WipeAllDataUseCase = WipeAllDataUseCase(backupAndDiagnosticsPort)
}
