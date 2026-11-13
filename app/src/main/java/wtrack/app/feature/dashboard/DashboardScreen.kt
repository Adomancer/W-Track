package wtrack.app.feature.dashboard

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import wtrack.app.feature.PendingScreenPlaceholder

/**
 * Dashboard screen (PRD section 10.3). Displays income, expenses, net cash flow,
 * account overview, recent transactions, pending review count)Skip this until the
 * [ObserveDashboardUseCase] contract is available in domain. This placeholder never
 * shows fabricated financial data.
 */
@Composable
fun DashboardScreen(modifier: Modifier = Modifier) {
    PendingScreenPlaceholder(modifier = modifier)
}