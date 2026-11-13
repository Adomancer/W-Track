package wtrack.app.feature.review

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import wtrack.app.feature.PendingScreenPlaceholder

/**
 * Needs Review screen (PRD section 10.3): the queue of failed or uncertain transaction events.
 * Wiring follows once the [NeedsReviewRepository] contract is available in domain.
 */
@Composable
fun ReviewScreen(modifier: Modifier = Modifier) {
    PendingScreenPlaceholder(modifier = modifier)
}