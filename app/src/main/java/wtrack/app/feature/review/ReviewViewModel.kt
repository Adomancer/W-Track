package wtrack.app.feature.review

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
import wtrack.app.domain.model.Category
import wtrack.app.domain.model.CustomNumberLocale
import wtrack.app.domain.model.DomainTransactionType
import wtrack.app.domain.model.MoneyFormatter
import wtrack.app.domain.model.NeedsReviewItem
import wtrack.app.domain.repository.AccountRepository
import wtrack.app.domain.repository.CategoryRepository
import wtrack.app.domain.repository.CustomParserRuleRepository
import wtrack.app.domain.repository.GeneratedParserTemplate
import wtrack.app.domain.repository.NeedsReviewRepository
import wtrack.app.domain.repository.SaveReviewItemRequest
import wtrack.app.domain.repository.SettingsRepository
import wtrack.app.domain.repository.UpsertCustomParserRuleRequest
import wtrack.app.domain.time.Clock
import wtrack.app.domain.usecase.ResolveNeedsReviewUseCase
import wtrack.integration.Direction

data class ReviewUiState(
    val items: List<NeedsReviewItem> = emptyList(),
    val accounts: List<Account> = emptyList(),
    val categories: List<Category> = emptyList(),
    val defaultCurrency: String = "IDR",
    val actionMessage: String? = null,
)

@HiltViewModel
class ReviewViewModel @Inject constructor(
    needsReviewRepository: NeedsReviewRepository,
    accountRepository: AccountRepository,
    categoryRepository: CategoryRepository,
    settingsRepository: SettingsRepository,
    private val customParserRuleRepository: CustomParserRuleRepository,
    private val resolveNeedsReviewUseCase: ResolveNeedsReviewUseCase,
    private val clock: Clock,
) : ViewModel() {

    private val actionMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<ReviewUiState> = combine(
        needsReviewRepository.items,
        accountRepository.accounts,
        categoryRepository.categories,
        settingsRepository.settings,
        actionMessage,
    ) { items, accounts, categories, settings, message ->
        ReviewUiState(
            items = items,
            accounts = accounts.filter { it.isActive },
            categories = categories.filter { !it.isArchived },
            defaultCurrency = settings.baseCurrency,
            actionMessage = message,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = ReviewUiState(),
    )

    fun generatePatternSuggestion(
        item: NeedsReviewItem,
        descriptionHint: String?,
        currency: String,
    ): GeneratedParserTemplate? {
        val sampleBody = item.payloadBody
            ?: item.payloadText?.let { text ->
                if (!item.payloadTitle.isNullOrBlank() && text.startsWith(item.payloadTitle)) {
                    text.removePrefix(item.payloadTitle).trim()
                } else {
                    text
                }
            }
            ?: return null
        return customParserRuleRepository.generateTemplateFromSample(
            sampleBody = sampleBody,
            merchantOrDescHint = item.draftMerchantRaw ?: descriptionHint,
            defaultCurrency = currency.trim().uppercase().ifEmpty { uiState.value.defaultCurrency },
        )
    }

    fun saveResolved(
        item: NeedsReviewItem,
        accountId: Long,
        amountMajorInput: String,
        currency: String,
        direction: Direction,
        type: DomainTransactionType,
        description: String?,
        categoryId: Long?,
        rememberAccountHint: Boolean,
        rememberPattern: Boolean = false,
        customTemplatePattern: String? = null,
        customNumberLocale: CustomNumberLocale = CustomNumberLocale.INDONESIAN,
    ) {
        viewModelScope.launch {
            val normalizedCurrency = currency.trim().uppercase().ifEmpty { uiState.value.defaultCurrency }
            val minor = MoneyFormatter.parseMajorInput(amountMajorInput, normalizedCurrency)
                ?.takeIf { it > 0L }
                ?: run {
                    actionMessage.value = "Enter a valid positive amount for $normalizedCurrency."
                    return@launch
                }
            runCatching {
                resolveNeedsReviewUseCase.save(
                    itemId = item.id,
                    resolved = SaveReviewItemRequest(
                        accountId = accountId,
                        amountMinor = minor,
                        currency = normalizedCurrency,
                        direction = direction,
                        transactionType = type,
                        occurredAt = item.draftOccurredAt ?: item.postedAt.takeIf { it > 0L } ?: clock.nowEpochMillis(),
                        description = description?.trim()?.takeIf { it.isNotEmpty() },
                        categoryId = categoryId,
                        rememberAccountHint = rememberAccountHint,
                    ),
                )
            }.onSuccess {
                if (rememberPattern && !customTemplatePattern.isNullOrBlank()) {
                    val ruleOutcome = runCatching {
                        val ruleName = "${item.packageName.substringAfterLast('.')} ${direction.name.lowercase()}"
                        customParserRuleRepository.upsertRule(
                            UpsertCustomParserRuleRequest(
                                sourceId = item.sourceId,
                                name = ruleName,
                                titleContains = null,
                                templatePattern = customTemplatePattern,
                                direction = direction,
                                transactionType = type,
                                currency = normalizedCurrency,
                                numberLocale = customNumberLocale,
                                accountHint = item.draftAccountHint?.takeIf { rememberAccountHint },
                                enabled = true,
                            ),
                        )
                        customParserRuleRepository.retryPendingReviewForSource(item.sourceId)
                    }
                    ruleOutcome.onSuccess { autoParsedOthers ->
                        actionMessage.value = buildString {
                            append("Transaction saved & pattern remembered.")
                            if (autoParsedOthers > 0) {
                                append(" Auto-parsed $autoParsedOthers more pending notification(s).")
                            }
                        }
                    }.onFailure { err ->
                        actionMessage.value = "Transaction saved, but pattern was not saved: ${err.message}"
                    }
                } else {
                    actionMessage.value = "Transaction confirmed and saved."
                }
            }.onFailure { err ->
                actionMessage.value = err.message ?: "Failed to save transaction."
            }
        }
    }

    fun ignore(itemId: Long) {
        viewModelScope.launch {
            runCatching { resolveNeedsReviewUseCase.ignore(itemId) }
                .onSuccess { actionMessage.value = "Event ignored and payload purged." }
                .onFailure { actionMessage.value = it.message ?: "Ignore failed." }
        }
    }

    fun retry(itemId: Long) {
        viewModelScope.launch {
            runCatching { resolveNeedsReviewUseCase.retry(itemId) }
                .onSuccess { actionMessage.value = "Re-run completed." }
                .onFailure { actionMessage.value = it.message ?: "Retry failed." }
        }
    }

    fun clearMessage() {
        actionMessage.value = null
    }
}
