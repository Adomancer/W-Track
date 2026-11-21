package wtrack.app.domain.usecase

import wtrack.app.domain.repository.BackupAndDiagnosticsPort

class WipeAllDataUseCase(
    private val backupAndDiagnosticsPort: BackupAndDiagnosticsPort,
) {
    suspend operator fun invoke() {
        backupAndDiagnosticsPort.wipeAllDataAndReset()
    }
}
