package com.nexa.ai.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.nexa.ai.R
import com.nexa.ai.core.error.AppError
import com.nexa.ai.core.error.ErrorKind

/**
 * Human-readable mapping of [AppError] to UI copy.
 *
 * Kept in presentation (not in the error type itself) so the domain layer stays free of
 * Android resources and remains unit-testable.
 */
@Composable
fun errorTitle(error: AppError): String = when (error.kind) {
    ErrorKind.OFFLINE -> stringResource(R.string.error_offline_title)
    ErrorKind.TIMEOUT -> stringResource(R.string.error_timeout_title)
    ErrorKind.UNAUTHORIZED -> stringResource(R.string.error_unauthorized_title)
    ErrorKind.FORBIDDEN -> stringResource(R.string.error_forbidden_title)
    ErrorKind.RATE_LIMITED -> stringResource(R.string.error_rate_limited_title)
    ErrorKind.SERVER -> stringResource(R.string.error_server_title)
    ErrorKind.CLIENT -> stringResource(R.string.error_client_title)
    ErrorKind.VALIDATION -> stringResource(R.string.error_validation_title)
    ErrorKind.EMPTY_RESPONSE -> stringResource(R.string.error_empty_title)
    ErrorKind.UNKNOWN -> stringResource(R.string.error_unknown_title)
}

@Composable
fun errorMessage(error: AppError): String = when (error.kind) {
    ErrorKind.OFFLINE -> stringResource(R.string.error_offline_body)
    ErrorKind.TIMEOUT -> stringResource(R.string.error_timeout_body)
    ErrorKind.UNAUTHORIZED -> stringResource(R.string.error_unauthorized_body)
    ErrorKind.FORBIDDEN -> stringResource(R.string.error_forbidden_body)
    ErrorKind.RATE_LIMITED -> stringResource(R.string.error_rate_limited_body)
    ErrorKind.SERVER -> stringResource(R.string.error_server_body)
    ErrorKind.CLIENT -> stringResource(R.string.error_client_body)
    ErrorKind.VALIDATION -> error.detail ?: stringResource(R.string.error_validation_body)
    ErrorKind.EMPTY_RESPONSE -> stringResource(R.string.error_empty_body)
    ErrorKind.UNKNOWN -> stringResource(R.string.error_unknown_body)
}

/** Optional second line with the HTTP status, so technical users can report precise issues. */
@Composable
fun errorDetail(error: AppError): String? {
    val code = error.httpCode
    val detail = error.detail?.takeIf { it.isNotBlank() }
    return when {
        code != null && detail != null -> "HTTP $code · ${detail.take(160)}"
        code != null -> "HTTP $code"
        detail != null -> detail.take(160)
        else -> null
    }
}
