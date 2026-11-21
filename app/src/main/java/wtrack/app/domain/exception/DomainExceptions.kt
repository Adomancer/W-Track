package wtrack.app.domain.exception

sealed class DomainException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

class DatabaseLockedException(message: String, cause: Throwable? = null) : DomainException(message, cause)

class ValidationException(message: String) : DomainException(message)

class NotFoundException(message: String) : DomainException(message)

class InvalidTransferPairException(message: String) : DomainException(message)

class TransferPairEditBlockedException(message: String) : DomainException(message)

class BackupRestoreException(message: String, cause: Throwable? = null) : DomainException(message, cause)
