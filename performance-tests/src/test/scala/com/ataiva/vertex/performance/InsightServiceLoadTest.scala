package com.ataiva.vertex.performance

import io.gatling.core.Predef._
import io.gatling.http.Predef._
import scala.concurrent.duration._

/**
 * Load test for the Insight Service.
 * This test simulates various user scenarios under high load.
 */
class InsightServiceLoadTest extends Simulation {

  // Test configuration
  val baseUrl = System.getProperty("baseUrl", "http://localhost:8080")
  val testDuration = System.getProperty("duration", "5").toInt.minutes
  val rampUpTime = System.getProperty("rampUp", "1").toInt.minutes
  val maxUsers = System.getProperty("users", "1000").toInt
  val constantUsers = System.getProperty("constantUsers", "100").toInt

  // HTTP configuration
  val httpProtocol = http
    .baseUrl(baseUrl)
    .acceptHeader("application/json")
    .contentTypeHeader("application/json")
    .userAgentHeader("Gatling/Performance-Test")
    .shareConnections // Connection pooling

  // Common headers
  val headers = Map(
    "Accept" -> "application/json",
    "Content-Type" -> "application/json"
  )

  // Feeder for query parameters
  val queryFeeder = csv("queries.csv").random

  // Feeder for report parameters
  val reportFeeder = csv("reports.csv").random

  // Scenario 1: Health check
  val healthCheckScenario = scenario("Health Check")
    .exec(
      http("Health Check")
        .get("/health")
        .headers(headers)
        .check(status.is(200))
        .check(jsonPath("$.status").is("UP"))
    )
    .pause(1.seconds, 3.seconds)

  // Scenario 2: Get queries
  val getQueriesScenario = scenario("Get Queries")
    .feed(queryFeeder)
    .exec(
      http("Get Queries")
        .get("/api/v1/queries")
        .queryParam("queryType", "${queryType}")
        .queryParam("isActive", "true")
        .headers(headers)
        .check(status.is(200))
        .check(jsonPath("$[0].id").exists)
    )
    .pause(2.seconds, 5.seconds)

  // Scenario 3: Get query by ID
  val getQueryByIdScenario = scenario("Get Query By ID")
    .feed(queryFeeder)
    .exec(
      http("Get Query By ID")
        .get("/api/v1/queries/${queryId}")
        .headers(headers)
        .check(status.is(200))
        .check(jsonPath("$.id").is("${queryId}"))
    )
    .pause(1.seconds, 3.seconds)

  // Scenario 4: Get reports
  val getReportsScenario = scenario("Get Reports")
    .exec(
      http("Get Reports")
        .get("/api/v1/reports")
        .headers(headers)
        .check(status.is(200))
    )
    .pause(2.seconds, 5.seconds)

  // Scenario 5: Get report by ID
  val getReportByIdScenario = scenario("Get Report By ID")
    .feed(reportFeeder)
    .exec(
      http("Get Report By ID")
        .get("/api/v1/reports/${reportId}")
        .headers(headers)
        .check(status.is(200))
        .check(jsonPath("$.id").is("${reportId}"))
    )
    .pause(1.seconds, 3.seconds)

  // Scenario 6: Get dashboards
  val getDashboardsScenario = scenario("Get Dashboards")
    .exec(
      http("Get Dashboards")
        .get("/api/v1/dashboards")
        .headers(headers)
        .check(status.is(200))
    )
    .pause(2.seconds, 5.seconds)

  // Scenario 7: Get metrics
  val getMetricsScenario = scenario("Get Metrics")
    .exec(
      http("Get Metrics")
        .get("/api/v1/metrics")
        .headers(headers)
        .check(status.is(200))
    )
    .pause(1.seconds, 3.seconds)

  // Scenario 8: Get KPIs
  val getKpisScenario = scenario("Get KPIs")
    .exec(
      http("Get KPIs")
        .get("/api/v1/kpis")
        .headers(headers)
        .check(status.is(200))
    )
    .pause(1.seconds, 3.seconds)

  // Scenario 9: Mixed user journey
  val mixedUserJourneyScenario = scenario("Mixed User Journey")
    .feed(queryFeeder)
    .feed(reportFeeder)
    .exec(
      http("Health Check")
        .get("/health")
        .headers(headers)
        .check(status.is(200))
    )
    .pause(1.seconds)
    .exec(
      http("Get Queries")
        .get("/api/v1/queries")
        .headers(headers)
        .check(status.is(200))
    )
    .pause(2.seconds)
    .exec(
      http("Get Query By ID")
        .get("/api/v1/queries/${queryId}")
        .headers(headers)
        .check(status.is(200))
    )
    .pause(1.seconds)
    .exec(
      http("Get Reports")
        .get("/api/v1/reports")
        .headers(headers)
        .check(status.is(200))
    )
    .pause(2.seconds)
    .exec(
      http("Get Report By ID")
        .get("/api/v1/reports/${reportId}")
        .headers(headers)
        .check(status.is(200))
    )
    .pause(1.seconds)
    .exec(
      http("Get Dashboards")
        .get("/api/v1/dashboards")
        .headers(headers)
        .check(status.is(200))
    )
    .pause(2.seconds)
    .exec(
      http("Get Metrics")
        .get("/api/v1/metrics")
        .headers(headers)
        .check(status.is(200))
    )
    .pause(1.seconds)
    .exec(
      http("Get KPIs")
        .get("/api/v1/kpis")
        .headers(headers)
        .check(status.is(200))
    )

  // Load test setup
  setUp(
    // Constant load for health check
    healthCheckScenario.inject(
      constantUsersPerSec(10) during testDuration
    ),
    
    // Ramp up for get queries
    getQueriesScenario.inject(
      rampUsers(maxUsers) during rampUpTime,
      constantUsersPerSec(constantUsers) during testDuration
    ),
    
    // Ramp up for get query by ID
    getQueryByIdScenario.inject(
      rampUsers(maxUsers) during rampUpTime,
      constantUsersPerSec(constantUsers) during testDuration
    ),
    
    // Ramp up for get reports
    getReportsScenario.inject(
      rampUsers(maxUsers / 2) during rampUpTime,
      constantUsersPerSec(constantUsers / 2) during testDuration
    ),
    
    // Ramp up for get report by ID
    getReportByIdScenario.inject(
      rampUsers(maxUsers / 2) during rampUpTime,
      constantUsersPerSec(constantUsers / 2) during testDuration
    ),
    
    // Ramp up for get dashboards
    getDashboardsScenario.inject(
      rampUsers(maxUsers / 4) during rampUpTime,
      constantUsersPerSec(constantUsers / 4) during testDuration
    ),
    
    // Ramp up for get metrics
    getMetricsScenario.inject(
      rampUsers(maxUsers / 4) during rampUpTime,
      constantUsersPerSec(constantUsers / 4) during testDuration
    ),
    
    // Ramp up for get KPIs
    getKpisScenario.inject(
      rampUsers(maxUsers / 4) during rampUpTime,
      constantUsersPerSec(constantUsers / 4) during testDuration
    ),
    
    // Mixed user journey
    mixedUserJourneyScenario.inject(
      rampUsers(maxUsers / 10) during rampUpTime,
      constantUsersPerSec(constantUsers / 10) during testDuration
    )
  ).protocols(httpProtocol)
   .assertions(
     // Global assertions
     global.responseTime.mean.lt(500),    // Mean response time less than 500ms
     global.responseTime.percentile(95).lt(1000),  // 95th percentile response time less than 1s
     global.successfulRequests.percent.gt(95)      // Success rate greater than 95%
   )
}