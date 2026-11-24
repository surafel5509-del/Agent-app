package com.nexa.ai.presentation.about

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexa.ai.core.result.AppResult
import com.nexa.ai.domain.model.ConnectionStatus
import com.nexa.ai.domain.repository.HealthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AboutViewModel @Inject constructor(
    private val healthRepository: HealthRepository,
) : ViewModel() {

    private val _health = MutableStateFlow<ConnectionStatus>(ConnectionStatus.Idle)
    val health: StateFlow<ConnectionStatus> = _health.asStateFlow()

    init {
        checkServer()
    }

    fun checkServer() {
        viewModelScope.launch {
            _health.value = ConnectionStatus.Checking
            _health.value = when (val result = healthRepository.check()) {
                is AppResult.Success -> ConnectionStatus.Online(result.value)
                is AppResult.Failure -> ConnectionStatus.Failed(result.error)
            }
        }
    }
}
