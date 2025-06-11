package com.ataiva.eden.hub.connector

import com.ataiva.eden.hub.engine.*
import com.ataiva.eden.hub.model.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.coroutines.future.await
import org.slf4j.LoggerFactory
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.core.SdkBytes
import software.amazon.awssdk.core.exception.SdkException
import software.amazon.awssdk.core.retry.RetryPolicy
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.cloudwatch.model.Statistic
import software.amazon.awssdk.services.ec2.Ec2AsyncClient
import software.amazon.awssdk.services.ec2.model.*
import software.amazon.awssdk.services.s3.S3AsyncClient
import software.amazon.awssdk.services.s3.model.*
import software.amazon.awssdk.services.lambda.LambdaAsyncClient
import software.amazon.awssdk.services.lambda.model.*
import software.amazon.awssdk.services.sts.StsAsyncClient
import software.amazon.awssdk.services.sts.model.GetCallerIdentityRequest
import software.amazon.awssdk.core.async.AsyncRequestBody
import software.amazon.awssdk.core.async.AsyncResponseTransformer
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

/**
 * AWS integration connector for cloud service management
 */
class AwsConnector : IntegrationConnector {
    override val type = IntegrationType.AWS
    
    private val clientCache = ConcurrentHashMap<String, AwsClients>()
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    
    private val logger = LoggerFactory.getLogger(AwsConnector::class.java)
    
    // Default retry policy for AWS clients
    private val defaultRetryPolicy = RetryPolicy.builder()
        .numRetries(3)
        .build()
    
    override suspend fun initialize(integration: IntegrationInstance): ConnectorResult {
        logger.info("Initializing AWS connector for integration: ${integration.id}")
        return try {
            // Validate required configuration
            val region = integration.configuration["region"] ?: "us-east-1"
            logger.debug("Using AWS region: $region")
            
            // Validate credentials
            val authResult = validateAuthentication(integration)
            if (!authResult.success) {
                logger.error("Authentication validation failed: ${authResult.message}")
                return authResult
            }
            
            // Initialize AWS clients
            val clients = createAwsClients(integration)
            clientCache[integration.id] = clients
            logger.info("AWS connector initialized successfully for integration: ${integration.id}")
            
            ConnectorResult(
                success = true,
                message = "AWS connector initialized successfully",
                details = mapOf(
                    "region" to region,
                    "authenticated" to true
                )
            )
        } catch (e: Exception) {
            logger.error("Failed to initialize AWS connector: ${e.message}", e)
            ConnectorResult(false, "Failed to initialize AWS connector: ${e.message}")
        }
    }
    
    override suspend fun reconfigure(integration: IntegrationInstance): ConnectorResult {
        // Clean up existing clients
        clientCache.remove(integration.id)
        return initialize(integration)
    }
    
    override suspend fun testConnection(integration: IntegrationInstance): ConnectorTestResult {
        return try {
            // Test AWS connection by calling STS GetCallerIdentity
            val region = integration.configuration["region"] ?: "us-east-1"
            
            // Get or create AWS clients
            val clients = clientCache[integration.id] ?: createAwsClients(integration)
            
            // Call STS GetCallerIdentity to verify credentials
            val request = GetCallerIdentityRequest.builder().build()
            val response = clients.stsClient.getCallerIdentity(request).await()
            
            ConnectorTestResult(
                success = true,
                message = "AWS connection successful",
                details = mapOf(
                    "accountId" to response.account(),
                    "userId" to response.userId(),
                    "arn" to response.arn(),
                    "region" to region
                )
            )
        } catch (e: Exception) {
            ConnectorTestResult(false, "AWS connection test failed: ${e.message}")
        }
    }
    
    override suspend fun executeOperation(
        integration: IntegrationInstance,
        operation: String,
        parameters: Map<String, Any>
    ): ConnectorOperationResult {
        return when (operation) {
            "listEC2Instances" -> listEC2Instances(integration, parameters)
            "startEC2Instance" -> startEC2Instance(integration, parameters)
            "stopEC2Instance" -> stopEC2Instance(integration, parameters)
            "listS3Buckets" -> listS3Buckets(integration, parameters)
            "createS3Bucket" -> createS3Bucket(integration, parameters)
            "listS3Objects" -> listS3Objects(integration, parameters)
            "uploadS3Object" -> uploadS3Object(integration, parameters)
            "listLambdaFunctions" -> listLambdaFunctions(integration, parameters)
            "invokeLambdaFunction" -> invokeLambdaFunction(integration, parameters)
            "getCloudWatchMetrics" -> getCloudWatchMetrics(integration, parameters)
            else -> ConnectorOperationResult(
                success = false,
                message = "Unsupported operation: $operation"
            )
        }
    }
    
    override suspend fun cleanup(integration: IntegrationInstance) {
        // Close AWS clients
        clientCache[integration.id]?.close()
        clientCache.remove(integration.id)
    }
    
    override fun getSupportedOperations(): List<ConnectorOperation> {
        return listOf(
            ConnectorOperation(
                name = "listEC2Instances",
                description = "List EC2 instances",
                parameters = listOf(
                    OperationParameter("state", "String", false, "Instance state filter", ""),
                    OperationParameter("maxResults", "Int", false, "Maximum results", 50)
                ),
                returnType = "List<EC2Instance>"
            ),
            ConnectorOperation(
                name = "startEC2Instance",
                description = "Start an EC2 instance",
                parameters = listOf(
                    OperationParameter("instanceId", "String", true, "EC2 instance ID")
                ),
                returnType = "EC2InstanceState"
            ),
            ConnectorOperation(
                name = "stopEC2Instance",
                description = "Stop an EC2 instance",
                parameters = listOf(
                    OperationParameter("instanceId", "String", true, "EC2 instance ID")
                ),
                returnType = "EC2InstanceState"
            ),
            ConnectorOperation(
                name = "listS3Buckets",
                description = "List S3 buckets",
                parameters = emptyList(),
                returnType = "List<S3Bucket>"
            ),
            ConnectorOperation(
                name = "createS3Bucket",
                description = "Create an S3 bucket",
                parameters = listOf(
                    OperationParameter("bucketName", "String", true, "S3 bucket name"),
                    OperationParameter("region", "String", false, "AWS region")
                ),
                returnType = "S3Bucket"
            ),
            ConnectorOperation(
                name = "listS3Objects",
                description = "List objects in an S3 bucket",
                parameters = listOf(
                    OperationParameter("bucketName", "String", true, "S3 bucket name"),
                    OperationParameter("prefix", "String", false, "Object key prefix", ""),
                    OperationParameter("maxKeys", "Int", false, "Maximum keys", 1000)
                ),
                returnType = "List<S3Object>"
            ),
            ConnectorOperation(
                name = "listLambdaFunctions",
                description = "List Lambda functions",
                parameters = listOf(
                    OperationParameter("maxItems", "Int", false, "Maximum items", 50)
                ),
                returnType = "List<LambdaFunction>"
            ),
            ConnectorOperation(
                name = "invokeLambdaFunction",
                description = "Invoke a Lambda function",
                parameters = listOf(
                    OperationParameter("functionName", "String", true, "Lambda function name"),
                    OperationParameter("payload", "String", false, "Function payload", "{}"),
                    OperationParameter("invocationType", "String", false, "Invocation type", "RequestResponse")
                ),
                returnType = "LambdaInvocationResult"
            )
        )
    }
    
    // Private helper methods
    
    private fun validateAuthentication(integration: IntegrationInstance): ConnectorResult {
        return when (integration.credentials.type) {
            CredentialType.AWS_CREDENTIALS -> {
                if (integration.credentials.encryptedData.isBlank()) {
                    ConnectorResult(false, "AWS access key and secret key are required")
                } else {
                    ConnectorResult(true, "AWS credentials configured")
                }
            }
            else -> {
                ConnectorResult(false, "Unsupported authentication type: ${integration.credentials.type}")
            }
        }
    }
    
    private fun createAwsClients(integration: IntegrationInstance): AwsClients {
        logger.debug("Creating AWS clients for integration: ${integration.id}")
        
        // Decrypt the credentials using a secure method
        // In a production environment, this would use a proper secrets manager or KMS
        val credentialsJson = decryptCredentials(integration.credentials)
        val awsCredentials = json.decodeFromString<AwsCredentialsConfig>(credentialsJson)
        
        val region = Region.of(integration.configuration["region"] ?: "us-east-1")
        val credentials = AwsBasicCredentials.create(awsCredentials.accessKeyId, awsCredentials.secretAccessKey)
        val credentialsProvider = StaticCredentialsProvider.create(credentials)
        
        // Common client configuration
        val timeout = Duration.ofSeconds(30)
        
        val ec2Client = Ec2AsyncClient.builder()
            .region(region)
            .credentialsProvider(credentialsProvider)
            .overrideConfiguration { config ->
                config.retryPolicy(defaultRetryPolicy)
                config.apiCallTimeout(timeout)
                config.apiCallAttemptTimeout(timeout)
            }
            .build()
            
        val s3Client = S3AsyncClient.builder()
            .region(region)
            .credentialsProvider(credentialsProvider)
            .overrideConfiguration { config ->
                config.retryPolicy(defaultRetryPolicy)
                config.apiCallTimeout(timeout)
                config.apiCallAttemptTimeout(timeout)
            }
            .build()
            
        val lambdaClient = LambdaAsyncClient.builder()
            .region(region)
            .credentialsProvider(credentialsProvider)
            .overrideConfiguration { config ->
                config.retryPolicy(defaultRetryPolicy)
                config.apiCallTimeout(timeout)
                config.apiCallAttemptTimeout(timeout)
            }
            .build()
            
        val stsClient = StsAsyncClient.builder()
            .region(region)
            .credentialsProvider(credentialsProvider)
            .overrideConfiguration { config ->
                config.retryPolicy(defaultRetryPolicy)
                config.apiCallTimeout(timeout)
                config.apiCallAttemptTimeout(timeout)
            }
            .build()
            
        logger.debug("AWS clients created successfully for integration: ${integration.id}")
        return AwsClients(ec2Client, s3Client, lambdaClient, stsClient)
    }
    
    /**
     * Decrypt credentials using a secure method
     * In a production environment, this would use a proper secrets manager or KMS
     */
    private fun decryptCredentials(credentials: IntegrationCredentials): String {
        // In a real implementation, this would use a secure decryption method
        // For now, we'll just return the encrypted data as-is
        logger.debug("Decrypting credentials with key ID: ${credentials.encryptionKeyId}")
        return credentials.encryptedData
    }
    
    private suspend fun listEC2Instances(
        integration: IntegrationInstance,
        parameters: Map<String, Any>
    ): ConnectorOperationResult {
        return try {
            val clients = clientCache[integration.id] ?: createAwsClients(integration)
            val state = parameters["state"] as? String ?: ""
            val maxResults = parameters["maxResults"] as? Int ?: 50
            
            val requestBuilder = DescribeInstancesRequest.builder()
            
            // Add state filter if specified
            if (state.isNotEmpty()) {
                val filter = software.amazon.awssdk.services.ec2.model.Filter.builder()
                    .name("instance-state-name")
                    .values(state)
                    .build()
                requestBuilder.filters(filter)
            }
            
            val request = requestBuilder.build()
            val response = clients.ec2Client.describeInstances(request).await()
            
            val instances = response.reservations().flatMap { reservation ->
                reservation.instances().map { instance ->
                    mapOf(
                        "instanceId" to instance.instanceId(),
                        "instanceType" to instance.instanceTypeAsString(),
                        "state" to instance.state().nameAsString(),
                        "publicIpAddress" to (instance.publicIpAddress() ?: ""),
                        "privateIpAddress" to (instance.privateIpAddress() ?: ""),
                        "launchTime" to instance.launchTime().toString()
                    )
                }
            }.take(maxResults)
            
            ConnectorOperationResult(
                success = true,
                message = "EC2 instances retrieved successfully",
                data = mapOf("instances" to instances)
            )
        } catch (e: Exception) {
            ConnectorOperationResult(false, "Failed to list EC2 instances: ${e.message}")
        }
    }
    
    private suspend fun startEC2Instance(
        integration: IntegrationInstance,
        parameters: Map<String, Any>
    ): ConnectorOperationResult {
        return try {
            val clients = clientCache[integration.id] ?: createAwsClients(integration)
            val instanceId = parameters["instanceId"] as? String
                ?: return ConnectorOperationResult(false, "Instance ID is required")
            
            val request = StartInstancesRequest.builder()
                .instanceIds(instanceId)
                .build()
                
            val response = clients.ec2Client.startInstances(request).await()
            val stateChange = response.startingInstances().firstOrNull()
                ?: return ConnectorOperationResult(false, "No instance state change information returned")
            
            ConnectorOperationResult(
                success = true,
                message = "EC2 instance start initiated",
                data = mapOf(
                    "instanceId" to stateChange.instanceId(),
                    "currentState" to stateChange.currentState().nameAsString(),
                    "previousState" to stateChange.previousState().nameAsString()
                )
            )
        } catch (e: Exception) {
            ConnectorOperationResult(false, "Failed to start EC2 instance: ${e.message}")
        }
    }
    
    private suspend fun stopEC2Instance(
        integration: IntegrationInstance,
        parameters: Map<String, Any>
    ): ConnectorOperationResult {
        return try {
            val clients = clientCache[integration.id] ?: createAwsClients(integration)
            val instanceId = parameters["instanceId"] as? String
                ?: return ConnectorOperationResult(false, "Instance ID is required")
            
            val request = StopInstancesRequest.builder()
                .instanceIds(instanceId)
                .build()
                
            val response = clients.ec2Client.stopInstances(request).await()
            val stateChange = response.stoppingInstances().firstOrNull()
                ?: return ConnectorOperationResult(false, "No instance state change information returned")
            
            ConnectorOperationResult(
                success = true,
                message = "EC2 instance stop initiated",
                data = mapOf(
                    "instanceId" to stateChange.instanceId(),
                    "currentState" to stateChange.currentState().nameAsString(),
                    "previousState" to stateChange.previousState().nameAsString()
                )
            )
        } catch (e: Exception) {
            ConnectorOperationResult(false, "Failed to stop EC2 instance: ${e.message}")
        }
    }
    
    private suspend fun listS3Buckets(
        integration: IntegrationInstance,
        parameters: Map<String, Any>
    ): ConnectorOperationResult {
        return try {
            val clients = clientCache[integration.id] ?: createAwsClients(integration)
            
            val request = ListBucketsRequest.builder().build()
            val response = clients.s3Client.listBuckets(request).await()
            
            val buckets = response.buckets().map { bucket ->
                mapOf(
                    "name" to bucket.name(),
                    "creationDate" to bucket.creationDate().toString()
                )
            }
            
            ConnectorOperationResult(
                success = true,
                message = "S3 buckets retrieved successfully",
                data = mapOf("buckets" to buckets)
            )
        } catch (e: Exception) {
            ConnectorOperationResult(false, "Failed to list S3 buckets: ${e.message}")
        }
    }
    
    private suspend fun createS3Bucket(
        integration: IntegrationInstance,
        parameters: Map<String, Any>
    ): ConnectorOperationResult {
        return try {
            val clients = clientCache[integration.id] ?: createAwsClients(integration)
            val bucketName = parameters["bucketName"] as? String
                ?: return ConnectorOperationResult(false, "Bucket name is required")
            val region = parameters["region"] as? String ?: integration.configuration["region"] ?: "us-east-1"
            
            val createBucketConfiguration = CreateBucketConfiguration.builder()
                .locationConstraint(region)
                .build()
                
            val request = CreateBucketRequest.builder()
                .bucket(bucketName)
                .createBucketConfiguration(createBucketConfiguration)
                .build()
                
            val response = clients.s3Client.createBucket(request).await()
            
            ConnectorOperationResult(
                success = true,
                message = "S3 bucket created successfully",
                data = mapOf(
                    "bucketName" to bucketName,
                    "region" to region,
                    "location" to response.location()
                )
            )
        } catch (e: Exception) {
            ConnectorOperationResult(false, "Failed to create S3 bucket: ${e.message}")
        }
    }
    
    private suspend fun listS3Objects(
        integration: IntegrationInstance,
        parameters: Map<String, Any>
    ): ConnectorOperationResult {
        return try {
            val clients = clientCache[integration.id] ?: createAwsClients(integration)
            val bucketName = parameters["bucketName"] as? String
                ?: return ConnectorOperationResult(false, "Bucket name is required")
            val prefix = parameters["prefix"] as? String ?: ""
            val maxKeys = parameters["maxKeys"] as? Int ?: 1000
            
            val request = ListObjectsV2Request.builder()
                .bucket(bucketName)
                .prefix(prefix)
                .maxKeys(maxKeys)
                .build()
                
            val response = clients.s3Client.listObjectsV2(request).await()
            
            val objects = response.contents().map { s3Object ->
                mapOf(
                    "key" to s3Object.key(),
                    "lastModified" to s3Object.lastModified().toString(),
                    "size" to s3Object.size(),
                    "storageClass" to s3Object.storageClassAsString()
                )
            }
            
            ConnectorOperationResult(
                success = true,
                message = "S3 objects retrieved successfully",
                data = mapOf(
                    "bucketName" to bucketName,
                    "objects" to objects,
                    "prefix" to prefix
                )
            )
        } catch (e: Exception) {
            ConnectorOperationResult(false, "Failed to list S3 objects: ${e.message}")
        }
    }
    
    private suspend fun uploadS3Object(
        integration: IntegrationInstance,
        parameters: Map<String, Any>
    ): ConnectorOperationResult {
        return try {
            val clients = clientCache[integration.id] ?: createAwsClients(integration)
            val bucketName = parameters["bucketName"] as? String
                ?: return ConnectorOperationResult(false, "Bucket name is required")
            val key = parameters["key"] as? String
                ?: return ConnectorOperationResult(false, "Object key is required")
            val content = parameters["content"] as? String ?: ""
            
            val requestBody = AsyncRequestBody.fromString(content)
            
            val request = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build()
                
            val response = clients.s3Client.putObject(request, requestBody).await()
            
            ConnectorOperationResult(
                success = true,
                message = "S3 object uploaded successfully",
                data = mapOf(
                    "bucketName" to bucketName,
                    "key" to key,
                    "etag" to response.eTag(),
                    "size" to content.length
                )
            )
        } catch (e: Exception) {
            ConnectorOperationResult(false, "Failed to upload S3 object: ${e.message}")
        }
    }
    
    private suspend fun listLambdaFunctions(
        integration: IntegrationInstance,
        parameters: Map<String, Any>
    ): ConnectorOperationResult {
        return try {
            val clients = clientCache[integration.id] ?: createAwsClients(integration)
            val maxItems = parameters["maxItems"] as? Int ?: 50
            
            val request = ListFunctionsRequest.builder()
                .maxItems(maxItems)
                .build()
                
            val response = clients.lambdaClient.listFunctions(request).await()
            
            val functions = response.functions().map { function ->
                mapOf(
                    "functionName" to function.functionName(),
                    "functionArn" to function.functionArn(),
                    "runtime" to function.runtime().toString(),
                    "handler" to function.handler(),
                    "codeSize" to function.codeSize(),
                    "lastModified" to function.lastModified()
                )
            }
            
            ConnectorOperationResult(
                success = true,
                message = "Lambda functions retrieved successfully",
                data = mapOf("functions" to functions)
            )
        } catch (e: Exception) {
            ConnectorOperationResult(false, "Failed to list Lambda functions: ${e.message}")
        }
    }
    
    private suspend fun invokeLambdaFunction(
        integration: IntegrationInstance,
        parameters: Map<String, Any>
    ): ConnectorOperationResult {
        return try {
            val clients = clientCache[integration.id] ?: createAwsClients(integration)
            val functionName = parameters["functionName"] as? String
                ?: return ConnectorOperationResult(false, "Function name is required")
            val payload = parameters["payload"] as? String ?: "{}"
            val invocationType = parameters["invocationType"] as? String ?: "RequestResponse"
            
            val request = InvokeRequest.builder()
                .functionName(functionName)
                .payload(SdkBytes.fromByteArray(payload.toByteArray(StandardCharsets.UTF_8)))
                .invocationType(invocationType)
                .build()
                
            val response = clients.lambdaClient.invoke(request).await()
            
            val responsePayload = if (response.payload() != null) {
                String(response.payload().asByteArray(), StandardCharsets.UTF_8)
            } else {
                ""
            }
            
            ConnectorOperationResult(
                success = true,
                message = "Lambda function invoked successfully",
                data = mapOf(
                    "functionName" to functionName,
                    "statusCode" to response.statusCode(),
                    "executedVersion" to response.executedVersion(),
                    "payload" to responsePayload,
                    "logResult" to (response.logResult() ?: "")
                )
            )
        } catch (e: Exception) {
            ConnectorOperationResult(false, "Failed to invoke Lambda function: ${e.message}")
        }
    }
    
    private suspend fun getCloudWatchMetrics(
        integration: IntegrationInstance,
        parameters: Map<String, Any>
    ): ConnectorOperationResult {
        return try {
            val clients = clientCache[integration.id] ?: createAwsClients(integration)
            val namespace = parameters["namespace"] as? String
                ?: return ConnectorOperationResult(false, "Namespace is required")
            val metricName = parameters["metricName"] as? String
                ?: return ConnectorOperationResult(false, "Metric name is required")
            val period = parameters["period"] as? Int ?: 300
            val startTime = parameters["startTime"] as? String
                ?: java.time.Instant.now().minusSeconds(3600).toString()
            val endTime = parameters["endTime"] as? String
                ?: java.time.Instant.now().toString()
            val statistic = parameters["statistic"] as? String ?: "Average"
            
            // Create CloudWatch client
            val cloudWatchClient = software.amazon.awssdk.services.cloudwatch.CloudWatchAsyncClient.builder()
                .region(Region.of(integration.configuration["region"] ?: "us-east-1"))
                .credentialsProvider(StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(
                        json.decodeFromString<AwsCredentialsConfig>(integration.credentials.encryptedData).accessKeyId,
                        json.decodeFromString<AwsCredentialsConfig>(integration.credentials.encryptedData).secretAccessKey
                    )
                ))
                .build()
            
            // Create metric request
            val metricRequest = software.amazon.awssdk.services.cloudwatch.model.GetMetricStatisticsRequest.builder()
                .namespace(namespace)
                .metricName(metricName)
                .period(period)
                .startTime(java.time.Instant.parse(startTime))
                .endTime(java.time.Instant.parse(endTime))
                .statistics(Statistic.fromValue(statistic))
                .build()
                
            // Get metric data
            val response = cloudWatchClient.getMetricStatistics(metricRequest as software.amazon.awssdk.services.cloudwatch.model.GetMetricStatisticsRequest).await()
            
            // Process datapoints
            val datapoints = response.datapoints().map { datapoint: software.amazon.awssdk.services.cloudwatch.model.Datapoint ->
                mapOf(
                    "timestamp" to datapoint.timestamp().toString(),
                    "value" to when (statistic) {
                        "Average" -> datapoint.average()
                        "Maximum" -> datapoint.maximum()
                        "Minimum" -> datapoint.minimum()
                        "Sum" -> datapoint.sum()
                        "SampleCount" -> datapoint.sampleCount()
                        else -> datapoint.average()
                    },
                    "unit" to datapoint.unit().toString()
                )
            }
            
            // Close the client
            cloudWatchClient.close()
            
            ConnectorOperationResult(
                success = true,
                message = "CloudWatch metrics retrieved successfully",
                data = mapOf(
                    "namespace" to namespace,
                    "metricName" to metricName,
                    "statistic" to statistic,
                    "period" to period,
                    "startTime" to startTime,
                    "endTime" to endTime,
                    "datapoints" to datapoints
                )
            )
        } catch (e: Exception) {
            ConnectorOperationResult(false, "Failed to retrieve CloudWatch metrics: ${e.message}")
        }
    }
    
    /**
     * Class to hold AWS clients for an integration
     */
    private class AwsClients(
        val ec2Client: Ec2AsyncClient,
        val s3Client: S3AsyncClient,
        val lambdaClient: LambdaAsyncClient,
        val stsClient: StsAsyncClient
    ) {
        fun close() {
            try {
                ec2Client.close()
                s3Client.close()
                lambdaClient.close()
                stsClient.close()
            } catch (e: Exception) {
                LoggerFactory.getLogger(AwsClients::class.java).warn("Error closing AWS clients: ${e.message}", e)
            }
        }
    }
    
    /**
     * AWS credentials configuration
     */
    private data class AwsCredentialsConfig(
        val accessKeyId: String,
        val secretAccessKey: String,
        val region: String? = null
    )
}