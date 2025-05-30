# CLI Reference

The Eden CLI (`eden`) is the primary command-line interface for interacting with all Eden DevOps Suite components.

## Installation

### Build from Source

```bash
# Clone the repository
git clone https://github.com/ataivadev/eden.git
cd eden

# Build CLI for your platform
./gradlew :clients:cli:linkReleaseExecutableLinuxX64    # Linux
./gradlew :clients:cli:linkReleaseExecutableMacosX64    # macOS
./gradlew :clients:cli:linkReleaseExecutableMingwX64    # Windows

# Add to PATH (Linux/macOS example)
sudo ln -s $(pwd)/clients/cli/build/bin/linuxX64/releaseExecutable/eden /usr/local/bin/eden
```

### Verify Installation

```bash
eden --help
eden --version
```

## Global Options

All Eden commands support these global options:

```bash
eden [global-options] <command> [command-options]
```

### Global Options
- `-v, --verbose` - Enable verbose output
- `-c, --config <path>` - Specify configuration file path
- `-p, --profile <name>` - Use specific configuration profile (default: "default")
- `--api-url <url>` - Override API base URL

### Examples
```bash
# Verbose output
eden --verbose auth login

# Custom configuration
eden --config ~/.eden/custom.yml vault list

# Different profile
eden --profile production monitor status

# Custom API URL
eden --api-url https://eden.company.com auth login
```

## Authentication Commands

### `eden auth`

Manage authentication and user sessions.

#### `eden auth login`

Login to Eden platform.

```bash
eden auth login [options]
```

**Options:**
- `--email <email>` - Email address for login
- `--password <password>` - Password (not recommended, will prompt if omitted)

**Examples:**
```bash
# Interactive login (recommended)
eden auth login

# Login with email (will prompt for password)
eden auth login --email user@company.com

# Non-interactive login (not recommended for security)
eden auth login --email user@company.com --password mypassword
```

#### `eden auth logout`

Logout from Eden platform.

```bash
eden auth logout
```

#### `eden auth whoami`

Display current user information.

```bash
eden auth whoami
```

**Output:**
```
User: john.doe@company.com
Organization: ACME Corp
Role: Developer
Session Expires: 2024-12-31 23:59:59 UTC
```

## Secrets Management Commands

### `eden vault`

Manage secrets with zero-knowledge encryption.

> **Note**: Vault commands are currently in development. Most functionality returns "to be implemented" messages.

#### `eden vault set`

Store a secret.

```bash
eden vault set <key> <value> [options]
```

**Options:**
- `--description <text>` - Description for the secret
- `--tags <tag1,tag2>` - Comma-separated tags
- `--ttl <duration>` - Time-to-live (e.g., "30d", "1h")

**Examples:**
```bash
# Store a simple secret
eden vault set DATABASE_URL "postgresql://user:pass@host:5432/db"

# Store with metadata
eden vault set API_KEY "sk-1234567890" \
  --description "Production API key" \
  --tags "production,api" \
  --ttl "90d"
```

#### `eden vault get`

Retrieve a secret.

```bash
eden vault get <key> [options]
```

**Options:**
- `--show-metadata` - Include metadata in output
- `--format <json|yaml|env>` - Output format

**Examples:**
```bash
# Get secret value
eden vault get DATABASE_URL

# Get with metadata
eden vault get API_KEY --show-metadata

# Export format
eden vault get DATABASE_URL --format env
```

#### `eden vault list`

List available secrets.

```bash
eden vault list [options]
```

**Options:**
- `--tags <tag1,tag2>` - Filter by tags
- `--format <table|json|yaml>` - Output format

**Examples:**
```bash
# List all secrets
eden vault list

# Filter by tags
eden vault list --tags production

# JSON output
eden vault list --format json
```

#### `eden vault delete`

Delete a secret.

```bash
eden vault delete <key> [options]
```

**Options:**
- `--force` - Skip confirmation prompt

**Examples:**
```bash
# Delete with confirmation
eden vault delete OLD_API_KEY

# Force delete
eden vault delete OLD_API_KEY --force
```

## Workflow Commands

### `eden flow`

Manage workflow automation.

> **Note**: Flow commands are currently in development.

#### `eden flow create`

Create a new workflow.

```bash
eden flow create <name> [options]
```

**Options:**
- `--template <template>` - Use workflow template
- `--file <path>` - Workflow definition file

**Examples:**
```bash
# Create from template
eden flow create deployment --template ci-cd

# Create from file
eden flow create custom-workflow --file ./workflow.yml
```

#### `eden flow run`

Execute a workflow.

```bash
eden flow run <workflow> [options]
```

**Options:**
- `--env <environment>` - Target environment
- `--params <key=value>` - Workflow parameters
- `--wait` - Wait for completion

**Examples:**
```bash
# Run workflow
eden flow run deployment --env staging

# Run with parameters
eden flow run deployment --env production --params version=1.2.3

# Run and wait
eden flow run deployment --wait
```

#### `eden flow status`

Check workflow status.

```bash
eden flow status [workflow-id]
```

**Examples:**
```bash
# List all workflow runs
eden flow status

# Check specific workflow
eden flow status wf-12345
```

## Task Orchestration Commands

### `eden task`

Manage distributed task execution.

> **Note**: Task commands are currently in development.

#### `eden task submit`

Submit a task for execution.

```bash
eden task submit <task-file> [options]
```

**Options:**
- `--priority <high|normal|low>` - Task priority
- `--schedule <cron>` - Schedule task execution
- `--wait` - Wait for completion

**Examples:**
```bash
# Submit task
eden task submit build-job.yaml

# High priority task
eden task submit urgent-task.yaml --priority high

# Scheduled task
eden task submit backup.yaml --schedule "0 2 * * *"
```

#### `eden task status`

Check task status.

```bash
eden task status [task-id]
```

#### `eden task logs`

View task execution logs.

```bash
eden task logs <task-id> [options]
```

**Options:**
- `--follow` - Follow log output
- `--tail <lines>` - Show last N lines

## Monitoring Commands

### `eden monitor`

Manage monitoring and alerting.

> **Note**: Monitor commands are currently in development.

#### `eden monitor create`

Create a monitoring check.

```bash
eden monitor create [options]
```

**Options:**
- `--url <url>` - URL to monitor
- `--interval <duration>` - Check interval
- `--timeout <duration>` - Request timeout

**Examples:**
```bash
# Create uptime check
eden monitor create --url https://api.example.com --interval 1m

# Custom timeout
eden monitor create --url https://slow-api.com --timeout 30s
```

#### `eden monitor status`

View monitoring status.

```bash
eden monitor status [check-id]
```

## Multi-Cloud Commands

### `eden sync`

Manage multi-cloud resources and costs.

> **Note**: Sync commands are currently in development.

#### `eden sync costs`

View cloud costs.

```bash
eden sync costs [options]
```

**Options:**
- `--provider <aws,gcp,azure>` - Filter by provider
- `--period <this-month|last-month>` - Time period

**Examples:**
```bash
# All providers, current month
eden sync costs

# Specific provider
eden sync costs --provider aws

# Last month
eden sync costs --period last-month
```

#### `eden sync optimize`

Get optimization recommendations.

```bash
eden sync optimize [options]
```

**Options:**
- `--auto-apply` - Automatically apply safe optimizations
- `--recommendations` - Show recommendations only

## Analytics Commands

### `eden insight`

Manage analytics and insights.

> **Note**: Insight commands are currently in development.

#### `eden insight dashboard`

Manage dashboards.

```bash
eden insight dashboard [options]
```

**Options:**
- `--name <name>` - Dashboard name
- `--create` - Create new dashboard
- `--list` - List dashboards

#### `eden insight metrics`

View and export metrics.

```bash
eden insight metrics [options]
```

**Options:**
- `--export <csv|json>` - Export format
- `--period <duration>` - Time period

## Service Discovery Commands

### `eden hub`

Manage service discovery and configuration.

> **Note**: Hub commands are currently in development.

#### `eden hub register`

Register a service.

```bash
eden hub register [options]
```

**Options:**
- `--name <name>` - Service name
- `--port <port>` - Service port
- `--health <url>` - Health check URL

#### `eden hub discover`

Discover services.

```bash
eden hub discover [options]
```

**Options:**
- `--service <name>` - Service name to discover

## Configuration Commands

### `eden config`

Manage CLI configuration.

> **Note**: Config commands are currently in development.

#### `eden config set`

Set configuration value.

```bash
eden config set <key> <value>
```

#### `eden config get`

Get configuration value.

```bash
eden config get <key>
```

#### `eden config list`

List all configuration.

```bash
eden config list
```

## Configuration File

The Eden CLI uses a configuration file located at `~/.eden/config.yml`:

```yaml
# Default configuration
default:
  api_url: "http://localhost:8080"
  timeout: "30s"
  format: "table"

# Production configuration
production:
  api_url: "https://eden.company.com"
  timeout: "60s"
  format: "json"

# Authentication tokens (managed automatically)
auth:
  access_token: "..."
  refresh_token: "..."
  expires_at: "2024-12-31T23:59:59Z"
```

## Environment Variables

The CLI respects these environment variables:

- `EDEN_API_URL` - API base URL
- `EDEN_CONFIG_PATH` - Configuration file path
- `EDEN_PROFILE` - Configuration profile
- `EDEN_TOKEN` - Authentication token
- `EDEN_TIMEOUT` - Request timeout

## Exit Codes

The Eden CLI uses standard exit codes:

- `0` - Success
- `1` - General error
- `2` - Authentication error
- `3` - Permission denied
- `4` - Not found
- `5` - Network error

## Examples

### Complete Workflow Example

```bash
# 1. Login
eden auth login --email developer@company.com

# 2. Store secrets
eden vault set DATABASE_URL "postgresql://localhost/myapp"
eden vault set API_KEY "sk-1234567890"

# 3. Create and run workflow
eden flow create deployment --template ci-cd
eden flow run deployment --env staging --wait

# 4. Monitor the application
eden monitor create --url https://staging.myapp.com

# 5. Check costs
eden sync costs --this-month
```

### Batch Operations

```bash
# Store multiple secrets
eden vault set DB_HOST "localhost"
eden vault set DB_PORT "5432"
eden vault set DB_NAME "myapp"

# List and export
eden vault list --format json > secrets.json
```

---

**Note**: Many CLI commands are currently in development and return "to be implemented" messages. Check the [Project Status](../development/project-status.md) for current implementation status.