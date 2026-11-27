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
import wtrack.app.R
import wtrack.app.domain.model.CurrencyRegistry
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
import wtrack.app.feature.common.UiText
import wtrack.integration.Direction

data class CustomParsersUiState(
    val rules: List<CustomParserRule> = emptyList(),
    val sources: List<TransactionSource> = emptyList(),
    val defaultCurrency: String = CurrencyRegistry.DEFAULT_CURRENCY,
    val actionMessage: UiText? = null,
)

@HiltViewModel
class CustomParsersViewModel @Inject constructor(
    private val customParserRuleRepository: CustomParserRuleRepository,
    sourceRepository: SourceAndEventRepository,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    private val actionMessage = MutableStateFlow<UiText?>(null)

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
                actionMessage.value = when {
                    id == null && autoParsed > 0 ->
                        UiText.Resource(R.string.msg_custom_parser_saved_autoparsed, autoParsed)
                    id == null ->
                        UiText.Resource(R.string.msg_custom_parser_saved)
                    autoParsed > 0 ->
                        UiText.Resource(R.string.msg_custom_parser_updated_autoparsed, autoParsed)
                    else ->
                        UiText.Resource(R.string.msg_custom_parser_updated)
                }
            }.onFailure { err ->
                actionMessage.value = UiText.fromError(err, R.string.msg_custom_parser_save_failed)
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
            actionMessage.value = UiText.Resource(R.string.msg_custom_parser_deleted)
        }
    }

    fun clearMessage() {
        actionMessage.value = null
    }
}
