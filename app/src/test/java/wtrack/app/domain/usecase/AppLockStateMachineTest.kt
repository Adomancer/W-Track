package wtrack.app.domain.usecase

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import wtrack.app.domain.model.AppSettings

class AppLockStateMachineTest {

    @Test
    fun coldStartWithAppLockEnabledStartsLockedUntilUnlocked() {
        val machine = AppLockStateMachine()
        val settings = AppSettings(appLockEnabled = true, appLockTimeoutSeconds = 60)

        assertThat(machine.onSettingsObserved(settings)).isTrue()
        assertThat(machine.isLocked).isTrue()

        assertThat(machine.onUnlocked()).isFalse()
        assertThat(machine.isLocked).isFalse()
    }

    @Test
    fun togglingLockOnAfterStartupDoesNotInterruptActiveSessionUntilTimeoutElapses() {
        val machine = AppLockStateMachine()
        assertThat(machine.onSettingsObserved(AppSettings(appLockEnabled = false))).isFalse()

        val enabled = AppSettings(appLockEnabled = true, appLockTimeoutSeconds = 60)
        assertThat(machine.onSettingsObserved(enabled)).isFalse()

        // Short background (30s < 60s) remains unlocked.
        assertThat(machine.onAppBackgrounded(10_000L, enabled)).isFalse()
        assertThat(machine.onAppForegrounded(40_000L, enabled)).isFalse()

        // Background exceeding timeout (60s >= 60s) locks on foreground.
        assertThat(machine.onAppBackgrounded(50_000L, enabled)).isFalse()
        assertThat(machine.onAppForegrounded(110_000L, enabled)).isTrue()

        // Turning off lock clears lock immediately.
        assertThat(machine.onSettingsObserved(AppSettings(appLockEnabled = false))).isFalse()
        assertThat(machine.isLocked).isFalse()
    }

    @Test
    fun immediateTimeoutLocksOnBackgroundImmediately() {
        val machine = AppLockStateMachine()
        val immediate = AppSettings(appLockEnabled = true, appLockTimeoutSeconds = 0)
        assertThat(machine.onSettingsObserved(immediate)).isTrue()
        machine.onUnlocked()

        assertThat(machine.onAppBackgrounded(1_000L, immediate)).isTrue()
        assertThat(machine.onAppForegrounded(1_100L, immediate)).isTrue()
    }
}
