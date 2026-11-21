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
import wtrack.app.domain.model.Account
import wtrack.app.domain.model.AccountSource
import wtrack.app.domain.model.AccountType
import wtrack.app.domain.model.MoneyFormatter
import wtrack.app.domain.model.Transaction
import wtrack.app.domain.model.TransactionSource
import wtrack.app.domain.repository.AccountRepository
import wtrack.app.domain.repository.BackupAndDiagnosticsPort
import wtrack.app.domain.repository.CreateAccountRequest
import wtrack.app.domain.repository.ProviderCatalogEntry
import wtrack.app.domain.repository.SourceAndEventRepository
import wtrack.app.domain.repository.TransactionRepository
import wtrack.app.domain.repository.UpdateAccountRequest
import wtrack.app.domain.time.Clock
import wtrack.app.domain.usecase.BalanceCheckpointEvaluation
import wtrack.app.domain.usecase.BalanceCheckpointEvaluator
import wtrack.app.domain.usecase.CorrectBalanceUseCase

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
    val nowEpochMillis: Long = 0L,
    val actionMessage: String? = null,
)

@HiltViewModel
class AccountsViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
    private val sourceRepository: SourceAndEventRepository,
    transactionRepository: TransactionRepository,
    private val backupPort: BackupAndDiagnosticsPort,
    private val checkpointEvaluator: BalanceCheckpointEvaluator,
    private val correctBalanceUseCase: CorrectBalanceUseCase,
    private val clock: Clock,
) : ViewModel() {

    private val actionMessage = MutableStateFlow<String?>(null)

    private val repoSnapshot = combine(
        accountRepository.accounts,
        accountRepository.accountSources,
        sourceRepository.sources,
        transactionRepository.transactions,
    ) { accounts, accountSources, sources, transactions ->
        AccountDataSnapshot(accounts, accountSources, sources, transactions)
    }

    val uiState: StateFlow<AccountsUiState> = combine(
        repoSnapshot,
        actionMessage,
    ) { snap, message ->
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
            val normalizedCurrency = currency.trim().uppercase().ifEmpty { "IDR" }
            val openingMinor = if (openingBalanceInput.isBlank()) {
                null
            } else {
                MoneyFormatter.parseMajorInput(openingBalanceInput, normalizedCurrency) ?: run {
                    actionMessage.value = "Invalid opening balance format for $normalizedCurrency."
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
                actionMessage.value = "Account created."
            }.onFailure { err ->
                actionMessage.value = err.message ?: "Failed to create account."
            }
        }
    }

    fun updateAccount(id: Long, name: String, provider: String, type: AccountType, currency: String) {
        viewModelScope.launch {
            runCatching {
                accountRepository.update(UpdateAccountRequest(id, name, provider, type, currency))
            }.onSuccess {
                actionMessage.value = "Account updated."
            }.onFailure {
                actionMessage.value = it.message ?: "Failed to update account."
            }
        }
    }

    fun archiveAccount(id: Long) {
        viewModelScope.launch {
            accountRepository.archive(id)
            actionMessage.value = "Account archived."
        }
    }

    fun restoreAccount(id: Long) {
        viewModelScope.launch {
            accountRepository.restore(id)
            actionMessage.value = "Account restored."
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
                actionMessage.value = "Enter an Android package ID."
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
                actionMessage.value = "Source $cleanedPkg allowlisted and linked."
            }.onFailure { err ->
                actionMessage.value = err.message ?: "Could not link source (hint may already belong to another account)."
            }
        }
    }

    fun unlinkSource(linkId: Long) {
        viewModelScope.launch {
            accountRepository.unlinkAccountSource(linkId)
            actionMessage.value = "Source link removed."
        }
    }

    fun toggleAllowlist(packageName: String, allowlisted: Boolean) {
        viewModelScope.launch {
            sourceRepository.setAllowlisted(packageName, allowlisted)
            backupPort.refreshAllowlistCache()
            actionMessage.value = if (allowlisted) "$packageName allowlisted." else "$packageName removed from allowlist."
        }
    }

    fun toggleSampleCollection(sourceId: String, enable14Days: Boolean) {
        viewModelScope.launch {
            val until = if (enable14Days) clock.nowEpochMillis() + 14L * 86_400_000L else null
            sourceRepository.setSampleCollectionUntil(sourceId, until)
            actionMessage.value = if (enable14Days) {
                "Sample collection enabled for 14 days (stored encrypted locally)."
            } else {
                "Sample collection disabled."
            }
        }
    }

    fun correctBalance(accountId: Long, currency: String, actualMajorInput: String, note: String?) {
        viewModelScope.launch {
            val minor = MoneyFormatter.parseMajorInput(actualMajorInput, currency) ?: run {
                actionMessage.value = "Invalid balance input for $currency."
                return@launch
            }
            runCatching {
                correctBalanceUseCase(accountId, minor, note)
            }.onSuccess { result ->
                actionMessage.value = if (result.adjustmentTransactionId == null) {
                    "Balance matches estimate (no adjustment required)."
                } else {
                    "Recorded adjustment ${MoneyFormatter.format(result.deltaMinor, currency, signed = true)}."
                }
            }.onFailure { err ->
                actionMessage.value = err.message ?: "Failed to record balance adjustment."
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
    )
}
