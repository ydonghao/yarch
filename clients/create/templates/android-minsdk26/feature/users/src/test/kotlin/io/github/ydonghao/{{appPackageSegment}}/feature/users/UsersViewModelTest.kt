package io.github.ydonghao.{{appPackageSegment}}.feature.users

import io.github.ydonghao.yarch.client.error.ApiError
import io.github.ydonghao.yarch.client.error.NetworkError
import io.github.ydonghao.{{appPackageSegment}}.core.network.api.UserDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UsersViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private class FakeUsersRepository(
        private val block: suspend () -> List<UserDto>
    ) : UsersRepository {
        override suspend fun users(page: Int): List<UserDto> = block()
    }

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `成功态加载用户列表`() = runTest(dispatcher) {
        val vm = UsersViewModel(
            FakeUsersRepository { listOf(UserDto(id = "u1", name = "甲")) }
        )
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, vm.state.value.users.size)
        assertEquals("u1", vm.state.value.users.first().id)
        assertNull(vm.state.value.errorMessage)
    }

    @Test
    fun `业务错误透出 message 且清 loading`() = runTest(dispatcher) {
        val vm = UsersViewModel(
            FakeUsersRepository {
                throw ApiError(code = 1001, message = "参数校验失败", traceId = "t", httpStatus = 400)
            }
        )
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(vm.state.value.users.isEmpty())
        assertEquals("参数校验失败", vm.state.value.errorMessage)
        assertEquals(false, vm.state.value.loading)
    }

    @Test
    fun `传输错误统一网络文案`() = runTest(dispatcher) {
        val vm = UsersViewModel(
            FakeUsersRepository { throw NetworkError() }
        )
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals("网络异常，请稍后重试", vm.state.value.errorMessage)
    }
}
