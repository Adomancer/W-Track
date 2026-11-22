package wtrack.app.feature.settings

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
import wtrack.app.domain.model.CustomNumberLocale
import wtrack.app.domain.model.CustomParserRule
import wtrack.app.domain.model.DomainTransactionType
import wtrack.app.domain.model.TransactionSource
import wtrack.app.domain.repository.CustomParserRuleRepository
import wtrack.app.domain.repository.CustomParserTestOutcome
import wtrack.app.domain.repository.GeneratedParserTemplate
import wtrack.app.domain.repository.SettingsRepository
import wtrack.app.domain.repository.SourceAndEventRepository
import wtrack.app.domain.repository.UpsertCustomParserRuleRequest
import wtrack.integration.Direction

data class CustomParsersUiState(
    val rules: List<CustomParserRule> = emptyList(),
    val sources: List<TransactionSource> = emptyList(),
    val defaultCurrency: String = "IDR",
    val actionMessage: String? = null,
)

@HiltViewModel
class CustomParsersViewModel @Inject constructor(
    private val customParserRuleRepository: CustomParserRuleRepository,
    sourceRepository: SourceAndEventRepository,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    private val actionMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<CustomParsersUiState> = combine(
        customParserRuleRepository.rules,
        sourceRepository.sources,
        settingsRepository.settings,
        actionMessage,
    ) { rules, sources, settings, message ->
        CustomParsersUiState(
            rules = rules,
            sources = sources,
            defaultCurrency = settings.baseCurrency,
            actionMessage = message,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = CustomParsersUiState(),
    )

    fun generateFromSample(
        sampleBody: String,
        hint: String? = null,
        currency: String = uiState.value.defaultCurrency,
    ): GeneratedParserTemplate? = customParserRuleRepository.generateTemplateFromSample(
        sampleBody = sampleBody,
        merchantOrDescHint = hint,
        defaultCurrency = currency,
    )

    fun testRule(
        templatePattern: String,
        titleContains: String?,
        direction: Direction,
        transactionType: DomainTransactionType,
        currency: String,
        numberLocale: CustomNumberLocale,
        sampleTitle: String?,
        sampleBody: String,
    ): CustomParserTestOutcome = customParserRuleRepository.testRuleAgainstSample(
        templatePattern = templatePattern,
        titleContains = titleContains,
        direction = direction,
        transactionType = transactionType,
        currency = currency,
        numberLocale = numberLocale,
        sampleTitle = sampleTitle,
        sampleBody = sampleBody,
    )

    fun saveRule(
        id: Long?,
        sourceId: String,
        name: String,
        titleContains: String?,
        templatePattern: String,
        direction: Direction,
        transactionType: DomainTransactionType,
        currency: String,
        numberLocale: CustomNumberLocale,
        accountHint: String?,
        enabled: Boolean = true,
    ) {
        viewModelScope.launch {
            runCatching {
                val cleanedSource = sourceId.trim()
                customParserRuleRepository.upsertRule(
                    UpsertCustomParserRuleRequest(
                        id = id,
                        sourceId = cleanedSource,
                        name = name,
                        titleContains = titleContains,
                        templatePattern = templatePattern,
                        direction = direction,
                        transactionType = transactionType,
                        currency = currency,
                        numberLocale = numberLocale,
                        accountHint = accountHint,
                        enabled = enabled,
                    ),
                )
                customParserRuleRepository.retryPendingReviewForSource(cleanedSource)
            }.onSuccess { autoParsed ->
                actionMessage.value = buildString {
                    append(if (id == null) "Custom parser pattern saved." else "Custom parser pattern updated.")
                    if (autoParsed > 0) {
                        append(" Auto-parsed $autoParsed pending notification(s).")
                    }
                }
            }.onFailure { err ->
                actionMessage.value = err.message ?: "Failed to save custom parser pattern."
            }
        }
    }

    fun setEnabled(id: Long, enabled: Boolean) {
        viewModelScope.launch {
            customParserRuleRepository.setEnabled(id, enabled)
        }
    }

    fun deleteRule(id: Long) {
        viewModelScope.launch {
            customParserRuleRepository.delete(id)
            actionMessage.value = "Custom parser pattern deleted."
        }
    }

    fun clearMessage() {
        actionMessage.value = null
    }
}
