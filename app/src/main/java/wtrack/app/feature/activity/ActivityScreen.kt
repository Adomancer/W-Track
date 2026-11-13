package wtrack.app.feature.activity

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import wtrack.app.feature.PendingScreenPlaceholder

/**
 * Activity screen (PRD section 10.3): chronological transaction history with search and
 * filters. Wiring follows once the [TransactionRepository] contract is available.
 */
@Composable
fun ActivityScreen(modifier: Modifier = Modifier) {
    PendingScreenPlaceholder(modifier = modifier)
}