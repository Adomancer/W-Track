package wtrack.app.feature.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import wtrack.app.R
import wtrack.app.domain.model.Account
import wtrack.app.domain.model.AccountSource
import wtrack.app.domain.model.AccountType
import wtrack.app.domain.model.CurrencyRegistry
import wtrack.app.domain.model.MoneyFormatter
import wtrack.app.domain.model.Transaction
import wtrack.app.domain.model.TransactionSource
import wtrack.app.domain.repository.AccountRepository
import wtrack.app.domain.repository.BackupAndDiagnosticsPort
import wtrack.app.domain.repository.CreateAccountRequest
import wtrack.app.domain.repository.CustomParserRuleRepository
import wtrack.app.domain.repository.ProviderCatalogEntry
import wtrack.app.domain.repository.SettingsRepository
import wtrack.app.domain.repository.SourceAndEventRepository
import wtrack.app.domain.repository.TransactionRepository
import wtrack.app.domain.repository.UpdateAccountRequest
import wtrack.app.domain.time.Clock
import wtrack.app.domain.usecase.BalanceCheckpointEvaluation
import wtrack.app.domain.usecase.BalanceCheckpointEvaluator
import wtrack.app.domain.usecase.CorrectBalanceUseCase
import wtrack.app.feature.common.UiText

data class LinkedSourceUi(
    val link: AccountSource,
    val source: TransactionSource?,
)

data class AccountCardUiModel(
    val account: Account,
    val linkedSources: List<LinkedSourceUi>,
    val checkpoint: BalanceCheckpointEvaluation?,
)

data class AccountsUiState(
    val activeAccounts: List<AccountCardUiModel> = emptyList(),
    val archivedAccounts: List<AccountCardUiModel> = emptyList(),
    val sources: List<TransactionSource> = emptyList(),
    val providerCatalog: List<ProviderCatalogEntry> = emptyList(),
    val customRuleCountsBySource: Map<String, Int> = emptyMap(),
    val baseCurrency: String = CurrencyRegistry.DEFAULT_CURRENCY,
    val nowEpochMillis: Long = 0L,
    val actionMessage: UiText? = null,
)

@HiltViewModel
class AccountsViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
    private val sourceRepository: SourceAndEventRepository,
    transactionRepository: TransactionRepository,
    customParserRuleRepository: CustomParserRuleRepository,
    private val settingsRepository: SettingsRepository,
    private val backupPort: BackupAndDiagnosticsPort,
    private val checkpointEvaluator: BalanceCheckpointEvaluator,
    private val correctBalanceUseCase: CorrectBalanceUseCase,
    private val clock: Clock,
) : ViewModel() {

    private val actionMessage = MutableStateFlow<UiText?>(null)

    private val repoSnapshot = combine(
        accountRepository.accounts,
        accountRepository.accountSources,
        sourceRepository.sources,
        transactionRepository.transactions,
        customParserRuleRepository.rules,
    ) { accounts, accountSources, sources, transactions, customRules ->
        AccountDataSnapshot(
            accounts = accounts,
            accountSources = accountSources,
            sources = sources,
            transactions = transactions,
            customRuleCountsBySource = customRules
                .asSequence()
                .filter { it.enabled }
                .groupingBy { it.sourceId }
                .eachCount(),
        )
    }

    val uiState: StateFlow<AccountsUiState> = combine(
        repoSnapshot,
        settingsRepository.settings,
        actionMessage,
    ) { snap, settings, message ->
        val sourcesById = snap.sources.associateBy { it.id }
        val cards = snap.accounts.map { account ->
            val links = snap.accountSources
                .filter { it.accountId == account.id }
                .map { link -> LinkedSourceUi(link, sourcesById[link.sourceId]) }
            val checkpoint = checkpointEvaluator.evaluateLatest(account, snap.transactions)
            AccountCardUiModel(
                account = account,
                linkedSources = links,
                checkpoint = checkpoint,
            )
        }
        AccountsUiState(
            activeAccounts = cards.filter { it.account.isActive },
            archivedAccounts = cards.filter { !it.account.isActive },
            sources = snap.sources,
            providerCatalog = backupPort.providerCatalogEntries(),
            customRuleCountsBySource = snap.customRuleCountsBySource,
            baseCurrency = settings.baseCurrency.ifBlank { CurrencyRegistry.DEFAULT_CURRENCY },
            nowEpochMillis = clock.nowEpochMillis(),
            actionMessage = message,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = AccountsUiState(),
    )

    fun createAccount(
        name: String,
        provider: String,
        type: AccountType,
        currency: String,
        openingBalanceInput: String,
    ) {
        viewModelScope.launch {
            val defaultCurrency = settingsRepository.current().baseCurrency.ifBlank { CurrencyRegistry.DEFAULT_CURRENCY }
            val normalizedCurrency = currency.trim().uppercase().ifEmpty { defaultCurrency }
            val openingMinor = if (openingBalanceInput.isBlank()) {
                null
            } else {
                MoneyFormatter.parseMajorInput(openingBalanceInput, normalizedCurrency) ?: run {
                    actionMessage.value = UiText.Resource(R.string.msg_invalid_opening_balance, normalizedCurrency)
                    return@launch
                }
            }
            val now = if (openingMinor != null) clock.nowEpochMillis() else null
            runCatching {
                accountRepository.create(
                    CreateAccountRequest(
                        name = name,
                        provider = provider,
                        accountType = type,
                        currency = normalizedCurrency,
                        initialBalanceMinor = openingMinor,
                        initialBalanceAt = now,
                    ),
                )
            }.onSuccess {
                actionMessage.value = UiText.Resource(R.string.msg_account_created)
            }.onFailure { err ->
                actionMessage.value = UiText.fromError(err, R.string.msg_failed_create_account)
            }
        }
    }

    fun updateAccount(id: Long, name: String, provider: String, type: AccountType, currency: String) {
        viewModelScope.launch {
            runCatching {
                accountRepository.update(UpdateAccountRequest(id, name, provider, type, currency))
            }.onSuccess {
                actionMessage.value = UiText.Resource(R.string.msg_account_updated)
            }.onFailure {
                actionMessage.value = UiText.fromError(it, R.string.msg_failed_update_account)
            }
        }
    }

    fun archiveAccount(id: Long) {
        viewModelScope.launch {
            accountRepository.archive(id)
            actionMessage.value = UiText.Resource(R.string.msg_account_archived)
        }
    }

    fun restoreAccount(id: Long) {
        viewModelScope.launch {
            accountRepository.restore(id)
            actionMessage.value = UiText.Resource(R.string.msg_account_restored)
        }
    }

    fun linkSource(
        accountId: Long,
        packageName: String,
        displayName: String,
        accountHint: String?,
    ) {
        viewModelScope.launch {
            val cleanedPkg = packageName.trim()
            if (cleanedPkg.isEmpty()) {
                actionMessage.value = UiText.Resource(R.string.msg_enter_android_package_id)
                return@launch
            }
            runCatching {
                val existingSource = sourceRepository.getSource(cleanedPkg)
                sourceRepository.upsertSource(
                    packageName = cleanedPkg,
                    displayName = displayName.trim().ifEmpty { cleanedPkg },
                    isAllowlisted = true,
                    parserVersion = existingSource?.parserVersion,
                )
                accountRepository.linkAccountSource(accountId, cleanedPkg, accountHint)
                backupPort.refreshAllowlistCache()
            }.onSuccess {
                actionMessage.value = UiText.Resource(R.string.msg_source_linked, cleanedPkg)
            }.onFailure { err ->
                actionMessage.value = UiText.fromError(err, R.string.msg_source_link_failed)
            }
        }
    }

    fun unlinkSource(linkId: Long) {
        viewModelScope.launch {
            accountRepository.unlinkAccountSource(linkId)
            actionMessage.value = UiText.Resource(R.string.msg_source_unlinked)
        }
    }

    fun toggleAllowlist(packageName: String, allowlisted: Boolean) {
        viewModelScope.launch {
            sourceRepository.setAllowlisted(packageName, allowlisted)
            backupPort.refreshAllowlistCache()
            actionMessage.value = if (allowlisted) {
                UiText.Resource(R.string.msg_source_allowlisted, packageName)
            } else {
                UiText.Resource(R.string.msg_source_removed_allowlist, packageName)
            }
        }
    }

    fun toggleSampleCollection(sourceId: String, enable14Days: Boolean) {
        viewModelScope.launch {
            val until = if (enable14Days) clock.nowEpochMillis() + 14L * 86_400_000L else null
            sourceRepository.setSampleCollectionUntil(sourceId, until)
            actionMessage.value = if (enable14Days) {
                UiText.Resource(R.string.msg_sample_collection_enabled)
            } else {
                UiText.Resource(R.string.msg_sample_collection_disabled)
            }
        }
    }

    fun correctBalance(accountId: Long, currency: String, actualMajorInput: String, note: String?) {
        viewModelScope.launch {
            val minor = MoneyFormatter.parseMajorInput(actualMajorInput, currency) ?: run {
                actionMessage.value = UiText.Resource(R.string.msg_invalid_balance_input, currency)
                return@launch
            }
            runCatching {
                correctBalanceUseCase(accountId, minor, note)
            }.onSuccess { result ->
                actionMessage.value = if (result.adjustmentTransactionId == null) {
                    UiText.Resource(R.string.msg_balance_matches)
                } else {
                    UiText.Resource(
                        R.string.msg_adjustment_recorded,
                        MoneyFormatter.format(result.deltaMinor, currency, signed = true),
                    )
                }
            }.onFailure { err ->
                actionMessage.value = UiText.fromError(err, R.string.msg_adjustment_failed)
            }
        }
    }

    fun clearMessage() {
        actionMessage.value = null
    }

    private data class AccountDataSnapshot(
        val accounts: List<Account>,
        val accountSources: List<AccountSource>,
        val sources: List<TransactionSource>,
        val transactions: List<Transaction>,
        val customRuleCountsBySource: Map<String, Int>,
    )
}
