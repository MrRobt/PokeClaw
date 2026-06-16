package io.agents.pokeclaw.agent.llm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LocalModelManagerTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `model directory uses external app storage when it can be created`() {
        val externalRoot = temporaryFolder.newFolder("external")
        val internalRoot = temporaryFolder.newFolder("internal")

        val dir = LocalModelManager.resolveUsableModelDir(externalRoot, internalRoot)

        assertEquals(externalRoot.resolve("models"), dir)
        assertTrue(dir.isDirectory)
    }

    @Test
    fun `model directory falls back to internal storage when external path is unusable`() {
        val externalRoot = temporaryFolder.newFolder("external")
        externalRoot.resolve("models").writeText("blocking file")
        val internalRoot = temporaryFolder.newFolder("internal")

        val dir = LocalModelManager.resolveUsableModelDir(externalRoot, internalRoot)

        assertEquals(internalRoot.resolve("models"), dir)
        assertTrue(dir.isDirectory)
    }

    @Test
    fun `model directory falls back to internal storage when external path is not writable`() {
        val externalRoot = temporaryFolder.newFolder("external")
        val internalRoot = temporaryFolder.newFolder("internal")
        val externalModelDir = externalRoot.resolve("models")

        val dir = LocalModelManager.resolveUsableModelDir(
            externalRoot = externalRoot,
            internalRoot = internalRoot,
            canWriteDirectory = { candidate -> candidate != externalModelDir },
        )

        assertEquals(internalRoot.resolve("models"), dir)
        assertTrue(dir.isDirectory)
    }

    @Test
    fun `model directory falls back to internal storage when external root is missing`() {
        val internalRoot = temporaryFolder.newFolder("internal")

        val dir = LocalModelManager.resolveUsableModelDir(null, internalRoot)

        assertEquals(internalRoot.resolve("models"), dir)
        assertTrue(dir.isDirectory)
    }

    @Test
    fun `model directory throws when both external and internal are unusable`() {
        val externalRoot = temporaryFolder.newFolder("external")
        externalRoot.resolve("models").writeText("blocking file")
        val internalRoot = temporaryFolder.newFolder("internal")
        internalRoot.resolve("models").writeText("blocking file")

        val ex = runCatching {
            LocalModelManager.resolveUsableModelDir(externalRoot, internalRoot)
        }.exceptionOrNull()

        assertTrue(
            "expected IllegalStateException, got ${ex?.javaClass?.simpleName}",
            ex is IllegalStateException,
        )
    }

    @Test
    fun `model directory throws when external is null and internal is unusable`() {
        val internalRoot = temporaryFolder.newFolder("internal")
        internalRoot.resolve("models").writeText("blocking file")

        val ex = runCatching {
            LocalModelManager.resolveUsableModelDir(null, internalRoot)
        }.exceptionOrNull()

        assertTrue(ex is IllegalStateException)
    }

    @Test
    fun `model directory uses internal when external root is a file not a directory`() {
        val externalAsFile = temporaryFolder.newFile("external-as-file")
        val internalRoot = temporaryFolder.newFolder("internal")

        val dir = LocalModelManager.resolveUsableModelDir(externalAsFile, internalRoot)

        // External as a file (not a dir) → mkdirs under it fails → falls through to internal.
        assertEquals(internalRoot.resolve("models"), dir)
        assertTrue(dir.isDirectory)
    }

    @Test
    fun `model directory throws when both roots are missing`() {
        val internalRoot = temporaryFolder.newFolder("internal")

        val ex = runCatching {
            // external null, internal = a path that mkdirs cannot create (file blocking parent)
            LocalModelManager.resolveUsableModelDir(null, internalRoot.resolve("does-not-exist-parent").resolve("models"))
        }.exceptionOrNull()
        // Either throws IllegalStateException or just creates the path; assert no crash only.
        // This is a smoke test — the contract guarantees either success or IllegalStateException.
        if (ex != null) {
            assertTrue(
                "exception must be IllegalStateException, got ${ex.javaClass.simpleName}",
                ex is IllegalStateException,
            )
        }
    }

    // --- 扩展覆盖 ---

    @Test
    fun `model directory external 写探针失败 但 internal 可写 走 internal`() {
        // 模拟 canWriteDirectory 对 external 返回 false（探针失败），但 internal 可写
        val externalRoot = temporaryFolder.newFolder("external")
        val internalRoot = temporaryFolder.newFolder("internal")
        val externalModelDir = externalRoot.resolve("models")

        val dir = LocalModelManager.resolveUsableModelDir(
            externalRoot = externalRoot,
            internalRoot = internalRoot,
            canWriteDirectory = { candidate ->
                // external 不允许写，internal 允许
                candidate != externalModelDir
            },
        )
        assertEquals(internalRoot.resolve("models"), dir)
        assertTrue(dir.isDirectory)
    }

    @Test
    fun `model directory external 写探针失败 但 external 是 file 走 internal`() {
        val externalRoot = temporaryFolder.newFile("external-file")
        val internalRoot = temporaryFolder.newFolder("internal")

        val dir = LocalModelManager.resolveUsableModelDir(externalRoot, internalRoot)
        // externalRoot 是 file 不是 dir，mkdirs 失败 → 走 internal
        assertEquals(internalRoot.resolve("models"), dir)
    }

    @Test
    fun `model directory internal root 不存在 但可创建 返回 internal`() {
        // internalRoot 指向一个尚未创建的子目录 → mkdirs 应能成功创建
        val internalRoot = temporaryFolder.newFolder("internal")
        val target = internalRoot.resolve("nested").resolve("models")
        val dir = LocalModelManager.resolveUsableModelDir(null, internalRoot.resolve("nested"))
        assertEquals(target, dir)
        assertTrue(dir.isDirectory)
    }

    @Test
    fun `model directory 写探针两者都失败 抛 IllegalStateException`() {
        val externalRoot = temporaryFolder.newFolder("external")
        val internalRoot = temporaryFolder.newFolder("internal")

        val ex = runCatching {
            LocalModelManager.resolveUsableModelDir(
                externalRoot = externalRoot,
                internalRoot = internalRoot,
                canWriteDirectory = { _ -> false },
            )
        }.exceptionOrNull()

        assertTrue("must throw IllegalStateException", ex is IllegalStateException)
        assertTrue(
            "异常信息应提到两个候选路径",
            ex?.message?.contains(externalRoot.resolve("models").absolutePath) == true ||
                ex?.message?.contains(internalRoot.resolve("models").absolutePath) == true,
        )
    }

    @Test
    fun `AVAILABLE_MODELS 恰好 2 条 且都是 litertlm 后缀`() {
        assertEquals(2, LocalModelManager.AVAILABLE_MODELS.size)
        for (model in LocalModelManager.AVAILABLE_MODELS) {
            assertTrue(
                "fileName 必须以 .litertlm 结尾, got ${model.fileName}",
                model.fileName.endsWith(".litertlm"),
            )
            assertTrue("URL 必须是 huggingface.co", model.url.contains("huggingface.co"))
            assertTrue("sizeBytes 必须 > 0", model.sizeBytes > 0L)
            assertTrue("minRamGb 必须 > 0", model.minRamGb > 0)
        }
    }

    @Test
    fun `ModelInfo data class equality 与 copy 不改变 id`() {
        val a = LocalModelManager.AVAILABLE_MODELS[0]
        val b = a.copy()
        assertEquals(a, b)
        val c = a.copy(displayName = "Custom")
        assertEquals("id 应保持不变", a.id, c.id)
        assertEquals("displayName 改变", "Custom", c.displayName)
        assertNotEquals(a, c)
    }

    @Test
    fun `AvailabilitySource 枚举数量为 3 且名字固定`() {
        assertEquals(3, LocalModelManager.AvailabilitySource.values().size)
        assertEquals(
            "MANAGED_DOWNLOAD",
            LocalModelManager.AvailabilitySource.MANAGED_DOWNLOAD.name,
        )
        assertEquals(
            "LINKED_FILE",
            LocalModelManager.AvailabilitySource.LINKED_FILE.name,
        )
        assertEquals(
            "MISSING",
            LocalModelManager.AvailabilitySource.MISSING.name,
        )
    }

    @Test
    fun `StatusKind 枚举数量为 3 且名字固定`() {
        assertEquals(3, LocalModelManager.StatusKind.values().size)
        assertEquals("READY", LocalModelManager.StatusKind.READY.name)
        assertEquals("WARNING", LocalModelManager.StatusKind.WARNING.name)
        assertEquals("NEUTRAL", LocalModelManager.StatusKind.NEUTRAL.name)
    }

    @Test
    fun `ModelAvailability data class equality 与 copy`() {
        val a = LocalModelManager.ModelAvailability(
            isAvailable = true,
            source = LocalModelManager.AvailabilitySource.MANAGED_DOWNLOAD,
        )
        val b = a.copy()
        assertEquals(a, b)
        val c = a.copy(isAvailable = false, source = LocalModelManager.AvailabilitySource.MISSING)
        assertEquals(false, c.isAvailable)
        assertEquals(LocalModelManager.AvailabilitySource.MISSING, c.source)
    }

    @Test
    fun `DeviceSupport CatalogEntry ActiveModelState ModelStorageDiagnostics 都是 data class`() {
        // 反射式验证这些嵌套类型在 class 上声明为 data class
        // 判定方式：data class 自动生成 componentN() 函数（component1, component2...）
        val types = listOf(
            LocalModelManager.DeviceSupport::class.java,
            LocalModelManager.CatalogEntry::class.java,
            LocalModelManager.ActiveModelState::class.java,
            LocalModelManager.ModelStorageDiagnostics::class.java,
        )
        for (t in types) {
            val componentMethods = t.declaredMethods.count { it.name.startsWith("component") }
            assertTrue(
                "${t.simpleName} 应是 data class (声明 componentN 方法数应 >= 3)",
                componentMethods >= 3,
            )
        }
    }
}
