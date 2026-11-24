package com.naira.assistant.core.tools.executor

import com.naira.assistant.core.database.todo.TodoItem
import com.naira.assistant.core.database.todo.TodoPriority
import com.naira.assistant.core.database.todo.TodoRepository
import com.naira.assistant.domain.FunctionCall
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertNull
import org.junit.Test

class TodoToolExecutorTest {

    @Test
    fun `all list filters are passed to repository as no filter`() = runBlocking {
        val repo = RecordingTodoRepository()
        val call = FunctionCall(
            name = "list_todos",
            args = buildJsonObject {
                put("done", "all")
                put("priority", "all")
                put("list_name", "all")
            },
        )

        TodoToolExecutor.executeListTodos(call, repo)

        assertNull(repo.doneFilter)
        assertNull(repo.priorityFilter)
        assertNull(repo.listFilter)
    }

    private class RecordingTodoRepository : TodoRepository {
        var doneFilter: Boolean? = false
        var priorityFilter: String? = "not-called"
        var listFilter: String? = "not-called"

        override suspend fun insert(
            title: String,
            notes: String,
            dueAtMs: Long,
            priority: TodoPriority,
            tags: List<String>,
            listName: String,
            source: String,
        ): TodoItem = error("Not used by this test.")

        override suspend fun update(
            id: Long,
            title: String?,
            notes: String?,
            done: Boolean?,
            dueAtMs: Long?,
            priority: TodoPriority?,
            tags: List<String>?,
            listName: String?,
        ): TodoItem? = error("Not used by this test.")

        override suspend fun delete(id: Long): Boolean = error("Not used by this test.")
        override suspend fun markDone(id: Long): TodoItem? = error("Not used by this test.")
        override suspend fun markUndone(id: Long): TodoItem? = error("Not used by this test.")
        override suspend fun getById(id: Long): TodoItem? = error("Not used by this test.")

        override suspend fun getAll(
            done: Boolean?,
            priority: String?,
            listName: String?,
            limit: Int,
        ): List<TodoItem> {
            doneFilter = done
            priorityFilter = priority
            listFilter = listName
            return emptyList()
        }

        override suspend fun search(
            query: String?,
            listName: String?,
            limit: Int,
        ): List<TodoItem> = error("Not used by this test.")

        override suspend fun count(): Long = 0L
        override suspend fun clearDone(): Int = error("Not used by this test.")
        override suspend fun clearAll(): Int = error("Not used by this test.")
        override fun observeAll() = MutableStateFlow(emptyList<TodoItem>())
    }
}
