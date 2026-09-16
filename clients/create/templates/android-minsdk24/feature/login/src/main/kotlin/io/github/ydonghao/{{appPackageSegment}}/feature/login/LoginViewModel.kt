package io.github.ydonghao.{{appPackageSegment}}.feature.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 每屏一个 ViewModel = 唯一状态容器（contract/clients/android.md 二-2）：
 * UiState 单一可信状态源 + 一次性事件通道；UI 只发事件不改状态（UDF）。
 */
@HiltViewModel
class LoginViewModel @Inject constructor(
    private val repository: LoginRepository
) : ViewModel() {

    data class UiState(
        val username: String = "",
        val password: String = "",
        val loading: Boolean = false,
        val errorMessage: String? = null
    )

    sealed interface LoginEvent {
        data object LoggedIn : LoginEvent
    }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _events = Channel<LoginEvent>(Channel.BUFFERED)
    val events: Flow<LoginEvent> = _events.receiveAsFlow()

    fun onUsernameChange(value: String) {
        _state.update { it.copy(username = value, errorMessage = null) }
    }

    fun onPasswordChange(value: String) {
        _state.update { it.copy(password = value, errorMessage = null) }
    }

    fun submit() {
        if (_state.value.loading) {
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(loading = true, errorMessage = null) }
            when (val result = repository.login(_state.value.username, _state.value.password)) {
                is LoginResult.Success -> {
                    _state.update { it.copy(loading = false) }
                    _events.send(LoginEvent.LoggedIn)
                }
                is LoginResult.Failure -> {
                    _state.update { it.copy(loading = false, errorMessage = result.message) }
                }
            }
        }
    }
}
