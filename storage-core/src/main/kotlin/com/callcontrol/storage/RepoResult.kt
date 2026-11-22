package com.callcontrol.storage

import com.callcontrol.domain.InvalidReason

enum class ScheduleProblem {
    NO_DAYS,
    EMPTY_INTERVAL,

    /** Times are stored to the second; sub-second values would be silently lost, so they are rejected. */
    UNSUPPORTED_TIME_PRECISION,
}

/** Why an operation failed. Never contains a phone number or other personal data. */
sealed interface RepoError {
    data class InvalidNumber(val reason: InvalidReason) : RepoError
    data object DuplicateNumber : RepoError
    data object NotFound : RepoError
    data class InvalidSchedule(val problem: ScheduleProblem) : RepoError
    data object InvalidOrder : RepoError

    /** The storage layer threw. Only the exception class name is kept (its message may contain data). */
    data class Storage(val causeType: String) : RepoError
}

sealed interface RepoResult<out T> {
    data class Success<out T>(val value: T) : RepoResult<T>
    data class Failure(val error: RepoError) : RepoResult<Nothing>

    fun getOrNull(): T? = (this as? Success)?.value
}

class RepositoryException(val error: RepoError) : Exception("Repository failure: $error")

/** For callers that want exceptions (e.g. inside `RuleEngine.decideSafely`, which fails open). */
fun <T> RepoResult<T>.getOrThrow(): T = when (this) {
    is RepoResult.Success -> value
    is RepoResult.Failure -> throw RepositoryException(error)
}
