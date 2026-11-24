package com.nexa.ai.presentation.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexa.ai.core.AppConstants
import com.nexa.ai.core.error.AppError
import com.nexa.ai.core.network.BaseUrlHolder
import com.nexa.ai.core.network.BaseUrlNormalizer
import com.nexa.ai.domain.repository.SettingsRepository
import com.nexa.ai.domain.usecase.LoginOutcome
import com.nexa.ai.domain.usecase.LoginUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Why a sign-in attempt was refused, before or after hitting the network. */
sealed interface LoginError {
    data object EmptyUsername : LoginError
    data object EmptyPassword : LoginError
    data object InvalidUrl : LoginError
    data object HttpsRequired : LoginError
    data class RequestFailed(val error: AppError) : LoginError
}

data class LoginUiState(
    val apiUrl: String = AppConstants.DEFAULT_API_URL,
    val username: String = "",
    val password: String = "",
    val isPasswordVisible: Boolean = false,
    val isSubmitting: Boolean = false,
    val error: LoginError? = null,
    /** Non-fatal: login succeeded but the health probe failed. */
    val warning: AppError? = null,
) {
    val canSubmit: Boolean
        get() = !isSubmitting && username.isNotBlank() && password.isNotBlank() && apiUrl.isNotBlank()
}

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val loginUseCase: LoginUseCase,
    private val settingsRepository: SettingsRepository,
    private val baseUrlHolder: BaseUrlHolder,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    init {
        // Prefill the last used gateway URL and username. The password is never stored.
        viewModelScope.launch {
            val preferences = settingsRepository.preferences.first()
            _uiState.update {
                it.copy(
                    apiUrl = preferences.apiUrl,
                    username = preferences.username.orEmpty(),
                )
            }
        }
    }

    fun onApiUrlChanged(value: String) = _uiState.update {
        it.copy(apiUrl = value, error = null, warning = null)
    }

    fun onUsernameChanged(value: String) = _uiState.update {
        it.copy(username = value, error = null)
    }

    fun onPasswordChanged(value: String) = _uiState.update {
        it.copy(password = value, error = null)
    }

    fun onPasswordVisibilityToggled() = _uiState.update {
        it.copy(isPasswordVisible = !it.isPasswordVisible)
    }

    fun consumeError() = _uiState.update { it.copy(error = null) }

    fun consumeWarning() = _uiState.update { it.copy(warning = null) }

    fun submit() {
        val current = _uiState.value
        if (current.isSubmitting) return

        val validation = BaseUrlNormalizer.validate(current.apiUrl)
        if (validation is BaseUrlNormalizer.Validation.Invalid) {
            _uiState.update {
                it.copy(
                    error = if (validation.reason == BaseUrlNormalizer.Reason.HTTPS_REQUIRED) {
                        LoginError.HttpsRequired
                    } else {
                        LoginError.InvalidUrl
                    },
                )
            }
            return
        }
        if (current.username.isBlank()) {
            _uiState.update { it.copy(error = LoginError.EmptyUsername) }
            return
        }
        if (current.password.isBlank()) {
            _uiState.update { it.copy(error = LoginError.EmptyPassword) }
            return
        }

        val normalizedUrl = (validation as BaseUrlNormalizer.Validation.Valid).url
        baseUrlHolder.update(normalizedUrl)

        _uiState.update { it.copy(isSubmitting = true, error = null, warning = null) }

        viewModelScope.launch {
            when (val outcome = loginUseCase(current.username, current.password, current.apiUrl)) {
                is LoginOutcome.Success -> _uiState.update {
                    it.copy(isSubmitting = false, password = "", warning = outcome.warning)
                }
                is LoginOutcome.Failed -> _uiState.update {
                    it.copy(isSubmitting = false, error = LoginError.RequestFailed(outcome.error))
                }
            }
        }
    }
}
