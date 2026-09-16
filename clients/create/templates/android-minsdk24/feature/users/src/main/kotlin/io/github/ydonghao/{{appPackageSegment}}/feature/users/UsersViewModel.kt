package io.github.ydonghao.{{appPackageSegment}}.feature.users

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.ydonghao.yarch.client.error.ApiError
import io.github.ydonghao.yarch.client.error.NetworkError
import io.github.ydonghao.{{appPackageSegment}}.core.network.api.UserDto
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 状态下降 / 事件上行（UDF）：传输错误统一网络文案（client-shared 一-4），
 * 业务错误透出 message；取消（CancellationException）原生传导不捕获。
 */
@HiltViewModel
class UsersViewModel @Inject constructor(
    private val repository: UsersRepository
) : ViewModel() {

    data class UiState(
        val loading: Boolean = false,
        val users: List<UserDto> = emptyList(),
        val errorMessage: String? = null
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        if (_state.value.loading) {
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(loading = true, errorMessage = null) }
            try {
                val users = repository.users(1)
                _state.update { it.copy(loading = false, users = users) }
            } catch (error: ApiError) {
                _state.update { it.copy(loading = false, errorMessage = error.message) }
            } catch (_: NetworkError) {
                _state.update { it.copy(loading = false, errorMessage = "网络异常，请稍后重试") }
            }
        }
    }
}
