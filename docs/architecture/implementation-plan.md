# Eden DevOps Suite - Implementation Architecture Plan

**Document Purpose**: Detailed technical architecture and implementation plan for transforming Eden's foundation into a working DevOps platform.

**Target Phase**: Phase 1b - Core Business Logic Implementation  
**Timeline**: Q1 2025 (3 months)  
**Last Updated**: December 3, 2024

---

## 🏗️ Architecture Overview

### Current State
```
┌─────────────────────────────────────────────────────────────────┐
│                     CURRENT ARCHITECTURE                        │
│  ┌─────────────────────┐    ┌─────────────────────────────────┐ │
│  │   CLI Framework     │    │    8 Service Skeletons          │ │
│  │   (Mock Data)       │    │    (REST + Health Endpoints)    │ │
│  └─────────────────────┘    └─────────────────────────────────┘ │
│                                        │                        │
│  ┌─────────────────────────────────────────────────────────────┐│
│  │         Shared Libraries (✅ PRODUCTION READY)              ││
│  │  Crypto • Auth • Database • Events • Core Models           ││
│  └─────────────────────────────────────────────────────────────┘│
│                                        │                        │
│  ┌─────────────────────────────────────────────────────────────┐│
│  │         Infrastructure (✅ COMPLETE)                        ││
│  │         PostgreSQL • Redis • Docker • Gradle               ││
│  └─────────────────────────────────────────────────────────────┘│
└─────────────────────────────────────────────────────────────────┘
```

### Target Architecture (Phase 1b)
```
┌─────────────────────────────────────────────────────────────────┐
│                     TARGET ARCHITECTURE                         │
│  ┌─────────────────────┐    ┌─────────────────────────────────┐ │
│  │   Enhanced CLI      │    │    API Gateway                  │ │
│  │   (Real API Calls)  │◄──►│    (Auth + Routing)             │ │
│  └─────────────────────┘    └─────────────────────────────────┘ │
│                                        │                        │
│  ┌─────────────────────────────────────────────────────────────┐│
│  │              CORE SERVICES (Business Logic)                 ││
│  │  ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────────────────┐││
│  │  │  Vault  │ │  Flow   │ │  Task   │ │  Monitor/Sync/      ││
│  │  │ Service │ │ Service │ │ Service │ │  Insight/Hub        ││
│  │  │(Real DB)│ │(Engine) │ │(Queue)  │ │  (Enhanced)         ││
│  │  └─────────┘ └─────────┘ └─────────┘ └─────────────────────┘││
│  └─────────────────────────────────────────────────────────────┘│
│                                        │                        │
│  ┌─────────────────────────────────────────────────────────────┐│
│  │         Shared Libraries (✅ PRODUCTION READY)              ││
│  │  Crypto • Auth • Database • Events • Core Models           ││
│  └─────────────────────────────────────────────────────────────┘│
│                                        │                        │
│  ┌─────────────────────────────────────────────────────────────┐│
│  │         Data Layer (Enhanced Schemas)                       ││
│  │         PostgreSQL • Redis • Event Streams                 ││
│  └─────────────────────────────────────────────────────────────┘│
└─────────────────────────────────────────────────────────────────┘
```

---

## 🗄️ Database Schema Implementation

### Core Tables for Phase 1b

```sql
-- Users and Authentication
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(255),
    is_active BOOLEAN DEFAULT true,
    is_verified BOOLEAN DEFAULT false,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE user_sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES users(id) ON DELETE CASCADE,
    token_hash VARCHAR(255) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP DEFAULT NOW(),
    last_used_at TIMESTAMP DEFAULT NOW()
);

-- Secrets Management (Eden Vault)
CREATE TABLE secrets (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    encrypted_value TEXT NOT NULL,
    encryption_key_id VARCHAR(255) NOT NULL,
    secret_type VARCHAR(100) DEFAULT 'generic',
    description TEXT,
    user_id UUID REFERENCES users(id) ON DELETE CASCADE,
    organization_id UUID, -- For future multi-tenancy
    version INTEGER DEFAULT 1,
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW(),
    UNIQUE(name, user_id, version)
);

CREATE TABLE secret_access_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    secret_id UUID REFERENCES secrets(id) ON DELETE CASCADE,
    user_id UUID REFERENCES users(id) ON DELETE CASCADE,
    action VARCHAR(50) NOT NULL, -- 'read', 'write', 'delete'
    ip_address INET,
    user_agent TEXT,
    created_at TIMESTAMP DEFAULT NOW()
);

-- Workflow Management (Eden Flow)
CREATE TABLE workflows (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    description TEXT,
    definition JSONB NOT NULL, -- YAML converted to JSON
    user_id UUID REFERENCES users(id) ON DELETE CASCADE,
    status VARCHAR(50) DEFAULT 'active', -- 'active', 'paused', 'archived'
    version INTEGER DEFAULT 1,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW(),
    UNIQUE(name, user_id)
);

CREATE TABLE workflow_executions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workflow_id UUID REFERENCES workflows(id) ON DELETE CASCADE,
    triggered_by UUID REFERENCES users(id),
    status VARCHAR(50) DEFAULT 'pending', -- 'pending', 'running', 'completed', 'failed', 'cancelled'
    input_data JSONB,
    output_data JSONB,
    error_message TEXT,
    started_at TIMESTAMP DEFAULT NOW(),
    completed_at TIMESTAMP,
    duration_ms INTEGER
);

CREATE TABLE workflow_steps (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    execution_id UUID REFERENCES workflow_executions(id) ON DELETE CASCADE,
    step_name VARCHAR(255) NOT NULL,
    step_type VARCHAR(100) NOT NULL,
    status VARCHAR(50) DEFAULT 'pending',
    input_data JSONB,
    output_data JSONB,
    error_message TEXT,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    duration_ms INTEGER,
    step_order INTEGER NOT NULL
);

-- Task Management (Eden Task)
CREATE TABLE tasks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    description TEXT,
    task_type VARCHAR(100) NOT NULL,
    configuration JSONB NOT NULL,
    schedule_cron VARCHAR(255), -- Cron expression for scheduled tasks
    user_id UUID REFERENCES users(id) ON DELETE CASCADE,
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE task_executions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    task_id UUID REFERENCES tasks(id) ON DELETE CASCADE,
    status VARCHAR(50) DEFAULT 'queued', -- 'queued', 'running', 'completed', 'failed', 'cancelled'
    priority INTEGER DEFAULT 0,
    input_data JSONB,
    output_data JSONB,
    error_message TEXT,
    progress_percentage INTEGER DEFAULT 0,
    queued_at TIMESTAMP DEFAULT NOW(),
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    duration_ms INTEGER
);

-- System Monitoring and Events
CREATE TABLE system_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_type VARCHAR(100) NOT NULL,
    source_service VARCHAR(100) NOT NULL,
    event_data JSONB NOT NULL,
    severity VARCHAR(50) DEFAULT 'info', -- 'debug', 'info', 'warning', 'error', 'critical'
    user_id UUID REFERENCES users(id),
    created_at TIMESTAMP DEFAULT NOW()
);

-- Indexes for Performance
CREATE INDEX idx_secrets_user_id ON secrets(user_id);
CREATE INDEX idx_secrets_name ON secrets(name);
CREATE INDEX idx_workflow_executions_status ON workflow_executions(status);
CREATE INDEX idx_task_executions_status ON task_executions(status);
CREATE INDEX idx_system_events_type ON system_events(event_type);
CREATE INDEX idx_system_events_created_at ON system_events(created_at);
```

---

## 🔐 Eden Vault Service Implementation

### Service Architecture
```kotlin
// Core service structure
class VaultService(
    private val secretsRepository: SecretsRepository,
    private val encryptionService: EncryptionService,
    private val auditLogger: AuditLogger,
    private val eventBus: EventBus
) {
    suspend fun storeSecret(request: StoreSecretRequest, userId: String): SecretResponse
    suspend fun retrieveSecret(name: String, userId: String): SecretResponse
    suspend fun listSecrets(userId: String, filters: SecretFilters): List<SecretInfo>
    suspend fun updateSecret(name: String, request: UpdateSecretRequest, userId: String): SecretResponse
    suspend fun deleteSecret(name: String, userId: String): Boolean
    suspend fun getSecretHistory(name: String, userId: String): List<SecretVersion>
}
```

### Repository Implementation
```kotlin
class PostgreSQLSecretsRepository(
    private val database: Database
) : SecretsRepository {
    
    override suspend fun create(secret: Secret): Secret = database.dbQuery {
        SecretsTable.insert {
            it[id] = secret.id
            it[name] = secret.name
            it[encryptedValue] = secret.encryptedValue
            it[encryptionKeyId] = secret.encryptionKeyId
            it[secretType] = secret.type
            it[description] = secret.description
            it[userId] = secret.userId
            it[version] = secret.version
        }
        secret
    }
    
    override suspend fun findByNameAndUser(name: String, userId: String): Secret? = database.dbQuery {
        SecretsTable.select { 
            (SecretsTable.name eq name) and 
            (SecretsTable.userId eq userId) and 
            (SecretsTable.isActive eq true) 
        }
        .orderBy(SecretsTable.version, SortOrder.DESC)
        .limit(1)
        .map { rowToSecret(it) }
        .singleOrNull()
    }
    
    // Additional repository methods...
}
```

### Encryption Service Integration
```kotlin
class VaultEncryptionService(
    private val encryptionService: EncryptionService
) {
    suspend fun encryptSecret(plaintext: String, userKey: String): EncryptedSecret {
        val derivedKey = encryptionService.deriveKey(userKey, "vault-secret")
        val encrypted = encryptionService.encrypt(plaintext, derivedKey)
        return EncryptedSecret(
            encryptedValue = encrypted.ciphertext,
            keyId = encrypted.keyId,
            nonce = encrypted.nonce
        )
    }
    
    suspend fun decryptSecret(encrypted: EncryptedSecret, userKey: String): String {
        val derivedKey = encryptionService.deriveKey(userKey, "vault-secret")
        return encryptionService.decrypt(
            EncryptedData(
                ciphertext = encrypted.encryptedValue,
                keyId = encrypted.keyId,
                nonce = encrypted.nonce
            ),
            derivedKey
        )
    }
}
```

---

## 🔄 Eden Flow Service Implementation

### Workflow Engine Architecture
```kotlin
class WorkflowEngine(
    private val workflowRepository: WorkflowRepository,
    private val executionRepository: ExecutionRepository,
    private val stepExecutor: StepExecutor,
    private val eventBus: EventBus
) {
    suspend fun executeWorkflow(workflowId: String, input: Map<String, Any>): ExecutionResult {
        val workflow = workflowRepository.findById(workflowId) 
            ?: throw WorkflowNotFoundException(workflowId)
        
        val execution = createExecution(workflow, input)
        
        try {
            val result = executeSteps(workflow.definition, execution, input)
            completeExecution(execution, result)
            return ExecutionResult.Success(execution.id, result)
        } catch (e: Exception) {
            failExecution(execution, e)
            return ExecutionResult.Failure(execution.id, e.message ?: "Unknown error")
        }
    }
    
    private suspend fun executeSteps(
        definition: WorkflowDefinition, 
        execution: WorkflowExecution,
        context: Map<String, Any>
    ): Map<String, Any> {
        var currentContext = context.toMutableMap()
        
        for ((index, step) in definition.steps.withIndex()) {
            val stepExecution = createStepExecution(execution.id, step, index)
            
            try {
                val stepResult = stepExecutor.execute(step, currentContext)
                completeStepExecution(stepExecution, stepResult)
                currentContext.putAll(stepResult.outputs)
            } catch (e: Exception) {
                failStepExecution(stepExecution, e)
                if (!step.continueOnError) {
                    throw WorkflowExecutionException("Step ${step.name} failed", e)
                }
            }
        }
        
        return currentContext
    }
}
```

### YAML Workflow Definition Parser
```kotlin
data class WorkflowDefinition(
    val name: String,
    val description: String?,
    val version: String = "1.0",
    val steps: List<WorkflowStep>,
    val variables: Map<String, Any> = emptyMap()
)

data class WorkflowStep(
    val name: String,
    val type: StepType,
    val configuration: Map<String, Any>,
    val continueOnError: Boolean = false,
    val timeout: Duration? = null,
    val retryPolicy: RetryPolicy? = null
)

class WorkflowDefinitionParser {
    fun parseYaml(yamlContent: String): WorkflowDefinition {
        val yaml = Yaml()
        val data = yaml.load<Map<String, Any>>(yamlContent)
        
        return WorkflowDefinition(
            name = data["name"] as String,
            description = data["description"] as String?,
            version = data["version"] as String? ?: "1.0",
            steps = parseSteps(data["steps"] as List<Map<String, Any>>),
            variables = data["variables"] as Map<String, Any>? ?: emptyMap()
        )
    }
    
    private fun parseSteps(stepsData: List<Map<String, Any>>): List<WorkflowStep> {
        return stepsData.map { stepData ->
            WorkflowStep(
                name = stepData["name"] as String,
                type = StepType.valueOf((stepData["type"] as String).uppercase()),
                configuration = stepData["config"] as Map<String, Any>? ?: emptyMap(),
                continueOnError = stepData["continueOnError"] as Boolean? ?: false,
                timeout = (stepData["timeout"] as String?)?.let { Duration.parse(it) },
                retryPolicy = parseRetryPolicy(stepData["retry"] as Map<String, Any>?)
            )
        }
    }
}
```

---

## ⚡ Eden Task Service Implementation

### Task Queue Architecture
```kotlin
class TaskQueueService(
    private val redisClient: RedisClient,
    private val taskRepository: TaskRepository,
    private val executionRepository: TaskExecutionRepository
) {
    suspend fun enqueueTask(task: Task, priority: Int = 0): TaskExecution {
        val execution = TaskExecution(
            id = UUID.randomUUID(),
            taskId = task.id,
            status = TaskStatus.QUEUED,
            priority = priority,
            queuedAt = Instant.now()
        )
        
        // Store execution in database
        executionRepository.create(execution)
        
        // Add to Redis queue
        val queueKey = "tasks:queue:${task.type}"
        val taskData = TaskQueueItem(
            executionId = execution.id,
            taskId = task.id,
            priority = priority,
            queuedAt = execution.queuedAt
        )
        
        redisClient.zadd(queueKey, priority.toDouble(), Json.encodeToString(taskData))
        
        return execution
    }
    
    suspend fun dequeueTask(taskType: String): TaskQueueItem? {
        val queueKey = "tasks:queue:$taskType"
        val items = redisClient.zrevrange(queueKey, 0, 0)
        
        return items.firstOrNull()?.let { item ->
            redisClient.zrem(queueKey, item)
            Json.decodeFromString<TaskQueueItem>(item)
        }
    }
}
```

### Task Executor
```kotlin
class TaskExecutor(
    private val executionRepository: TaskExecutionRepository,
    private val taskHandlers: Map<String, TaskHandler>,
    private val eventBus: EventBus
) {
    suspend fun executeTask(execution: TaskExecution): TaskResult {
        val task = taskRepository.findById(execution.taskId) 
            ?: throw TaskNotFoundException(execution.taskId)
        
        val handler = taskHandlers[task.type] 
            ?: throw UnsupportedTaskTypeException(task.type)
        
        // Update execution status
        executionRepository.updateStatus(execution.id, TaskStatus.RUNNING, Instant.now())
        
        try {
            val result = handler.execute(task, execution)
            
            // Update completion status
            executionRepository.complete(
                execution.id, 
                TaskStatus.COMPLETED, 
                result.output,
                Instant.now()
            )
            
            // Publish completion event
            eventBus.publish(TaskCompletedEvent(execution.id, task.id, result))
            
            return result
            
        } catch (e: Exception) {
            // Update failure status
            executionRepository.fail(
                execution.id,
                TaskStatus.FAILED,
                e.message ?: "Unknown error",
                Instant.now()
            )
            
            // Publish failure event
            eventBus.publish(TaskFailedEvent(execution.id, task.id, e))
            
            throw e
        }
    }
}
```

---

## 🌐 API Gateway Enhancement

### Authentication Middleware
```kotlin
class JwtAuthenticationMiddleware(
    private val jwtService: JwtAuthenticationService
) {
    fun install(application: Application) {
        application.install(Authentication) {
            jwt("auth-jwt") {
                verifier(jwtService.verifier)
                validate { credential ->
                    val userId = credential.payload.getClaim("userId").asString()
                    val user = userService.findById(userId)
                    user?.let { JWTPrincipal(credential.payload) }
                }
                challenge { defaultScheme, realm ->
                    call.respond(HttpStatusCode.Unauthorized, "Token is not valid or has expired")
                }
            }
        }
    }
}
```

### Service Routing
```kotlin
fun Application.configureRouting() {
    routing {
        // Health check (no auth required)
        get("/health") {
            call.respond(mapOf("status" to "healthy", "timestamp" to System.currentTimeMillis()))
        }
        
        // Authenticated routes
        authenticate("auth-jwt") {
            route("/api/v1") {
                // Vault service routes
                route("/vault") {
                    get("/secrets") { 
                        proxyToService("vault", "/api/v1/secrets", call) 
                    }
                    post("/secrets") { 
                        proxyToService("vault", "/api/v1/secrets", call) 
                    }
                    get("/secrets/{name}") { 
                        proxyToService("vault", "/api/v1/secrets/${call.parameters["name"]}", call) 
                    }
                }
                
                // Flow service routes
                route("/workflows") {
                    get { proxyToService("flow", "/api/v1/workflows", call) }
                    post { proxyToService("flow", "/api/v1/workflows", call) }
                    post("/{id}/execute") { 
                        proxyToService("flow", "/api/v1/workflows/${call.parameters["id"]}/execute", call) 
                    }
                }
                
                // Task service routes
                route("/tasks") {
                    get { proxyToService("task", "/api/v1/tasks", call) }
                    post { proxyToService("task", "/api/v1/tasks", call) }
                    post("/{id}/execute") { 
                        proxyToService("task", "/api/v1/tasks/${call.parameters["id"]}/execute", call) 
                    }
                }
            }
        }
    }
}
```

---

## 🖥️ CLI Integration Implementation

### HTTP Client Service
```kotlin
class EdenApiClient(
    private val baseUrl: String,
    private val tokenManager: TokenManager
) {
    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                prettyPrint = true
                ignoreUnknownKeys = true
            })
        }
        install(Auth) {
            bearer {
                loadTokens {
                    BearerTokens(tokenManager.getAccessToken() ?: "", "")
                }
            }
        }
    }
    
    suspend fun getSecrets(): List<SecretInfo> {
        return client.get("$baseUrl/api/v1/vault/secrets").body()
    }
    
    suspend fun getSecret(name: String): SecretDetail {
        return client.get("$baseUrl/api/v1/vault/secrets/$name").body()
    }
    
    suspend fun createSecret(request: CreateSecretRequest): SecretResponse {
        return client.post("$baseUrl/api/v1/vault/secrets") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
    }
    
    suspend fun executeWorkflow(workflowId: String, input: Map<String, Any>): ExecutionResponse {
        return client.post("$baseUrl/api/v1/workflows/$workflowId/execute") {
            contentType(ContentType.Application.Json)
            setBody(input)
        }.body()
    }
}
```

### Updated CLI Commands
```kotlin
// Replace mock implementation with real API calls
private suspend fun handleVaultList() {
    println("🔐 Vault Secrets")
    println("=" * 40)
    
    try {
        val secrets = apiClient.getSecrets()
        if (secrets.isEmpty()) {
            println("No secrets found.")
            return
        }
        
        secrets.forEach { secret ->
            val icon = when (secret.type) {
                "api-key" -> "🔑"
                "password" -> "🔒"
                "certificate" -> "📜"
                else -> "📄"
            }
            println("$icon ${secret.name} (${secret.type}) - Updated: ${secret.updatedAt}")
        }
        
        println("\nTotal: ${secrets.size} secrets")
        
    } catch (e: Exception) {
        println("❌ Failed to retrieve secrets: ${e.message}")
        exitProcess(1)
    }
}

private suspend fun handleVaultGet(name: String?) {
    if (name == null) {
        println("❌ Secret name required. Usage: eden vault get <name>")
        exitProcess(1)
    }
    
    try {
        val secret = apiClient.getSecret(name)
        
        println("🔐 Secret: $name")
        println("  • Type: ${secret.type}")
        println("  • Value: ${maskSecret(secret.value)}")
        println("  • Created: ${secret.createdAt}")
        println("  • Updated: ${secret.updatedAt}")
        
        print("\nReveal full value? (y/N): ")
        val reveal = readLine()?.lowercase() == "y"
        if (reveal) {
            println("  • Full Value: ${secret.value}")
        }
        
    } catch (e: Exception) {
        println("❌ Failed to retrieve secret '$name': ${e.message}")
        exitProcess(1)
    }
}
```

---

## 📊 Implementation Timeline

### Month 1: Eden Vault Implementation
- **Week 1**: Database schema and repository implementation
- **Week 2**: Encryption service integration and business logic
- **Week 3**: REST API implementation and testing
- **Week 4**: CLI integration and end-to-end testing

### Month 2: Eden Flow Implementation
- **Week 1**: Workflow definition parser and database schema
- **Week 2**: Workflow execution engine implementation
- **Week 3**: Step executors and error handling
- **Week 4**: REST API and CLI integration

### Month 3: Eden Task + Integration
- **Week 1**: Task queue system with Redis
- **Week 2**: Task executors and scheduling
- **Week 3**: API Gateway authentication and routing
- **Week 4**: System integration testing and documentation

---

## 🧪 Testing Strategy

### Unit Testing
- Repository layer with in-memory database
- Service layer with mocked dependencies
- Encryption/decryption functionality
- Workflow parsing and validation

### Integration Testing
- Database operations with real PostgreSQL
- Redis queue operations
- Service-to-service communication
- End-to-end API workflows

### Performance Testing
- Database query performance
- Concurrent secret operations
- Workflow execution under load
- Task queue throughput

---

## 📈 Success Metrics

### Functional Metrics
- [ ] Users can store/retrieve 1000+ secrets without performance degradation
- [ ] Workflows execute successfully with <5% failure rate
- [ ] Task queue processes 100+ jobs/minute
- [ ] CLI commands complete in <2 seconds for typical operations

### Quality Metrics
- [ ] >80% test coverage for all business logic
- [ ] Zero critical security vulnerabilities
- [ ] <100ms average API response time
- [ ] 99.9% service uptime during testing

---

This implementation plan provides a clear path from the current foundation to a working DevOps platform, focusing on core functionality that delivers real user value while maintaining the excellent architectural foundation already established.