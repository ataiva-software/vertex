package com.ataiva.eden.hub.connector

import com.ataiva.eden.hub.engine.*
import com.ataiva.eden.hub.model.*
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.jupiter.MockitoExtension
import software.amazon.awssdk.auth.credentials.AwsCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.core.async.AsyncResponseTransformer
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.ec2.Ec2AsyncClient
import software.amazon.awssdk.services.ec2.model.*
import software.amazon.awssdk.services.s3.S3AsyncClient
import software.amazon.awssdk.services.s3.model.*
import software.amazon.awssdk.services.lambda.LambdaAsyncClient
import software.amazon.awssdk.services.lambda.model.*
import software.amazon.awssdk.services.sts.StsAsyncClient
import software.amazon.awssdk.services.sts.model.*
import java.util.concurrent.CompletableFuture
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@ExtendWith(MockitoExtension::class)
class AwsConnectorTest {

    @Mock
    private lateinit var ec2Client: Ec2AsyncClient

    @Mock
    private lateinit var s3Client: S3AsyncClient

    @Mock
    private lateinit var lambdaClient: LambdaAsyncClient

    @Mock
    private lateinit var stsClient: StsAsyncClient

    private lateinit var connector: AwsConnector
    private lateinit var integration: IntegrationInstance
    private val json = Json { ignoreUnknownKeys = true }

    @BeforeEach
    fun setup() {
        // Create a test connector with mocked AWS clients
        connector = AwsConnector()
        
        // Create a test integration instance
        val awsCredentials = AwsCredentialsConfig(
            accessKeyId = "test-access-key",
            secretAccessKey = "test-secret-key",
            region = "us-east-1"
        )
        
        integration = IntegrationInstance(
            id = "test-integration-id",
            name = "Test AWS Integration",
            type = IntegrationType.AWS,
            description = "Test AWS integration for unit tests",
            configuration = mapOf("region" to "us-east-1"),
            credentials = IntegrationCredentials(
                type = CredentialType.AWS_CREDENTIALS,
                encryptedData = json.encodeToString(awsCredentials),
                encryptionKeyId = "test-key-id"
            ),
            status = IntegrationStatus.ACTIVE,
            userId = "test-user-id",
            organizationId = "test-org-id",
            createdAt = Clock.System.now(),
            updatedAt = Clock.System.now()
        )
        
        // Use reflection to inject mocked clients
        val field = AwsConnector::class.java.getDeclaredField("clientCache")
        field.isAccessible = true
        
        val clientCache = field.get(connector) as java.util.concurrent.ConcurrentHashMap<String, Any>
        val awsClientsClass = Class.forName("com.ataiva.eden.hub.connector.AwsConnector\$AwsClients")
        val constructor = awsClientsClass.getDeclaredConstructor(
            Ec2AsyncClient::class.java,
            S3AsyncClient::class.java,
            LambdaAsyncClient::class.java,
            StsAsyncClient::class.java
        )
        constructor.isAccessible = true
        
        val awsClients = constructor.newInstance(ec2Client, s3Client, lambdaClient, stsClient)
        clientCache[integration.id] = awsClients
    }

    @Test
    fun `test connection should return success when STS call succeeds`() = runBlocking {
        // Arrange
        val callerIdentityResponse = GetCallerIdentityResponse.builder()
            .account("123456789012")
            .arn("arn:aws:iam::123456789012:user/test-user")
            .userId("AIDACKCEVSQ6C2EXAMPLE")
            .build()
        
        val future = CompletableFuture.completedFuture(callerIdentityResponse)
        `when`(stsClient.getCallerIdentity(any<GetCallerIdentityRequest>())).thenReturn(future)
        
        // Act
        val result = connector.testConnection(integration)
        
        // Assert
        assertTrue(result is ConnectorTestResult)
        assertTrue(result.success)
        assertEquals("AWS connection successful", result.message)
        assertEquals("123456789012", result.details["accountId"])
        assertEquals("AIDACKCEVSQ6C2EXAMPLE", result.details["userId"])
    }

    @Test
    fun `test connection should return failure when STS call fails`() = runBlocking {
        // Arrange
        val future = CompletableFuture<GetCallerIdentityResponse>()
        future.completeExceptionally(RuntimeException("Invalid credentials"))
        `when`(stsClient.getCallerIdentity(any<GetCallerIdentityRequest>())).thenReturn(future)
        
        // Act
        val result = connector.testConnection(integration)
        
        // Assert
        assertTrue(result is ConnectorTestResult)
        assertFalse(result.success)
        assertTrue(result.message.contains("AWS connection test failed"))
    }

    @Test
    fun `listEC2Instances should return instances when EC2 call succeeds`() = runBlocking {
        // Arrange
        val instance1 = Instance.builder()
            .instanceId("i-1234567890abcdef0")
            .instanceType("t3.micro")
            .state(InstanceState.builder().name(InstanceStateName.RUNNING).build())
            .publicIpAddress("203.0.113.12")
            .privateIpAddress("10.0.1.12")
            .launchTime(java.time.Instant.parse("2025-01-06T10:00:00Z"))
            .build()
            
        val instance2 = Instance.builder()
            .instanceId("i-0987654321fedcba0")
            .instanceType("t3.small")
            .state(InstanceState.builder().name(InstanceStateName.STOPPED).build())
            .privateIpAddress("10.0.1.13")
            .launchTime(java.time.Instant.parse("2025-01-05T15:30:00Z"))
            .build()
            
        val reservation = Reservation.builder()
            .instances(instance1, instance2)
            .build()
            
        val describeInstancesResponse = DescribeInstancesResponse.builder()
            .reservations(reservation)
            .build()
            
        val future = CompletableFuture.completedFuture(describeInstancesResponse)
        `when`(ec2Client.describeInstances(any<DescribeInstancesRequest>())).thenReturn(future)
        
        // Act
        val result = connector.executeOperation(integration, "listEC2Instances", emptyMap())
        
        // Assert
        assertTrue(result is ConnectorOperationResult)
        assertTrue(result.success)
        assertEquals("EC2 instances retrieved successfully", result.message)
        
        @Suppress("UNCHECKED_CAST")
        val instances = result.data["instances"] as List<Map<String, Any>>
        assertEquals(2, instances.size)
        assertEquals("i-1234567890abcdef0", instances[0]["instanceId"])
        assertEquals("running", instances[0]["state"])
        assertEquals("i-0987654321fedcba0", instances[1]["instanceId"])
        assertEquals("stopped", instances[1]["state"])
    }

    @Test
    fun `startEC2Instance should return state change when EC2 call succeeds`() = runBlocking {
        // Arrange
        val instanceId = "i-1234567890abcdef0"
        val stateChange = InstanceStateChange.builder()
            .instanceId(instanceId)
            .currentState(InstanceState.builder().name(InstanceStateName.PENDING).build())
            .previousState(InstanceState.builder().name(InstanceStateName.STOPPED).build())
            .build()
            
        val startInstancesResponse = StartInstancesResponse.builder()
            .startingInstances(stateChange)
            .build()
            
        val future = CompletableFuture.completedFuture(startInstancesResponse)
        `when`(ec2Client.startInstances(any<StartInstancesRequest>())).thenReturn(future)
        
        // Act
        val result = connector.executeOperation(
            integration, 
            "startEC2Instance", 
            mapOf("instanceId" to instanceId)
        )
        
        // Assert
        assertTrue(result is ConnectorOperationResult)
        assertTrue(result.success)
        assertEquals("EC2 instance start initiated", result.message)
        assertEquals(instanceId, result.data["instanceId"])
        assertEquals("pending", result.data["currentState"])
        assertEquals("stopped", result.data["previousState"])
    }

    @Test
    fun `listS3Buckets should return buckets when S3 call succeeds`() = runBlocking {
        // Arrange
        val bucket1 = Bucket.builder()
            .name("my-app-logs")
            .creationDate(java.time.Instant.parse("2024-12-01T10:00:00Z"))
            .build()
            
        val bucket2 = Bucket.builder()
            .name("my-app-backups")
            .creationDate(java.time.Instant.parse("2024-11-15T14:30:00Z"))
            .build()
            
        val listBucketsResponse = ListBucketsResponse.builder()
            .buckets(bucket1, bucket2)
            .build()
            
        val future = CompletableFuture.completedFuture(listBucketsResponse)
        `when`(s3Client.listBuckets(any<ListBucketsRequest>())).thenReturn(future)
        
        // Act
        val result = connector.executeOperation(integration, "listS3Buckets", emptyMap())
        
        // Assert
        assertTrue(result is ConnectorOperationResult)
        assertTrue(result.success)
        assertEquals("S3 buckets retrieved successfully", result.message)
        
        @Suppress("UNCHECKED_CAST")
        val buckets = result.data["buckets"] as List<Map<String, Any>>
        assertEquals(2, buckets.size)
        assertEquals("my-app-logs", buckets[0]["name"])
        assertEquals("my-app-backups", buckets[1]["name"])
    }
}