package com.ataiva.eden.cli

import kotlin.test.*

class MainTest {

    @Test
    fun testCliContextCreation() {
        val context = CliContext(
            verbose = true,
            configPath = "/path/to/config",
            profile = "test",
            apiUrl = "https://api.test.com"
        )
        
        assertEquals(true, context.verbose)
        assertEquals("/path/to/config", context.configPath)
        assertEquals("test", context.profile)
        assertEquals("https://api.test.com", context.apiUrl)
    }

    @Test
    fun testCliContextDefaults() {
        val context = CliContext()
        
        assertEquals(false, context.verbose)
        assertNull(context.configPath)
        assertEquals("default", context.profile)
        assertNull(context.apiUrl)
    }

    @Test
    fun testAuthCommandCreation() {
        val command = AuthCommand()
        assertEquals("auth", command.name)
        assertEquals("Authentication and user management", command.description)
    }

    @Test
    fun testVaultCommandCreation() {
        val command = VaultCommand()
        assertEquals("vault", command.name)
        assertEquals("Secrets management", command.description)
    }

    @Test
    fun testFlowCommandCreation() {
        val command = FlowCommand()
        assertEquals("flow", command.name)
        assertEquals("Workflow automation", command.description)
    }

    @Test
    fun testTaskCommandCreation() {
        val command = TaskCommand()
        assertEquals("task", command.name)
        assertEquals("Task orchestration", command.description)
    }

    @Test
    fun testMonitorCommandCreation() {
        val command = MonitorCommand()
        assertEquals("monitor", command.name)
        assertEquals("Monitoring and alerting", command.description)
    }

    @Test
    fun testSyncCommandCreation() {
        val command = SyncCommand()
        assertEquals("sync", command.name)
        assertEquals("Multi-cloud synchronization", command.description)
    }

    @Test
    fun testInsightCommandCreation() {
        val command = InsightCommand()
        assertEquals("insight", command.name)
        assertEquals("Analytics and insights", command.description)
    }

    @Test
    fun testHubCommandCreation() {
        val command = HubCommand()
        assertEquals("hub", command.name)
        assertEquals("Service discovery and configuration", command.description)
    }

    @Test
    fun testConfigCommandCreation() {
        val command = ConfigCommand()
        assertEquals("config", command.name)
        assertEquals("CLI configuration management", command.description)
    }

    @Test
    fun testAuthCommandExecution() {
        val command = AuthCommand()
        val context = CliContext(verbose = true)
        
        // Test that execute doesn't throw exceptions
        assertDoesNotThrow {
            command.execute(context)
        }
    }

    @Test
    fun testVaultCommandExecution() {
        val command = VaultCommand()
        val context = CliContext(verbose = true)
        
        assertDoesNotThrow {
            command.execute(context)
        }
    }

    @Test
    fun testFlowCommandExecution() {
        val command = FlowCommand()
        val context = CliContext(verbose = true)
        
        assertDoesNotThrow {
            command.execute(context)
        }
    }

    @Test
    fun testTaskCommandExecution() {
        val command = TaskCommand()
        val context = CliContext(verbose = true)
        
        assertDoesNotThrow {
            command.execute(context)
        }
    }

    @Test
    fun testMonitorCommandExecution() {
        val command = MonitorCommand()
        val context = CliContext(verbose = true)
        
        assertDoesNotThrow {
            command.execute(context)
        }
    }

    @Test
    fun testSyncCommandExecution() {
        val command = SyncCommand()
        val context = CliContext(verbose = true)
        
        assertDoesNotThrow {
            command.execute(context)
        }
    }

    @Test
    fun testInsightCommandExecution() {
        val command = InsightCommand()
        val context = CliContext(verbose = true)
        
        assertDoesNotThrow {
            command.execute(context)
        }
    }

    @Test
    fun testHubCommandExecution() {
        val command = HubCommand()
        val context = CliContext(verbose = true)
        
        assertDoesNotThrow {
            command.execute(context)
        }
    }

    @Test
    fun testConfigCommandExecution() {
        val command = ConfigCommand()
        val context = CliContext(verbose = true)
        
        assertDoesNotThrow {
            command.execute(context)
        }
    }

    @Test
    fun testCliCommandWasExecutedInitiallyFalse() {
        val command = AuthCommand()
        assertFalse(command.wasExecuted)
    }

    @Test
    fun testCliCommandWasExecutedAfterExecution() {
        val command = AuthCommand()
        command.execute()
        assertTrue(command.wasExecuted)
    }

    @Test
    fun testMultipleCommandsExecution() {
        val authCommand = AuthCommand()
        val vaultCommand = VaultCommand()
        
        authCommand.execute()
        vaultCommand.execute()
        
        assertTrue(authCommand.wasExecuted)
        assertTrue(vaultCommand.wasExecuted)
    }

    @Test
    fun testCommandExecutionTracking() {
        // Clear any previous executions
        CliCommand.executedSubcommands.clear()
        
        val command1 = AuthCommand()
        val command2 = VaultCommand()
        
        assertFalse(command1.wasExecuted)
        assertFalse(command2.wasExecuted)
        
        command1.execute()
        assertTrue(command1.wasExecuted)
        assertFalse(command2.wasExecuted)
        
        command2.execute()
        assertTrue(command1.wasExecuted)
        assertTrue(command2.wasExecuted)
    }

    @Test
    fun testContextWithAllParameters() {
        val context = CliContext(
            verbose = true,
            configPath = "/custom/config.yml",
            profile = "production",
            apiUrl = "https://api.production.com"
        )
        
        assertTrue(context.verbose)
        assertEquals("/custom/config.yml", context.configPath)
        assertEquals("production", context.profile)
        assertEquals("https://api.production.com", context.apiUrl)
    }

    @Test
    fun testContextWithPartialParameters() {
        val context = CliContext(
            verbose = false,
            profile = "staging"
        )
        
        assertFalse(context.verbose)
        assertNull(context.configPath)
        assertEquals("staging", context.profile)
        assertNull(context.apiUrl)
    }
}