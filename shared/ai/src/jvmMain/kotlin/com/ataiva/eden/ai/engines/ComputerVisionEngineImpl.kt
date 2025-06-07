package com.ataiva.eden.ai.engines

import com.ataiva.eden.ai.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import mu.KotlinLogging
import org.opencv.core.*
import org.opencv.imgcodecs.Imgcodecs
import org.opencv.imgproc.Imgproc
import org.opencv.objdetect.CascadeClassifier
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.util.concurrent.ConcurrentHashMap
import javax.imageio.ImageIO
import kotlin.math.*
import kotlin.random.Random

private val logger = KotlinLogging.logger {}

/**
 * Computer Vision Engine implementation using OpenCV
 * Provides infrastructure monitoring, anomaly detection, and visual analysis capabilities
 */
class ComputerVisionEngineImpl : ComputerVisionEngine {
    
    private val componentDetectors = ConcurrentHashMap<ComponentType, ComponentDetector>()
    private val anomalyDetectors = ConcurrentHashMap<String, VisualAnomalyDetector>()
    private val baselineImages = ConcurrentHashMap<String, Mat>()
    
    init {
        // Initialize OpenCV
        nu.pattern.OpenCV.loadShared()
        
        // Initialize component detectors
        initializeComponentDetectors()
        
        logger.info { "Computer Vision Engine initialized with OpenCV" }
    }
    
    override suspend fun analyzeInfrastructure(image: ImageData): InfrastructureAnalysis = withContext(Dispatchers.IO) {
        logger.info { "Analyzing infrastructure image: ${image.id}" }
        
        try {
            val mat = convertToMat(image)
            val components = detectComponents(mat)
            val healthStatus = assessInfrastructureHealth(components, mat)
            val issues = detectInfrastructureIssues(components, mat)
            val metrics = extractInfrastructureMetrics(mat, components)
            
            val overallConfidence = components.minOfOrNull { it.confidence } ?: 0.8
            
            InfrastructureAnalysis(
                imageId = image.id,
                components = components,
                healthStatus = healthStatus,
                issues = issues,
                metrics = metrics,
                confidence = overallConfidence
            )
            
        } catch (e: Exception) {
            logger.error(e) { "Failed to analyze infrastructure image: ${image.id}" }
            throw e
        }
    }
    
    override suspend fun detectVisualAnomalies(images: List<ImageData>): List<VisualAnomaly> = withContext(Dispatchers.IO) {
        logger.info { "Detecting visual anomalies in ${images.size} images" }
        
        val anomalies = mutableListOf<VisualAnomaly>()
        
        try {
            for (image in images) {
                val mat = convertToMat(image)
                val imageAnomalies = detectAnomaliesInImage(mat, image.id)
                anomalies.addAll(imageAnomalies)
            }
            
            // Cross-image anomaly detection
            val crossImageAnomalies = detectCrossImageAnomalies(images)
            anomalies.addAll(crossImageAnomalies)
            
            return@withContext anomalies.sortedByDescending { it.severity }
            
        } catch (e: Exception) {
            logger.error(e) { "Failed to detect visual anomalies" }
            throw e
        }
    }
    
    override suspend fun assessSystemHealth(visualData: VisualMonitoringData): SystemHealthAssessment = withContext(Dispatchers.IO) {
        logger.info { "Assessing system health from visual data" }
        
        try {
            val healthScores = mutableMapOf<String, Double>()
            val trends = mutableListOf<HealthTrend>()
            val predictions = mutableListOf<HealthPrediction>()
            
            // Analyze each monitoring point
            for (point in visualData.monitoringPoints) {
                val pointHealth = assessMonitoringPointHealth(point, visualData.images)
                healthScores[point.id] = pointHealth.health
                
                if (pointHealth.trend != null) {
                    trends.add(pointHealth.trend)
                }
                
                if (pointHealth.prediction != null) {
                    predictions.add(pointHealth.prediction)
                }
            }
            
            val overallHealth = healthScores.values.average()
            val recommendations = generateHealthRecommendations(healthScores, trends, predictions)
            
            SystemHealthAssessment(
                overallHealth = overallHealth,
                componentHealth = healthScores,
                trends = trends,
                predictions = predictions,
                recommendations = recommendations
            )
            
        } catch (e: Exception) {
            logger.error(e) { "Failed to assess system health" }
            throw e
        }
    }
    
    private fun initializeComponentDetectors() {
        // Initialize detectors for different component types
        componentDetectors[ComponentType.SERVER] = ServerDetector()
        componentDetectors[ComponentType.NETWORK_DEVICE] = NetworkDeviceDetector()
        componentDetectors[ComponentType.STORAGE] = StorageDetector()
        componentDetectors[ComponentType.LED_INDICATOR] = LEDDetector()
        componentDetectors[ComponentType.DISPLAY] = DisplayDetector()
        componentDetectors[ComponentType.CABLE] = CableDetector()
        componentDetectors[ComponentType.RACK] = RackDetector()
    }
    
    private fun convertToMat(image: ImageData): Mat {
        val bufferedImage = ImageIO.read(ByteArrayInputStream(image.data))
        val mat = Mat(bufferedImage.height, bufferedImage.width, CvType.CV_8UC3)
        
        // Convert BufferedImage to Mat
        val pixels = IntArray(bufferedImage.width * bufferedImage.height)
        bufferedImage.getRGB(0, 0, bufferedImage.width, bufferedImage.height, pixels, 0, bufferedImage.width)
        
        val data = ByteArray(pixels.size * 3)
        for (i in pixels.indices) {
            val pixel = pixels[i]
            data[i * 3] = ((pixel shr 16) and 0xFF).toByte()     // Red
            data[i * 3 + 1] = ((pixel shr 8) and 0xFF).toByte()  // Green
            data[i * 3 + 2] = (pixel and 0xFF).toByte()          // Blue
        }
        
        mat.put(0, 0, data)
        return mat
    }
    
    private fun detectComponents(mat: Mat): List<DetectedComponent> {
        val components = mutableListOf<DetectedComponent>()
        
        // Use each component detector
        componentDetectors.forEach { (type, detector) ->
            val detectedComponents = detector.detect(mat)
            components.addAll(detectedComponents.map { detection ->
                DetectedComponent(
                    type = type,
                    boundingBox = detection.boundingBox,
                    confidence = detection.confidence,
                    status = detection.status,
                    properties = detection.properties
                )
            })
        }
        
        return components
    }
    
    private fun assessInfrastructureHealth(components: List<DetectedComponent>, mat: Mat): InfrastructureHealth {
        val componentScores = mutableMapOf<ComponentType, Double>()
        val issues = mutableListOf<String>()
        val recommendations = mutableListOf<String>()
        
        // Assess health by component type
        ComponentType.values().forEach { type ->
            val typeComponents = components.filter { it.type == type }
            if (typeComponents.isNotEmpty()) {
                val healthScore = typeComponents.map { component ->
                    when (component.status) {
                        ComponentStatus.HEALTHY -> 1.0
                        ComponentStatus.WARNING -> 0.7
                        ComponentStatus.CRITICAL -> 0.3
                        ComponentStatus.UNKNOWN -> 0.5
                    }
                }.average()
                
                componentScores[type] = healthScore
                
                // Generate issues and recommendations
                val criticalComponents = typeComponents.filter { it.status == ComponentStatus.CRITICAL }
                if (criticalComponents.isNotEmpty()) {
                    issues.add("${criticalComponents.size} critical ${type.name.lowercase()} components detected")
                    recommendations.add("Immediate attention required for ${type.name.lowercase()} components")
                }
            }
        }
        
        val overallScore = if (componentScores.isNotEmpty()) componentScores.values.average() else 0.5
        
        return InfrastructureHealth(
            overallScore = overallScore,
            componentScores = componentScores,
            issues = issues,
            recommendations = recommendations
        )
    }
    
    private fun detectInfrastructureIssues(components: List<DetectedComponent>, mat: Mat): List<InfrastructureIssue> {
        val issues = mutableListOf<InfrastructureIssue>()
        
        // Check for component-specific issues
        components.forEach { component ->
            when (component.status) {
                ComponentStatus.CRITICAL -> {
                    issues.add(InfrastructureIssue(
                        id = "critical_${component.type.name.lowercase()}_${System.currentTimeMillis()}",
                        type = IssueType.HARDWARE_FAILURE,
                        severity = IssueSeverity.CRITICAL,
                        description = "Critical ${component.type.name.lowercase()} component detected",
                        location = component.boundingBox,
                        confidence = component.confidence
                    ))
                }
                ComponentStatus.WARNING -> {
                    issues.add(InfrastructureIssue(
                        id = "warning_${component.type.name.lowercase()}_${System.currentTimeMillis()}",
                        type = IssueType.PERFORMANCE_DEGRADATION,
                        severity = IssueSeverity.MEDIUM,
                        description = "Warning status on ${component.type.name.lowercase()} component",
                        location = component.boundingBox,
                        confidence = component.confidence
                    ))
                }
                else -> { /* No issues for healthy components */ }
            }
        }
        
        // Detect temperature issues using thermal analysis
        val thermalIssues = detectThermalIssues(mat)
        issues.addAll(thermalIssues)
        
        // Detect cable disconnection issues
        val cableIssues = detectCableIssues(components)
        issues.addAll(cableIssues)
        
        return issues
    }
    
    private fun extractInfrastructureMetrics(mat: Mat, components: List<DetectedComponent>): InfrastructureMetrics {
        val temperatureReadings = extractTemperatureReadings(mat)
        val statusLights = extractStatusLights(components)
        val cableConnections = extractCableConnections(components)
        val displayReadings = extractDisplayReadings(mat, components)
        
        return InfrastructureMetrics(
            temperatureIndicators = temperatureReadings,
            statusLights = statusLights,
            cableConnections = cableConnections,
            displayReadings = displayReadings
        )
    }
    
    private fun detectAnomaliesInImage(mat: Mat, imageId: String): List<VisualAnomaly> {
        val anomalies = mutableListOf<VisualAnomaly>()
        
        // Detect color anomalies
        val colorAnomalies = detectColorAnomalies(mat, imageId)
        anomalies.addAll(colorAnomalies)
        
        // Detect shape anomalies
        val shapeAnomalies = detectShapeAnomalies(mat, imageId)
        anomalies.addAll(shapeAnomalies)
        
        // Detect missing components
        val missingComponents = detectMissingComponents(mat, imageId)
        anomalies.addAll(missingComponents)
        
        return anomalies
    }
    
    private fun detectCrossImageAnomalies(images: List<ImageData>): List<VisualAnomaly> {
        val anomalies = mutableListOf<VisualAnomaly>()
        
        if (images.size < 2) return anomalies
        
        // Compare consecutive images for changes
        for (i in 1 until images.size) {
            val prevMat = convertToMat(images[i - 1])
            val currMat = convertToMat(images[i])
            
            val changeAnomalies = detectImageChanges(prevMat, currMat, images[i].id)
            anomalies.addAll(changeAnomalies)
        }
        
        return anomalies
    }
    
    private fun assessMonitoringPointHealth(point: MonitoringPoint, images: List<ImageData>): MonitoringPointHealth {
        val healthValues = mutableListOf<Double>()
        
        for (image in images) {
            val mat = convertToMat(image)
            val roi = extractROI(mat, point.location)
            val health = assessROIHealth(roi, point.type, point.baseline)
            healthValues.add(health)
        }
        
        val currentHealth = healthValues.lastOrNull() ?: 0.5
        val trend = calculateHealthTrend(healthValues)
        val prediction = predictHealthTrend(healthValues)
        
        return MonitoringPointHealth(
            health = currentHealth,
            trend = trend,
            prediction = prediction
        )
    }
    
    private fun detectThermalIssues(mat: Mat): List<InfrastructureIssue> {
        val issues = mutableListOf<InfrastructureIssue>()
        
        // Convert to HSV for better color analysis
        val hsvMat = Mat()
        Imgproc.cvtColor(mat, hsvMat, Imgproc.COLOR_BGR2HSV)
        
        // Detect red/orange areas that might indicate overheating
        val lowerRed = Scalar(0.0, 50.0, 50.0)
        val upperRed = Scalar(10.0, 255.0, 255.0)
        val redMask = Mat()
        Core.inRange(hsvMat, lowerRed, upperRed, redMask)
        
        // Find contours of red areas
        val contours = mutableListOf<MatOfPoint>()
        val hierarchy = Mat()
        Imgproc.findContours(redMask, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)
        
        contours.forEach { contour ->
            val area = Imgproc.contourArea(contour)
            if (area > 100) { // Minimum area threshold
                val boundingRect = Imgproc.boundingRect(contour)
                issues.add(InfrastructureIssue(
                    id = "thermal_${System.currentTimeMillis()}",
                    type = IssueType.OVERHEATING,
                    severity = IssueSeverity.HIGH,
                    description = "Potential overheating detected in thermal analysis",
                    location = BoundingBox(boundingRect.x, boundingRect.y, boundingRect.width, boundingRect.height),
                    confidence = 0.7
                ))
            }
        }
        
        return issues
    }
    
    private fun detectCableIssues(components: List<DetectedComponent>): List<InfrastructureIssue> {
        val issues = mutableListOf<InfrastructureIssue>()
        
        val cables = components.filter { it.type == ComponentType.CABLE }
        cables.forEach { cable ->
            val connected = cable.properties["connected"]?.toBoolean() ?: true
            if (!connected) {
                issues.add(InfrastructureIssue(
                    id = "cable_${System.currentTimeMillis()}",
                    type = IssueType.CABLE_DISCONNECTED,
                    severity = IssueSeverity.MEDIUM,
                    description = "Disconnected cable detected",
                    location = cable.boundingBox,
                    confidence = cable.confidence
                ))
            }
        }
        
        return issues
    }
    
    private fun extractTemperatureReadings(mat: Mat): List<TemperatureReading> {
        val readings = mutableListOf<TemperatureReading>()
        
        // Simplified thermal analysis using color temperature
        val hsvMat = Mat()
        Imgproc.cvtColor(mat, hsvMat, Imgproc.COLOR_BGR2HSV)
        
        // Sample temperature at various points
        val samplePoints = listOf(
            Point(mat.width() * 0.25, mat.height() * 0.25),
            Point(mat.width() * 0.75, mat.height() * 0.25),
            Point(mat.width() * 0.25, mat.height() * 0.75),
            Point(mat.width() * 0.75, mat.height() * 0.75)
        )
        
        samplePoints.forEachIndexed { index, point ->
            val hsvValue = hsvMat.get(point.y.toInt(), point.x.toInt())
            val temperature = estimateTemperatureFromHSV(hsvValue)
            val status = when {
                temperature > 80 -> TemperatureStatus.CRITICAL
                temperature > 60 -> TemperatureStatus.HIGH
                temperature > 40 -> TemperatureStatus.ELEVATED
                else -> TemperatureStatus.NORMAL
            }
            
            readings.add(TemperatureReading(
                location = BoundingBox(point.x.toInt() - 10, point.y.toInt() - 10, 20, 20),
                temperature = temperature,
                status = status
            ))
        }
        
        return readings
    }
    
    private fun extractStatusLights(components: List<DetectedComponent>): List<StatusLight> {
        return components.filter { it.type == ComponentType.LED_INDICATOR }
            .map { led ->
                val color = LightColor.valueOf(led.properties["color"] ?: "GREEN")
                val status = LightStatus.valueOf(led.properties["status"] ?: "ON")
                
                StatusLight(
                    location = led.boundingBox,
                    color = color,
                    status = status
                )
            }
    }
    
    private fun extractCableConnections(components: List<DetectedComponent>): List<CableConnection> {
        return components.filter { it.type == ComponentType.CABLE }
            .map { cable ->
                CableConnection(
                    location = cable.boundingBox,
                    connected = cable.properties["connected"]?.toBoolean() ?: true,
                    cableType = CableType.valueOf(cable.properties["type"] ?: "ETHERNET")
                )
            }
    }
    
    private fun extractDisplayReadings(mat: Mat, components: List<DetectedComponent>): List<DisplayReading> {
        val readings = mutableListOf<DisplayReading>()
        
        components.filter { it.type == ComponentType.DISPLAY }.forEach { display ->
            val roi = extractROI(mat, display.boundingBox)
            val text = performOCR(roi)
            
            readings.add(DisplayReading(
                location = display.boundingBox,
                text = text,
                confidence = 0.8 // OCR confidence would be calculated in real implementation
            ))
        }
        
        return readings
    }
    
    private fun detectColorAnomalies(mat: Mat, imageId: String): List<VisualAnomaly> {
        val anomalies = mutableListOf<VisualAnomaly>()
        
        // Detect unusual color distributions
        val meanColor = Core.mean(mat)
        val expectedMean = Scalar(100.0, 100.0, 100.0) // Expected server room colors
        
        val colorDistance = sqrt(
            (meanColor.`val`[0] - expectedMean.`val`[0]).pow(2) +
            (meanColor.`val`[1] - expectedMean.`val`[1]).pow(2) +
            (meanColor.`val`[2] - expectedMean.`val`[2]).pow(2)
        )
        
        if (colorDistance > 50) { // Threshold for color anomaly
            anomalies.add(VisualAnomaly(
                id = "color_${imageId}_${System.currentTimeMillis()}",
                imageId = imageId,
                type = VisualAnomalyType.COLOR_ANOMALY,
                location = BoundingBox(0, 0, mat.width(), mat.height()),
                severity = AnomalySeverity.MEDIUM,
                description = "Unusual color distribution detected",
                confidence = 0.7,
                timestamp = Instant.now()
            ))
        }
        
        return anomalies
    }
    
    private fun detectShapeAnomalies(mat: Mat, imageId: String): List<VisualAnomaly> {
        val anomalies = mutableListOf<VisualAnomaly>()
        
        // Convert to grayscale and detect edges
        val grayMat = Mat()
        Imgproc.cvtColor(mat, grayMat, Imgproc.COLOR_BGR2GRAY)
        
        val edges = Mat()
        Imgproc.Canny(grayMat, edges, 50.0, 150.0)
        
        // Find contours
        val contours = mutableListOf<MatOfPoint>()
        val hierarchy = Mat()
        Imgproc.findContours(edges, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)
        
        // Analyze contours for unusual shapes
        contours.forEach { contour ->
            val area = Imgproc.contourArea(contour)
            val perimeter = Imgproc.arcLength(MatOfPoint2f(*contour.toArray()), true)
            val circularity = 4 * PI * area / (perimeter * perimeter)
            
            // Detect highly irregular shapes
            if (area > 500 && circularity < 0.1) {
                val boundingRect = Imgproc.boundingRect(contour)
                anomalies.add(VisualAnomaly(
                    id = "shape_${imageId}_${System.currentTimeMillis()}",
                    imageId = imageId,
                    type = VisualAnomalyType.SHAPE_ANOMALY,
                    location = BoundingBox(boundingRect.x, boundingRect.y, boundingRect.width, boundingRect.height),
                    severity = AnomalySeverity.LOW,
                    description = "Irregular shape detected",
                    confidence = 0.6,
                    timestamp = Instant.now()
                ))
            }
        }
        
        return anomalies
    }
    
    private fun detectMissingComponents(mat: Mat, imageId: String): List<VisualAnomaly> {
        val anomalies = mutableListOf<VisualAnomaly>()
        
        // This would typically compare against a baseline image
        // For now, we'll detect empty spaces that should contain components
        val grayMat = Mat()
        Imgproc.cvtColor(mat, grayMat, Imgproc.COLOR_BGR2GRAY)
        
        // Detect large dark areas that might indicate missing components
        val threshold = Mat()
        Imgproc.threshold(grayMat, threshold, 30.0, 255.0, Imgproc.THRESH_BINARY_INV)
        
        val contours = mutableListOf<MatOfPoint>()
        val hierarchy = Mat()
        Imgproc.findContours(threshold, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)
        
        contours.forEach { contour ->
            val area = Imgproc.contourArea(contour)
            if (area > 1000) { // Large dark area
                val boundingRect = Imgproc.boundingRect(contour)
                anomalies.add(VisualAnomaly(
                    id = "missing_${imageId}_${System.currentTimeMillis()}",
                    imageId = imageId,
                    type = VisualAnomalyType.MISSING_COMPONENT,
                    location = BoundingBox(boundingRect.x, boundingRect.y, boundingRect.width, boundingRect.height),
                    severity = AnomalySeverity.HIGH,
                    description = "Potential missing component detected",
                    confidence = 0.5,
                    timestamp = Instant.now()
                ))
            }
        }
        
        return anomalies
    }
    
    private fun detectImageChanges(prevMat: Mat, currMat: Mat, imageId: String): List<VisualAnomaly> {
        val anomalies = mutableListOf<VisualAnomaly>()
        
        // Calculate difference between images
        val diff = Mat()
        Core.absdiff(prevMat, currMat, diff)
        
        // Convert to grayscale
        val grayDiff = Mat()
        Imgproc.cvtColor(diff, grayDiff, Imgproc.COLOR_BGR2GRAY)
        
        // Threshold to find significant changes
        val threshold = Mat()
        Imgproc.threshold(grayDiff, threshold, 30.0, 255.0, Imgproc.THRESH_BINARY)
        
        // Find contours of changes
        val contours = mutableListOf<MatOfPoint>()
        val hierarchy = Mat()
        Imgproc.findContours(threshold, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)
        
        contours.forEach { contour ->
            val area = Imgproc.contourArea(contour)
            if (area > 200) { // Significant change
                val boundingRect = Imgproc.boundingRect(contour)
                anomalies.add(VisualAnomaly(
                    id = "change_${imageId}_${System.currentTimeMillis()}",
                    imageId = imageId,
                    type = VisualAnomalyType.HARDWARE_CHANGE,
                    location = BoundingBox(boundingRect.x, boundingRect.y, boundingRect.width, boundingRect.height),
                    severity = AnomalySeverity.MEDIUM,
                    description = "Hardware change detected between images",
                    confidence = 0.8,
                    timestamp = Instant.now()
                ))
            }
        }
        
        return anomalies
    }
    
    private fun extractROI(mat: Mat, boundingBox: BoundingBox): Mat {
        val rect = Rect(boundingBox.x, boundingBox.y, boundingBox.width, boundingBox.height)
        return Mat(mat, rect)
    }
    
    private fun assessROIHealth(roi: Mat, type: MonitoringType, baseline: BaselineData): Double {
        return when (type) {
            MonitoringType.TEMPERATURE -> assessTemperatureHealth(roi, baseline)
            MonitoringType.STATUS_LIGHT -> assessStatusLightHealth(roi, baseline)
            MonitoringType.DISPLAY -> assessDisplayHealth(roi, baseline)
            MonitoringType.CABLE_CONNECTION -> assessCableHealth(roi, baseline)
            MonitoringType.GENERAL_HEALTH -> assessGeneralHealth(roi, baseline)
        }
    }
    
    private fun assessTemperatureHealth(roi: Mat, baseline: BaselineData): Double {
        val meanColor = Core.mean(roi)
        val temperature = estimateTemperatureFromHSV(meanColor.`val`)
        val normalTemp = baseline.normalValues.average()
        val maxTemp = baseline.thresholds["max"] ?: 70.0
        
        return when {
            temperature > maxTemp -> 0.2
            temperature > normalTemp * 1.2 -> 0.6
            else -> 1.0
        }
    }
    
    private fun assessStatusLightHealth(roi: Mat, baseline: BaselineData): Double {
        // Analyze color distribution to determine LED status
        val hsvMat = Mat()
        Imgproc.cvtColor(roi, hsvMat, Imgproc.COLOR_BGR2HSV)
        val meanHSV = Core.mean(hsvMat)
        
        // Green light indicates healthy, red indicates issues
        val hue = meanHSV.`val`[0]
        return when {
            hue < 30 || hue > 330 -> 0.3 // Red
            hue in 90.0..150.0 -> 1.0 // Green
            hue in 30.0..90.0 -> 0.7 // Yellow/Orange
            else -> 0.5 // Other colors
        }
    }
    
    private fun assessDisplayHealth(roi: Mat, baseline: BaselineData): Double {
        // Check if display is readable and showing expected content
        val text = performOCR(roi)
        return if (text.isNotEmpty() && text.length > 3) 1.0 else 0.3
    }
    
    private fun assessCableHealth(roi: Mat, baseline: BaselineData): Double {
        // Check for cable presence and proper connection
        val grayMat = Mat()
        Imgproc.cvtColor(roi, grayMat, Imgproc.COLOR_BGR2GRAY)
        val edges = Mat()
        Imgproc.Canny(grayMat, edges, 50.0, 150.0)
        
        val edgeCount = Core.countNonZero(edges)
        val expectedEdges = baseline.normalValues.average()
        
        return if (edgeCount > expectedEdges * 0.8) 1.0 else 0.4
    }
    
    private fun assessGeneralHealth(roi: Mat, baseline: BaselineData): Double {
        // General health assessment based on overall appearance
        val meanColor = Core.mean(roi)
        val brightness = (meanColor.`val`[0] + meanColor.`val`[1] + meanColor.`val`[2]) / 3.0
        val expectedBrightness = baseline.normalValues.average()
        
        val healthScore = 1.0 - abs(brightness - expectedBrightness) / 255.0
        return healthScore.coerceIn(0.0, 1.0)
    }
    
    private fun calculateHealthTrend(healthValues: List<Double>): HealthTrend? {
        if (healthValues.size < 3) return null
        
        val recent = healthValues.takeLast(3).average()
        val previous = healthValues.dropLast(3).takeLast(3).average()
        
        val trend = when {
            recent > previous * 1.1 -> TrendDirection.IMPROVING
            recent < previous * 0.9 -> TrendDirection.DEGRADING
            else -> TrendDirection.STABLE
        }
        
        val confidence = if (healthValues.size > 5) 0.8 else 0.6
        
        return HealthTrend(
            component = "monitoring_point",
            trend = trend,
            confidence = confidence,
            timeframe = kotlin.time.Duration.parse("1h")
        )
    }
    
    private fun predictHealthTrend(healthValues: List<Double>): HealthPrediction? {
        if (healthValues.size <
5) return null
        
        // Simple linear trend prediction
        val x = (0 until healthValues.size).map { it.toDouble() }
        val y = healthValues
        
        // Calculate linear regression
        val n = healthValues.size
        val xMean = x.average()
        val yMean = y.average()
        
        val numerator = x.zip(y) { xi, yi -> (xi - xMean) * (yi - yMean) }.sum()
        val denominator = x.map { (it - xMean).pow(2) }.sum()
        
        if (denominator == 0.0) return null
        
        val slope = numerator / denominator
        val intercept = yMean - slope * xMean
        
        // Predict health in 1 hour (assuming measurements every 10 minutes)
        val futureX = n + 6.0 // 6 measurements ahead
        val predictedHealth = (slope * futureX + intercept).coerceIn(0.0, 1.0)
        
        val confidence = if (abs(slope) < 0.01) 0.9 else 0.7 // More confident in stable predictions
        
        return HealthPrediction(
            component = "monitoring_point",
            predictedHealth = predictedHealth,
            timeHorizon = kotlin.time.Duration.parse("1h"),
            confidence = confidence
        )
    }
    
    private fun generateHealthRecommendations(
        healthScores: Map<String, Double>,
        trends: List<HealthTrend>,
        predictions: List<HealthPrediction>
    ): List<String> {
        val recommendations = mutableListOf<String>()
        
        // Check for low health scores
        healthScores.forEach { (component, health) ->
            when {
                health < 0.3 -> recommendations.add("Critical: Immediate attention required for $component")
                health < 0.6 -> recommendations.add("Warning: Monitor $component closely")
                health < 0.8 -> recommendations.add("Advisory: Consider maintenance for $component")
            }
        }
        
        // Check for degrading trends
        val degradingTrends = trends.filter { it.trend == TrendDirection.DEGRADING }
        if (degradingTrends.isNotEmpty()) {
            recommendations.add("Degrading trends detected - schedule preventive maintenance")
        }
        
        // Check for concerning predictions
        val concerningPredictions = predictions.filter { it.predictedHealth < 0.5 }
        if (concerningPredictions.isNotEmpty()) {
            recommendations.add("Predicted health decline - proactive intervention recommended")
        }
        
        return recommendations
    }
    
    private fun estimateTemperatureFromHSV(hsvValues: DoubleArray): Double {
        // Simplified temperature estimation from color
        // In reality, this would use calibrated thermal imaging
        val hue = hsvValues[0]
        val saturation = hsvValues[1]
        val value = hsvValues[2]
        
        // Map colors to temperature ranges
        return when {
            hue < 30 || hue > 330 -> 60 + (saturation / 255.0) * 40 // Red = hot
            hue in 30.0..90.0 -> 40 + (saturation / 255.0) * 20 // Yellow/Orange = warm
            hue in 90.0..150.0 -> 20 + (saturation / 255.0) * 20 // Green = normal
            else -> 15 + (value / 255.0) * 25 // Blue/Other = cool
        }
    }
    
    private fun performOCR(roi: Mat): String {
        // Simplified OCR implementation
        // In a real implementation, this would use Tesseract or similar
        val grayMat = Mat()
        Imgproc.cvtColor(roi, grayMat, Imgproc.COLOR_BGR2GRAY)
        
        // Apply threshold to improve OCR accuracy
        val threshold = Mat()
        Imgproc.threshold(grayMat, threshold, 0.0, 255.0, Imgproc.THRESH_BINARY + Imgproc.THRESH_OTSU)
        
        // For demonstration, return simulated OCR results
        val mockTexts = listOf("CPU: 45%", "MEM: 67%", "TEMP: 42°C", "STATUS: OK", "ERROR: 0")
        return mockTexts.random()
    }
}

// Helper classes and data structures

private data class ComponentDetection(
    val boundingBox: BoundingBox,
    val confidence: Double,
    val status: ComponentStatus,
    val properties: Map<String, String>
)

private data class MonitoringPointHealth(
    val health: Double,
    val trend: HealthTrend?,
    val prediction: HealthPrediction?
)

// Component Detector Interfaces and Implementations

private interface ComponentDetector {
    fun detect(mat: Mat): List<ComponentDetection>
}

/**
 * Server detector implementation using OpenCV's cascade classifier and template matching
 * for detecting server hardware in infrastructure images.
 *
 * This detector uses a combination of:
 * 1. Haar cascade classifier for initial detection of rectangular server-like objects
 * 2. Template matching for specific server models
 * 3. Feature-based detection for identifying server characteristics
 */
private class ServerDetector : ComponentDetector {
    // Configuration properties with defaults that can be overridden
    private val config = mapOf(
        "minServerWidth" to 100,
        "minServerHeight" to 30,
        "minConfidence" to 0.65,
        "templateMatchThreshold" to 0.7,
        "edgeDetectionThreshold1" to 50.0,
        "edgeDetectionThreshold2" to 150.0
    )
    
    // Pre-loaded server templates for different server models
    private val serverTemplates = mutableMapOf<String, Mat>()
    
    // Cascade classifier for server rack detection
    private val rackClassifier = CascadeClassifier()
    
    init {
        try {
            // Load server templates from resources
            // In a real implementation, these would be loaded from files
            // For now, we'll create simple template patterns
            val serverTemplate1 = Mat.zeros(60, 200, CvType.CV_8UC3)
            Imgproc.rectangle(serverTemplate1, Point(5.0, 5.0), Point(195.0, 55.0), Scalar(200.0, 200.0, 200.0), 2)
            serverTemplates["1U_server"] = serverTemplate1
            
            val serverTemplate2 = Mat.zeros(120, 200, CvType.CV_8UC3)
            Imgproc.rectangle(serverTemplate2, Point(5.0, 5.0), Point(195.0, 115.0), Scalar(200.0, 200.0, 200.0), 2)
            serverTemplates["2U_server"] = serverTemplate2
            
            // Load cascade classifier if available
            try {
                val classifierPath = System.getProperty("server.classifier.path")
                if (classifierPath != null) {
                    rackClassifier.load(classifierPath)
                    logger.info { "Loaded server rack classifier from: $classifierPath" }
                }
            } catch (e: Exception) {
                logger.warn(e) { "Failed to load server rack classifier, falling back to feature-based detection" }
            }
            
            logger.info { "ServerDetector initialized with ${serverTemplates.size} templates" }
        } catch (e: Exception) {
            logger.error(e) { "Error initializing ServerDetector" }
        }
    }
    
    override fun detect(mat: Mat): List<ComponentDetection> {
        val detections = mutableListOf<ComponentDetection>()
        
        try {
            // Convert to grayscale for processing
            val grayMat = Mat()
            Imgproc.cvtColor(mat, grayMat, Imgproc.COLOR_BGR2GRAY)
            
            // 1. Edge detection to find rectangular shapes
            val edges = Mat()
            Imgproc.Canny(
                grayMat,
                edges,
                config["edgeDetectionThreshold1"] as Double,
                config["edgeDetectionThreshold2"] as Double
            )
            
            // 2. Find contours of potential servers
            val contours = mutableListOf<MatOfPoint>()
            val hierarchy = Mat()
            Imgproc.findContours(edges, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)
            
            // 3. Filter and analyze contours
            for (contour in contours) {
                val rect = Imgproc.boundingRect(contour)
                
                // Filter by minimum size
                if (rect.width >= config["minServerWidth"] as Int &&
                    rect.height >= config["minServerHeight"] as Int) {
                    
                    // Check aspect ratio (servers are typically rectangular with specific ratios)
                    val aspectRatio = rect.width.toDouble() / rect.height
                    if (aspectRatio in 2.0..8.0) {
                        
                        // Extract ROI for further analysis
                        val roi = Mat(mat, rect)
                        
                        // Calculate confidence based on template matching
                        val confidence = calculateServerConfidence(roi)
                        
                        if (confidence >= config["minConfidence"] as Double) {
                            // Determine server status based on visual indicators
                            val status = determineServerStatus(roi)
                            
                            // Extract additional properties
                            val properties = extractServerProperties(roi)
                            
                            detections.add(ComponentDetection(
                                boundingBox = BoundingBox(rect.x, rect.y, rect.width, rect.height),
                                confidence = confidence,
                                status = status,
                                properties = properties
                            ))
                        }
                    }
                }
            }
            
            // 4. Use cascade classifier if available and no servers detected yet
            if (detections.isEmpty() && !rackClassifier.empty()) {
                val serverRects = MatOfRect()
                rackClassifier.detectMultiScale(grayMat, serverRects)
                
                for (rect in serverRects.toArray()) {
                    val roi = Mat(mat, rect)
                    val status = determineServerStatus(roi)
                    
                    detections.add(ComponentDetection(
                        boundingBox = BoundingBox(rect.x, rect.y, rect.width, rect.height),
                        confidence = 0.75, // Default confidence for classifier-based detection
                        status = status,
                        properties = mapOf("type" to "server", "detection_method" to "classifier")
                    ))
                }
            }
            
            // 5. If still no detections, fall back to template matching across the image
            if (detections.isEmpty()) {
                for ((serverType, template) in serverTemplates) {
                    val result = Mat()
                    Imgproc.matchTemplate(grayMat, template, result, Imgproc.TM_CCOEFF_NORMED)
                    
                    val threshold = config["templateMatchThreshold"] as Double
                    val matches = mutableListOf<Point>()
                    
                    for (y in 0 until result.rows()) {
                        for (x in 0 until result.cols()) {
                            val matchValue = result.get(y, x)[0]
                            if (matchValue >= threshold) {
                                matches.add(Point(x.toDouble(), y.toDouble()))
                            }
                        }
                    }
                    
                    // Non-maximum suppression to avoid duplicate detections
                    val filteredMatches = nonMaxSuppression(matches, template.width(), template.height())
                    
                    for (match in filteredMatches) {
                        val rect = Rect(match.x.toInt(), match.y.toInt(), template.width(), template.height())
                        val roi = Mat(mat, rect)
                        val status = determineServerStatus(roi)
                        
                        detections.add(ComponentDetection(
                            boundingBox = BoundingBox(rect.x, rect.y, rect.width, rect.height),
                            confidence = 0.7,
                            status = status,
                            properties = mapOf(
                                "type" to "server",
                                "server_model" to serverType,
                                "detection_method" to "template"
                            )
                        ))
                    }
                }
            }
            
            logger.debug { "Detected ${detections.size} servers in image" }
            
        } catch (e: Exception) {
            logger.error(e) { "Error in server detection" }
        }
        
        return detections
    }
    
    /**
     * Calculates confidence score for server detection based on multiple features
     */
    private fun calculateServerConfidence(roi: Mat): Double {
        // Combine multiple detection methods for higher accuracy
        var confidence = 0.0
        
        try {
            // 1. Check for horizontal lines (rack mounting rails)
            val grayRoi = Mat()
            Imgproc.cvtColor(roi, grayRoi, Imgproc.COLOR_BGR2GRAY)
            
            val lines = Mat()
            Imgproc.HoughLinesP(grayRoi, lines, 1.0, Math.PI/180, 50, 50.0, 10.0)
            
            var horizontalLineScore = 0.0
            if (lines.rows() > 0) {
                var horizontalLines = 0
                for (i in 0 until lines.rows()) {
                    val line = lines.get(i, 0)
                    val x1 = line[0]
                    val y1 = line[1]
                    val x2 = line[2]
                    val y2 = line[3]
                    
                    val angle = Math.abs(Math.atan2(y2 - y1, x2 - x1) * 180 / Math.PI)
                    if (angle < 10 || angle > 170) {
                        horizontalLines++
                    }
                }
                horizontalLineScore = horizontalLines.toDouble() / lines.rows()
            }
            
            // 2. Check for LED indicators (small bright spots)
            val ledScore = detectLEDIndicators(roi)
            
            // 3. Check for server front panel features
            val panelScore = detectServerPanelFeatures(roi)
            
            // Combine scores with different weights
            confidence = 0.4 * horizontalLineScore + 0.3 * ledScore + 0.3 * panelScore
            
            // Ensure confidence is in valid range
            confidence = confidence.coerceIn(0.0, 1.0)
            
        } catch (e: Exception) {
            logger.warn(e) { "Error calculating server confidence" }
            confidence = 0.5 // Default to medium confidence on error
        }
        
        return confidence
    }
    
    /**
     * Detects LED indicators in server image
     */
    private fun detectLEDIndicators(roi: Mat): Double {
        try {
            // Convert to HSV for better color detection
            val hsvRoi = Mat()
            Imgproc.cvtColor(roi, hsvRoi, Imgproc.COLOR_BGR2HSV)
            
            // Look for bright spots that could be LEDs
            val brightMask = Mat()
            Core.inRange(hsvRoi, Scalar(0.0, 0.0, 200.0), Scalar(180.0, 100.0, 255.0), brightMask)
            
            // Count potential LED spots
            val contours = mutableListOf<MatOfPoint>()
            Imgproc.findContours(brightMask, contours, Mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)
            
            // Filter by size (LEDs are small)
            val ledContours = contours.filter { Imgproc.contourArea(it) < 100 }
            
            return if (ledContours.isNotEmpty()) {
                // More LEDs = higher confidence, up to a reasonable limit
                Math.min(ledContours.size / 5.0, 1.0)
            } else {
                0.0
            }
        } catch (e: Exception) {
            logger.warn(e) { "Error detecting LED indicators" }
            return 0.0
        }
    }
    
    /**
     * Detects server panel features like vents, buttons, etc.
     */
    private fun detectServerPanelFeatures(roi: Mat): Double {
        try {
            // Convert to grayscale
            val grayRoi = Mat()
            Imgproc.cvtColor(roi, grayRoi, Imgproc.COLOR_BGR2GRAY)
            
            // Apply adaptive threshold to highlight features
            val threshold = Mat()
            Imgproc.adaptiveThreshold(
                grayRoi,
                threshold,
                255.0,
                Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
                Imgproc.THRESH_BINARY,
                11,
                2.0
            )
            
            // Count features using contour analysis
            val contours = mutableListOf<MatOfPoint>()
            Imgproc.findContours(threshold, contours, Mat(), Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE)
            
            // Filter contours by size and shape
            val featureContours = contours.filter {
                val area = Imgproc.contourArea(it)
                area > 20 && area < 1000
            }
            
            // Calculate feature density
            val density = featureContours.size.toDouble() / (roi.width() * roi.height()) * 10000
            
            return Math.min(density, 1.0)
        } catch (e: Exception) {
            logger.warn(e) { "Error detecting server panel features" }
            return 0.0
        }
    }
    
    /**
     * Determines server status based on visual indicators
     */
    private fun determineServerStatus(roi: Mat): ComponentStatus {
        try {
            // Convert to HSV for better color analysis
            val hsvRoi = Mat()
            Imgproc.cvtColor(roi, hsvRoi, Imgproc.COLOR_BGR2HSV)
            
            // Look for red LEDs (warning/critical)
            val redMask = Mat()
            Core.inRange(hsvRoi, Scalar(0.0, 100.0, 100.0), Scalar(10.0, 255.0, 255.0), redMask)
            val redLEDs = Core.countNonZero(redMask)
            
            // Look for green LEDs (healthy)
            val greenMask = Mat()
            Core.inRange(hsvRoi, Scalar(40.0, 100.0, 100.0), Scalar(80.0, 255.0, 255.0), greenMask)
            val greenLEDs = Core.countNonZero(greenMask)
            
            // Look for amber/yellow LEDs (warning)
            val amberMask = Mat()
            Core.inRange(hsvRoi, Scalar(20.0, 100.0, 100.0), Scalar(35.0, 255.0, 255.0), amberMask)
            val amberLEDs = Core.countNonZero(amberMask)
            
            return when {
                redLEDs > greenLEDs && redLEDs > amberLEDs -> ComponentStatus.CRITICAL
                amberLEDs > greenLEDs -> ComponentStatus.WARNING
                greenLEDs > 0 -> ComponentStatus.HEALTHY
                else -> ComponentStatus.UNKNOWN
            }
        } catch (e: Exception) {
            logger.warn(e) { "Error determining server status" }
            return ComponentStatus.UNKNOWN
        }
    }
    
    /**
     * Extracts additional properties from server image
     */
    private fun extractServerProperties(roi: Mat): Map<String, String> {
        val properties = mutableMapOf<String, String>()
        properties["type"] = "server"
        
        try {
            // Estimate server height in rack units
            val aspectRatio = roi.width().toDouble() / roi.height()
            val estimatedRackUnits = when {
                aspectRatio > 5.0 -> "1U"
                aspectRatio > 2.5 -> "2U"
                else -> "4U+"
            }
            properties["rack_units"] = estimatedRackUnits
            
            // Estimate server type based on visual features
            val grayRoi = Mat()
            Imgproc.cvtColor(roi, grayRoi, Imgproc.COLOR_BGR2GRAY)
            
            val meanBrightness = Core.mean(grayRoi).`val`[0]
            properties["brightness"] = meanBrightness.toString()
            
            // Detect if server has display panel
            val edges = Mat()
            Imgproc.Canny(grayRoi, edges, 50.0, 150.0)
            val edgeCount = Core.countNonZero(edges)
            val edgeDensity = edgeCount.toDouble() / (roi.width() * roi.height())
            
            if (edgeDensity > 0.1) {
                properties["has_display"] = "true"
            } else {
                properties["has_display"] = "false"
            }
            
        } catch (e: Exception) {
            logger.warn(e) { "Error extracting server properties" }
        }
        
        return properties
    }
    
    /**
     * Performs non-maximum suppression on template matching results
     */
    private fun nonMaxSuppression(matches: List<Point>, width: Int, height: Int): List<Point> {
        if (matches.isEmpty()) return emptyList()
        
        // Sort matches by position (top-left to bottom-right)
        val sortedMatches = matches.sortedWith(compareBy({ it.y }, { it.x }))
        val result = mutableListOf<Point>()
        
        // Keep track of suppressed points
        val suppressed = BooleanArray(sortedMatches.size)
        
        for (i in sortedMatches.indices) {
            if (suppressed[i]) continue
            
            result.add(sortedMatches[i])
            
            // Suppress nearby points
            for (j in i + 1 until sortedMatches.size) {
                val dx = Math.abs(sortedMatches[i].x - sortedMatches[j].x)
                val dy = Math.abs(sortedMatches[i].y - sortedMatches[j].y)
                
                // If points are close (within half template size), suppress the second point
                if (dx < width / 2 && dy < height / 2) {
                    suppressed[j] = true
                }
            }
        }
        
        return result
    }
}

/**
 * Network device detector implementation using OpenCV and machine learning techniques
 * for detecting network equipment like switches, routers, and firewalls.
 *
 * This detector uses:
 * 1. Feature-based detection for network device characteristics
 * 2. Port pattern recognition for identifying network interfaces
 * 3. LED pattern analysis for operational status
 */
private class NetworkDeviceDetector : ComponentDetector {
    // Configuration properties
    private val config = mapOf(
        "minDeviceWidth" to 80,
        "minDeviceHeight" to 20,
        "minConfidence" to 0.6,
        "portDetectionThreshold" to 0.65,
        "ledDetectionThreshold" to 0.7
    )
    
    // Pre-loaded network device templates
    private val deviceTemplates = mutableMapOf<String, Mat>()
    
    init {
        try {
            // Create simple templates for common network devices
            val switchTemplate = Mat.zeros(40, 200, CvType.CV_8UC3)
            // Draw port pattern (multiple small rectangles in a row)
            for (i in 0 until 8) {
                Imgproc.rectangle(
                    switchTemplate,
                    Point(10 + i * 22.0, 10.0),
                    Point(28 + i * 22.0, 25.0),
                    Scalar(200.0, 200.0, 200.0),
                    1
                )
            }
            // Add LED indicators
            for (i in 0 until 8) {
                Imgproc.circle(
                    switchTemplate,
                    Point(19 + i * 22.0, 32.0),
                    2,
                    Scalar(0.0, 255.0, 0.0),
                    -1
                )
            }
            deviceTemplates["switch"] = switchTemplate
            
            val routerTemplate = Mat.zeros(60, 180, CvType.CV_8UC3)
            // Draw typical router features
            Imgproc.rectangle(routerTemplate, Point(10.0, 10.0), Point(170.0, 50.0), Scalar(200.0, 200.0, 200.0), 1)
            // Add antenna connectors
            Imgproc.circle(routerTemplate, Point(20.0, 10.0), 5, Scalar(200.0, 200.0, 200.0), -1)
            Imgproc.circle(routerTemplate, Point(50.0, 10.0), 5, Scalar(200.0, 200.0, 200.0), -1)
            deviceTemplates["router"] = routerTemplate
            
            logger.info { "NetworkDeviceDetector initialized with ${deviceTemplates.size} templates" }
        } catch (e: Exception) {
            logger.error(e) { "Error initializing NetworkDeviceDetector" }
        }
    }
    
    override fun detect(mat: Mat): List<ComponentDetection> {
        val detections = mutableListOf<ComponentDetection>()
        
        try {
            // Convert to grayscale for processing
            val grayMat = Mat()
            Imgproc.cvtColor(mat, grayMat, Imgproc.COLOR_BGR2GRAY)
            
            // 1. Detect network ports using pattern recognition
            val portDetections = detectNetworkPorts(grayMat)
            
            // 2. Group ports into potential devices
            val deviceRegions = groupPortsIntoDevices(portDetections, mat.width(), mat.height())
            
            // 3. Analyze each potential device region
            for (region in deviceRegions) {
                // Extract region of interest
                val roi = Mat(mat, Rect(region.x, region.y, region.width, region.height))
                
                // Calculate confidence based on network device features
                val confidence = calculateNetworkDeviceConfidence(roi)
                
                if (confidence >= config["minConfidence"] as Double) {
                    // Determine device type and status
                    val (deviceType, properties) = determineDeviceType(roi)
                    val status = determineNetworkDeviceStatus(roi)
                    
                    // Add all properties
                    val allProperties = properties.toMutableMap()
                    allProperties["type"] = deviceType
                    
                    detections.add(ComponentDetection(
                        boundingBox = BoundingBox(region.x, region.y, region.width, region.height),
                        confidence = confidence,
                        status = status,
                        properties = allProperties
                    ))
                }
            }
            
            // 4. If no devices detected through port grouping, try template matching
            if (detections.isEmpty()) {
                for ((deviceType, template) in deviceTemplates) {
                    val result = Mat()
                    Imgproc.matchTemplate(grayMat, template, result, Imgproc.TM_CCOEFF_NORMED)
                    
                    val threshold = 0.7
                    val matches = mutableListOf<Point>()
                    
                    for (y in 0 until result.rows()) {
                        for (x in 0 until result.cols()) {
                            val matchValue = result.get(y, x)[0]
                            if (matchValue >= threshold) {
                                matches.add(Point(x.toDouble(), y.toDouble()))
                            }
                        }
                    }
                    
                    // Non-maximum suppression
                    val filteredMatches = nonMaxSuppression(matches, template.width(), template.height())
                    
                    for (match in filteredMatches) {
                        val rect = Rect(match.x.toInt(), match.y.toInt(), template.width(), template.height())
                        val roi = Mat(mat, rect)
                        val status = determineNetworkDeviceStatus(roi)
                        
                        detections.add(ComponentDetection(
                            boundingBox = BoundingBox(rect.x, rect.y, rect.width, rect.height),
                            confidence = 0.7,
                            status = status,
                            properties = mapOf(
                                "type" to deviceType,
                                "detection_method" to "template"
                            )
                        ))
                    }
                }
            }
            
            logger.debug { "Detected ${detections.size} network devices in image" }
            
        } catch (e: Exception) {
            logger.error(e) { "Error in network device detection" }
        }
        
        return detections
    }
    
    /**
     * Detects network ports in an image using pattern recognition
     */
    private fun detectNetworkPorts(grayMat: Mat): List<Rect> {
        val ports = mutableListOf<Rect>()
        
        try {
            // Apply adaptive threshold to highlight features
            val threshold = Mat()
            Imgproc.adaptiveThreshold(
                grayMat,
                threshold,
                255.0,
                Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
                Imgproc.THRESH_BINARY_INV,
                11,
                2.0
            )
            
            // Find contours
            val contours = mutableListOf<MatOfPoint>()
            Imgproc.findContours(threshold, contours, Mat(), Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE)
            
            // Filter contours by size and shape to find potential ports
            for (contour in contours) {
                val rect = Imgproc.boundingRect(contour)
                
                // Network ports are typically small rectangles with specific aspect ratios
                val aspectRatio = rect.width.toDouble() / rect.height
                val area = rect.width * rect.height
                
                if (area in 100.0..1000.0 && aspectRatio in 0.8..1.5) {
                    // Check if it looks like a network port
                    val roi = Mat(grayMat, rect)
                    val portConfidence = calculatePortConfidence(roi)
                    
                    if (portConfidence >= config["portDetectionThreshold"] as Double) {
                        ports.add(rect)
                    }
                }
            }
        } catch (e: Exception) {
            logger.warn(e) { "Error detecting network ports" }
        }
        
        return ports
    }
    
    /**
     * Calculates confidence that an image region contains a network port
     */
    private fun calculatePortConfidence(roi: Mat): Double {
        try {
            // Resize for consistent analysis
            val resizedRoi = Mat()
            Imgproc.resize(roi, resizedRoi, Size(20.0, 20.0))
            
            // Calculate histogram
            val hist = Mat()
            Imgproc.calcHist(
                listOf(resizedRoi),
                MatOfInt(0),
                Mat(),
                hist,
                MatOfInt(8),
                MatOfFloat(0f, 256f)
            )
            Core.normalize(hist, hist, 0.0, 1.0, Core.NORM_MINMAX)
            
            // Network ports typically have a specific brightness pattern
            // (dark center surrounded by lighter border)
            val centerBrightness = resizedRoi.get(10, 10)[0]
            val edgeBrightness = (
                resizedRoi.get(0, 0)[0] +
                resizedRoi.get(0, 19)[0] +
                resizedRoi.get(19, 0)[0] +
                resizedRoi.get(19, 19)[0]
            ) / 4.0
            
            // Calculate contrast between center and edges
            val contrast = Math.abs(centerBrightness - edgeBrightness) / 255.0
            
            // Calculate edge density
            val edges = Mat()
            Imgproc.Canny(resizedRoi, edges, 50.0, 150.0)
            val edgeCount = Core.countNonZero(edges)
            val edgeDensity = edgeCount.toDouble() / (resizedRoi.width() * resizedRoi.height())
            
            // Combine metrics
            return (0.5 * contrast + 0.5 * edgeDensity).coerceIn(0.0, 1.0)
        } catch (e: Exception) {
            logger.warn(e) { "Error calculating port confidence" }
            return 0.0
        }
    }
    
    /**
     * Groups detected ports into potential network devices
     */
    private fun groupPortsIntoDevices(ports: List<Rect>, imageWidth: Int, imageHeight: Int): List<Rect> {
        if (ports.isEmpty()) return emptyList()
        
        val devices = mutableListOf<Rect>()
        
        try {
            // Sort ports by y-coordinate (row)
            val sortedPorts = ports.sortedBy { it.y }
            
            // Group ports that are horizontally aligned (same row)
            val rows = mutableListOf<MutableList<Rect>>()
            var currentRow = mutableListOf(sortedPorts[0])
            
            for (i in 1 until sortedPorts.size) {
                val port = sortedPorts[i]
                val lastPort = currentRow.last()
                
                // If this port is in the same row (y-coordinate within threshold)
                if (Math.abs(port.y - lastPort.y) < port.height * 1.5) {
                    currentRow.add(port)
                } else {
                    // Start a new row
                    rows.add(currentRow)
                    currentRow = mutableListOf(port)
                }
            }
            rows.add(currentRow)
            
            // For each row with multiple ports, create a device region
            for (row in rows) {
                if (row.size >= 2) {
                    // Sort ports in this row by x-coordinate
                    val sortedRow = row.sortedBy { it.x }
                    
                    // Create a bounding rectangle that encompasses all ports in this row
                    val minX = sortedRow.minOf { it.x }
                    val minY = sortedRow.minOf { it.y }
                    val maxX = sortedRow.maxOf { it.x + it.width }
                    val maxY = sortedRow.maxOf { it.y + it.height }
                    
                    // Expand the region slightly to include the device body
                    val deviceRect = Rect(
                        minX - 10,
                        minY - 20,
                        maxX - minX + 20,
                        maxY - minY + 40
                    )
                    
                    // Ensure the rectangle is within image bounds
                    val boundedRect = Rect(
                        deviceRect.x.coerceIn(0, imageWidth - 1),
                        deviceRect.y.coerceIn(0, imageHeight - 1),
                        deviceRect.width.coerceAtMost(imageWidth - deviceRect.x),
                        deviceRect.height.coerceAtMost(imageHeight - deviceRect.y)
                    )
                    
                    devices.add(boundedRect)
                }
            }
        } catch (e: Exception) {
            logger.warn(e) { "Error grouping ports into devices" }
        }
        
        return devices
    }
    
    /**
     * Calculates confidence score for network device detection
     */
    private fun calculateNetworkDeviceConfidence(roi: Mat): Double {
        var confidence = 0.0
        
        try {
            // Convert to grayscale
            val grayRoi = Mat()
            Imgproc.cvtColor(roi, grayRoi, Imgproc.COLOR_BGR2GRAY)
            
            // 1. Check for port patterns
            val portScore = detectPortPattern(grayRoi)
            
            // 2. Check for LED indicators
            val ledScore = detectLEDIndicators(roi)
            
            // 3. Check for device housing features (vents, logos, etc.)
            val housingScore = detectDeviceHousingFeatures(grayRoi)
            
            // Combine scores with weights
            confidence = 0.5 * portScore + 0.3 * ledScore + 0.2 * housingScore
            confidence = confidence.coerceIn(0.0, 1.0)
            
        } catch (e: Exception) {
            logger.warn(e) { "Error calculating network device confidence" }
            confidence = 0.5 // Default to medium confidence on error
        }
        
        return confidence
    }
    
    /**
     * Detects port patterns characteristic of network devices
     */
    private fun detectPortPattern(grayRoi: Mat): Double {
        try {
            // Apply edge detection
            val edges = Mat()
            Imgproc.Canny(grayRoi, edges, 50.0, 150.0)
            
            // Find contours
            val contours = mutableListOf<MatOfPoint>()
            Imgproc.findContours(edges, contours, Mat(), Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE)
            
            // Filter for small rectangular contours (potential ports)
            val portContours = contours.filter { contour ->
                val rect = Imgproc.boundingRect(contour)
                val area = rect.width * rect.height
                val aspectRatio = rect.width.toDouble() / rect.height
                
                area in 50.0..500.0 && aspectRatio in 0.8..1.5
            }
            
            if (portContours.isEmpty()) return 0.0
            
            // Check for horizontal alignment of ports
            val portRects = portContours.map { Imgproc.boundingRect(it) }
            val sortedByX = portRects.sortedBy { it.x }
            
            var alignedPorts = 0
            for (i in 0 until sortedByX.size - 1) {
                val current = sortedByX[i]
                val next = sortedByX[i + 1]
                
                // Check if ports are horizontally aligned
                if (Math.abs(current.y - next.y) < current.height / 2) {
                    // Check if ports are evenly spaced
                    val spacing = next.x - (current.x + current.width)
                    if (spacing in 0..current.width * 2) {
                        alignedPorts++
                    }
                }
            }
            
            // Calculate score based on number of aligned ports
            return if (alignedPorts > 0) {
                Math.min(alignedPorts / 4.0, 1.0)
            } else {
                0.0
            }
        } catch (e: Exception) {
            logger.warn(e) { "Error detecting port pattern" }
            return 0.0
        }
    }
    
    /**
     * Detects LED indicators in network device image
     */
    private fun detectLEDIndicators(roi: Mat): Double {
        try {
            // Convert to HSV for better color detection
            val hsvRoi = Mat()
            Imgproc.cvtColor(roi, hsvRoi, Imgproc.COLOR_BGR2HSV)
            
            // Look for green LEDs
            val greenMask = Mat()
            Core.inRange(hsvRoi, Scalar(40.0, 100.0, 100.0), Scalar(80.0, 255.0, 255.0), greenMask)
            
            // Look for amber LEDs
            val amberMask = Mat()
            Core.inRange(hsvRoi, Scalar(20.0, 100.0, 100.0), Scalar(35.0, 255.0, 255.0), amberMask)
            
            // Combine masks
            val combinedMask = Mat()
            Core.add(greenMask, amberMask, combinedMask)
            
            // Find LED-like contours
            val contours = mutableListOf<MatOfPoint>()
            Imgproc.findContours(combinedMask, contours, Mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)
            
            // Filter by size and shape (LEDs are small and roughly circular)
            val ledContours = contours.filter { contour ->
                val area = Imgproc.contourArea(contour)
                area in 4.0..100.0
            }
            
            return if (ledContours.isNotEmpty()) {
                Math.min(ledContours.size / 3.0, 1.0)
            } else {
                0.0
            }
        } catch (e: Exception) {
            logger.warn(e) { "Error detecting LED indicators" }
            return 0.0
        }
    }
    
    /**
     * Detects features characteristic of network device housing
     */
    private fun detectDeviceHousingFeatures(grayRoi: Mat): Double {
        try {
            // Apply adaptive threshold to highlight features
            val threshold = Mat()
            Imgproc.adaptiveThreshold(
                grayRoi,
                threshold,
                255.0,
                Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
                Imgproc.THRESH_BINARY,
                11,
                2.0
            )
            
            // Calculate texture features
            val glcm = calculateGLCM(grayRoi)
            val contrast = calculateGLCMContrast(glcm)
            val homogeneity = calculateGLCMHomogeneity(glcm)
            
            // Network devices typically have specific texture patterns
            // (ventilation holes, logos, etc.)
            val textureScore = (contrast * 0.5 + homogeneity * 0.5).coerceIn(0.0, 1.0)
            
            return textureScore
        } catch (e: Exception) {
            logger.warn(e) { "Error detecting device housing features" }
            return 0.0
        }
    }
    
    /**
     * Calculates Gray-Level Co-occurrence Matrix for texture analysis
     */
    private fun calculateGLCM(grayMat: Mat): Array<DoubleArray> {
        // Simplified GLCM implementation
        val levels = 8
        val glcm = Array(levels) { DoubleArray(levels) }
        
        // Reduce gray levels
        val reducedMat = Mat()
        Core.divide(grayMat, Scalar(256.0 / levels), reducedMat)
        
        // Calculate co-occurrence matrix (horizontal direction)
        for (y in 0 until reducedMat.rows()) {
            for (x in 0 until reducedMat.cols() - 1) {
                val i = reducedMat.get(y, x)[0].toInt().coerceIn(0, levels - 1)
                val j = reducedMat.get(y, x + 1)[0].toInt().coerceIn(0, levels - 1)
                glcm[i][j]++
            }
        }
        
        // Normalize
        val sum = glcm.sumOf { row -> row.sum() }
        if (sum > 0) {
            for (i in 0 until levels) {
                for (j in 0 until levels) {
                    glcm[i][j] /= sum
                }
            }
        }
        
        return glcm
    }
    
    /**
     * Calculates contrast from GLCM
     */
    private fun calculateGLCMContrast(glcm: Array<DoubleArray>): Double {
        var contrast = 0.0
        for (i in glcm.indices) {
            for (j in glcm[i].indices) {
                contrast += glcm[i][j] * (i - j) * (i - j)
            }
        }
        return contrast
    }
    
    /**
     * Calculates homogeneity from GLCM
     */
    private fun calculateGLCMHomogeneity(glcm: Array<DoubleArray>): Double {
        var homogeneity = 0.0
        for (i in glcm.indices) {
            for (j in glcm[i].indices) {
                homogeneity += glcm[i][j] / (1 + (i - j) * (i - j))
            }
        }
        return homogeneity
    }
    
    /**
     * Determines network device type and extracts properties
     */
    private fun determineDeviceType(roi: Mat): Pair<String, Map<String, String>> {
        val properties = mutableMapOf<String, String>()
        var deviceType = "network_device"
        
        try {
            // Convert to grayscale
            val grayRoi = Mat()
            Imgproc.cvtColor(roi, grayRoi, Imgproc.COLOR_BGR2GRAY)
            
            // Count potential ports
            val portCount = countPotentialPorts(grayRoi)
            properties["port_count"] = portCount.toString()
            
            // Determine device type based on features
            deviceType = when {
                portCount >= 16 -> "switch"
                portCount >= 4 -> "router"
                else -> "network_device"
            }
            
            // Check for antenna connectors (characteristic of wireless devices)
            val antennaConnectors = detectAntennaConnectors(grayRoi)
            if (antennaConnectors > 0) {
                properties["antenna_connectors"] = antennaConnectors.toString()
                if (deviceType == "network_device") {
                    deviceType = "wireless_router"
                }
            }
            
            // Estimate device size
            val size = when {
                roi.width() > 300 -> "large"
                roi.width() > 150 -> "medium"
                else -> "small"
            }
            properties["size"] = size
            
        } catch (e: Exception) {
            logger.warn(e) { "Error determining device type" }
        }
        
        return Pair(deviceType, properties)
    }
    
    /**
     * Counts potential network ports in an image
     */
    private fun countPotentialPorts(grayMat: Mat): Int {
        try {
            // Apply adaptive threshold
            val threshold = Mat()
            Imgproc.adaptiveThreshold(
                grayMat,
                threshold,
                255.0,
                Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
                Imgproc.THRESH_BINARY_INV,
                11,
                2.0
            )
            
            // Find contours
            val contours = mutableListOf<MatOfPoint>()
            Imgproc.findContours(threshold, contours, Mat(), Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE)
            
            // Filter for port-like contours
            val portContours = contours.filter { contour ->
                val rect = Imgproc.boundingRect(contour)
                val area = rect.width * rect.height
                val aspectRatio = rect.width.toDouble() / rect.height
                
                area in 50.0..500.0 && aspectRatio in 0.8..1.5
            }
            
            return portContours.size
        } catch (e: Exception) {
            logger.warn(e) { "Error counting potential ports" }
            return 0
        }
    }
    
    /**
     * Detects antenna connectors characteristic of wireless devices
     */
    private fun detectAntennaConnectors(grayMat: Mat): Int {
        try {
            // Apply threshold to highlight features
            val threshold = Mat()
            Imgproc.threshold(grayMat, threshold, 0.0, 255.0, Imgproc.THRESH_BINARY + Imgproc.THRESH_OTSU)
            
            // Find contours
            val contours = mutableListOf<MatOfPoint>()
            Imgproc.findContours(threshold, contours, Mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)
            
            // Filter for circular contours (potential antenna connectors)
            val antennaContours = contours.filter { contour ->
                val area = Imgproc.contourArea(contour)
                if (area < 50 || area > 500) return@filter false
                
                // Check circularity
                val perimeter = Imgproc.arcLength(MatOfPoint2f(*contour.toArray()), true)
                val circularity = 4 * Math.PI * area / (perimeter * perimeter)
                
                circularity > 0.7 // Highly circular
            }
            
            return antennaContours.size
        } catch (e: Exception) {
            logger.warn(e) { "Error detecting antenna connectors" }
            return 0
        }
    }
    
    /**
     * Determines network device status based on visual indicators
     */
    private fun determineNetworkDeviceStatus(roi: Mat): ComponentStatus {
        try {
            // Convert to HSV for better color analysis
            val hsvRoi = Mat()
            Imgproc.cvtColor(roi, hsvRoi, Imgproc.COLOR_BGR2HSV)
            
            // Look for green LEDs (healthy)
            val greenMask = Mat()
            Core.inRange(hsvRoi, Scalar(40.0, 100.0, 100.0), Scalar(80.0, 255.0, 255.0), greenMask)
            val greenLEDs = Core.countNonZero(greenMask)
            
            // Look for amber/yellow LEDs (warning)
            val amberMask = Mat()
            Core.inRange(hsvRoi, Scalar(20.0, 100.0, 100.0), Scalar(35.0, 255.0, 255.0), amberMask)
            val amberLEDs = Core.countNonZero(amberMask)
            
            // Look for red LEDs (critical)
            val redMask = Mat()
            Core.inRange(hsvRoi, Scalar(0.0, 100.0, 100.0), Scalar(10.0, 255.0, 255.0), redMask)
            val redLEDs = Core.countNonZero(redMask)
            
            return when {
                redLEDs > greenLEDs && redLEDs > amberLEDs -> ComponentStatus.CRITICAL
                amberLEDs > greenLEDs -> ComponentStatus.WARNING
                greenLEDs > 0 -> ComponentStatus.HEALTHY
                else -> ComponentStatus.UNKNOWN
            }
        } catch (e: Exception) {
            logger.warn(e) { "Error determining network device status" }
            return ComponentStatus.UNKNOWN
        }
    }
    
    /**
     * Performs non-maximum suppression on template matching results
     */
    private fun nonMaxSuppression(matches: List<Point>, width: Int, height: Int): List<Point> {
        if (matches.isEmpty()) return emptyList()
        
        // Sort matches by position (top-left to bottom-right)
        val sortedMatches = matches.sortedWith(compareBy({ it.y }, { it.x }))
        val result = mutableListOf<Point>()
        
        // Keep track of suppressed points
        val suppressed = BooleanArray(sortedMatches.size)
        
        for (i in sortedMatches.indices) {
            if (suppressed[i]) continue
            
            result.add(sortedMatches[i])
            
            // Suppress nearby points
            for (j in i + 1 until sortedMatches.size) {
                val dx = Math.abs(sortedMatches[i].x - sortedMatches[j].x)
                val dy = Math.abs(sortedMatches[i].y - sortedMatches[j].y)
                
                // If points are close (within half template size), suppress the second point
                if (dx < width / 2 && dy < height / 2) {
                    suppressed[j] = true
                }
            }
        }
        
        return result
    }
}

/**
 * Storage device detector implementation using OpenCV and machine learning techniques
 * for detecting storage equipment like disk arrays, NAS devices, and storage servers.
 *
 * This detector uses:
 * 1. Feature-based detection for storage device characteristics
 * 2. Disk drive pattern recognition
 * 3. LED array pattern analysis for operational status
 */
private class StorageDetector : ComponentDetector {
    // Configuration properties
    private val config = mapOf(
        "minStorageWidth" to 100,
        "minStorageHeight" to 50,
        "minConfidence" to 0.6,
        "diskPatternThreshold" to 0.7,
        "edgeDetectionThreshold1" to 50.0,
        "edgeDetectionThreshold2" to 150.0
    )
    
    // Pre-loaded storage device templates
    private val storageTemplates = mutableMapOf<String, Mat>()
    
    init {
        try {
            // Create simple templates for common storage devices
            val diskArrayTemplate = Mat.zeros(100, 200, CvType.CV_8UC3)
            // Draw disk drive pattern (multiple rectangles in a grid)
            for (row in 0 until 3) {
                for (col in 0 until 4) {
                    Imgproc.rectangle(
                        diskArrayTemplate,
                        Point(10 + col * 45.0, 10 + row * 30.0),
                        Point(45 + col * 45.0, 30 + row * 30.0),
                        Scalar(200.0, 200.0, 200.0),
                        1
                    )
                }
            }
            storageTemplates["disk_array"] = diskArrayTemplate
            
            val nasTemplate = Mat.zeros(80, 160, CvType.CV_8UC3)
            // Draw NAS device features
            Imgproc.rectangle(nasTemplate, Point(10.0, 10.0), Point(150.0, 70.0), Scalar(200.0, 200.0, 200.0), 1)
            // Add disk slots
            for (i in 0 until 2) {
                Imgproc.rectangle(
                    nasTemplate,
                    Point(20.0, 20 + i * 25.0),
                    Point(140.0, 40 + i * 25.0),
                    Scalar(180.0, 180.0, 180.0),
                    1
                )
            }
            storageTemplates["nas"] = nasTemplate
            
            logger.info { "StorageDetector initialized with ${storageTemplates.size} templates" }
        } catch (e: Exception) {
            logger.error(e) { "Error initializing StorageDetector" }
        }
    }
    
    override fun detect(mat: Mat): List<ComponentDetection> {
        val detections = mutableListOf<ComponentDetection>()
        
        try {
            // Convert to grayscale for processing
            val grayMat = Mat()
            Imgproc.cvtColor(mat, grayMat, Imgproc.COLOR_BGR2GRAY)
            
            // 1. Edge detection to find rectangular shapes
            val edges = Mat()
            Imgproc.Canny(
                grayMat,
                edges,
                config["edgeDetectionThreshold1"] as Double,
                config["edgeDetectionThreshold2"] as Double
            )
            
            // 2. Find contours of potential storage devices
            val contours = mutableListOf<MatOfPoint>()
            val hierarchy = Mat()
            Imgproc.findContours(edges, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)
            
            // 3. Filter and analyze contours
            for (contour in contours) {
                val rect = Imgproc.boundingRect(contour)
                
                // Filter by minimum size
                if (rect.width >= config["minStorageWidth"] as Int &&
                    rect.height >= config["minStorageHeight"] as Int) {
                    
                    // Check aspect ratio (storage devices typically have specific ratios)
                    val aspectRatio = rect.width.toDouble() / rect.height
                    if (aspectRatio in 1.0..4.0) {
                        
                        // Extract ROI for further analysis
                        val roi = Mat(mat, rect)
                        
                        // Calculate confidence based on storage device features
                        val confidence = calculateStorageConfidence(roi)
                        
                        if (confidence >= config["minConfidence"] as Double) {
                            // Determine storage type and status
                            val (storageType, properties) = determineStorageType(roi)
                            val status = determineStorageStatus(roi)
                            
                            // Add all properties
                            val allProperties = properties.toMutableMap()
                            allProperties["type"] = storageType
                            
                            detections.add(ComponentDetection(
                                boundingBox = BoundingBox(rect.x, rect.y, rect.width, rect.height),
                                confidence = confidence,
                                status = status,
                                properties = allProperties
                            ))
                        }
                    }
                }
            }
            
            // 4. If no devices detected through contour analysis, try template matching
            if (detections.isEmpty()) {
                for ((storageType, template) in storageTemplates) {
                    val result = Mat()
                    Imgproc.matchTemplate(grayMat, template, result, Imgproc.TM_CCOEFF_NORMED)
                    
                    val threshold = 0.7
                    val matches = mutableListOf<Point>()
                    
                    for (y in 0 until result.rows()) {
                        for (x in 0 until result.cols()) {
                            val matchValue = result.get(y, x)[0]
                            if (matchValue >= threshold) {
                                matches.add(Point(x.toDouble(), y.toDouble()))
                            }
                        }
                    }
                    
                    // Non-maximum suppression
                    val filteredMatches = nonMaxSuppression(matches, template.width(), template.height())
                    
                    for (match in filteredMatches) {
                        val rect = Rect(match.x.toInt(), match.y.toInt(), template.width(), template.height())
                        val roi = Mat(mat, rect)
                        val status = determineStorageStatus(roi)
                        
                        detections.add(ComponentDetection(
                            boundingBox = BoundingBox(rect.x, rect.y, rect.width, rect.height),
                            confidence = 0.7,
                            status = status,
                            properties = mapOf(
                                "type" to storageType,
                                "detection_method" to "template"
                            )
                        ))
                    }
                }
            }
            
            logger.debug { "Detected ${detections.size} storage devices in image" }
            
        } catch (e: Exception) {
            logger.error(e) { "Error in storage device detection" }
        }
        
        return detections
    }
    
    /**
     * Calculates confidence score for storage device detection based on multiple features
     */
    private fun calculateStorageConfidence(roi: Mat): Double {
        var confidence = 0.0
        
        try {
            // Convert to grayscale
            val grayRoi = Mat()
            Imgproc.cvtColor(roi, grayRoi, Imgproc.COLOR_BGR2GRAY)
            
            // 1. Check for disk drive patterns
            val diskScore = detectDiskPattern(grayRoi)
            
            // 2. Check for LED indicators
            val ledScore = detectLEDArray(roi)
            
            // 3. Check for storage device housing features
            val housingScore = detectStorageHousingFeatures(grayRoi)
            
            // Combine scores with weights
            confidence = 0.5 * diskScore + 0.3 * ledScore + 0.2 * housingScore
            confidence = confidence.coerceIn(0.0, 1.0)
            
        } catch (e: Exception) {
            logger.warn(e) { "Error calculating storage confidence" }
            confidence = 0.5 // Default to medium confidence on error
        }
        
        return confidence
    }
    
    /**
     * Detects disk drive patterns characteristic of storage devices
     */
    private fun detectDiskPattern(grayRoi: Mat): Double {
        try {
            // Apply adaptive threshold to highlight features
            val threshold = Mat()
            Imgproc.adaptiveThreshold(
                grayRoi,
                threshold,
                255.0,
                Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
                Imgproc.THRESH_BINARY,
                11,
                2.0
            )
            
            // Find contours
            val contours = mutableListOf<MatOfPoint>()
            Imgproc.findContours(threshold, contours, Mat(), Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE)
            
            // Filter for rectangular contours (potential disk drives)
            val diskContours = contours.filter { contour ->
                val rect = Imgproc.boundingRect(contour)
                val area = rect.width * rect.height
                val aspectRatio = rect.width.toDouble() / rect.height
                
                // Disk drives typically have specific aspect ratios
                area in 500.0..5000.0 && aspectRatio in 1.5..4.0
            }
            
            if (diskContours.isEmpty()) return 0.0
            
            // Check for grid-like arrangement of disk drives
            val diskRects = diskContours.map { Imgproc.boundingRect(it) }
            
            // Group by rows (similar y-coordinates)
            val rows = mutableMapOf<Int, MutableList<Rect>>()
            for (rect in diskRects) {
                val rowKey = (rect.y / 30) * 30 // Group by 30-pixel bands
                if (!rows.containsKey(rowKey)) {
                    rows[rowKey] = mutableListOf()
                }
                rows[rowKey]?.add(rect)
            }
            
            // Calculate score based on grid arrangement
            var gridScore = 0.0
            
            // If we have multiple rows with multiple disks each
            if (rows.size >= 2 && rows.values.count { it.size >= 2 } >= 2) {
                gridScore = 1.0
            }
            // If we have one row with multiple disks
            else if (rows.values.any { it.size >= 3 }) {
                gridScore = 0.8
            }
            // If we have at least a few disk-like rectangles
            else if (diskRects.size >= 2) {
                gridScore = 0.5
            }
            
            return gridScore
            
        } catch (e: Exception) {
            logger.warn(e) { "Error detecting disk pattern" }
            return 0.0
        }
    }
    
    /**
     * Detects LED arrays characteristic of storage devices
     */
    private fun detectLEDArray(roi: Mat): Double {
        try {
            // Convert to HSV for better color detection
            val hsvRoi = Mat()
            Imgproc.cvtColor(roi, hsvRoi, Imgproc.COLOR_BGR2HSV)
            
            // Look for green LEDs
            val greenMask = Mat()
            Core.inRange(hsvRoi, Scalar(40.0, 100.0, 100.0), Scalar(80.0, 255.0, 255.0), greenMask)
            
            // Look for amber LEDs
            val amberMask = Mat()
            Core.inRange(hsvRoi, Scalar(20.0, 100.0, 100.0), Scalar(35.0, 255.0, 255.0), amberMask)
            
            // Combine masks
            val combinedMask = Mat()
            Core.add(greenMask, amberMask, combinedMask)
            
            // Find LED-like contours
            val contours = mutableListOf<MatOfPoint>()
            Imgproc.findContours(combinedMask, contours, Mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)
            
            // Filter by size (LEDs are small)
            val ledContours = contours.filter { contour ->
                val area = Imgproc.contourArea(contour)
                area in 4.0..100.0
            }
            
            if (ledContours.isEmpty()) return 0.0
            
            // Check for horizontal or vertical alignment of LEDs
            val ledRects = ledContours.map { Imgproc.boundingRect(it) }
            
            // Sort by x and y coordinates
            val sortedByX = ledRects.sortedBy { it.x }
            val sortedByY = ledRects.sortedBy { it.y }
            
            var alignedLEDs = 0
            
            // Check horizontal alignment
            for (i in 0 until sortedByX.size - 1) {
                val current = sortedByX[i]
                val next = sortedByX[i + 1]
                
                // Check if LEDs are horizontally aligned
                if (Math.abs(current.y - next.y) < current.height * 2) {
                    // Check if LEDs are evenly spaced
                    val spacing = next.x - (current.x + current.width)
                    if (spacing in 0..current.width * 5) {
                        alignedLEDs++
                    }
                }
            }
            
            // Check vertical alignment
            for (i in 0 until sortedByY.size - 1) {
                val current = sortedByY[i]
                val next = sortedByY[i + 1]
                
                // Check if LEDs are vertically aligned
                if (Math.abs(current.x - next.x) < current.width * 2) {
                    // Check if LEDs are evenly spaced
                    val spacing = next.y - (current.y + current.height)
                    if (spacing in 0..current.height * 5) {
                        alignedLEDs++
                    }
                }
            }
            
            // Calculate score based on number of aligned LEDs
            return if (alignedLEDs > 0) {
                Math.min(alignedLEDs / 3.0, 1.0)
            } else {
                Math.min(ledContours.size / 5.0, 0.5) // Some LEDs but not aligned
            }
            
        } catch (e: Exception) {
            logger.warn(e) { "Error detecting LED array" }
            return 0.0
        }
    }
    
    /**
     * Detects features characteristic of storage device housing
     */
    private fun detectStorageHousingFeatures(grayRoi: Mat): Double {
        try {
            // Apply adaptive threshold to highlight features
            val threshold = Mat()
            Imgproc.adaptiveThreshold(
                grayRoi,
                threshold,
                255.0,
                Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
                Imgproc.THRESH_BINARY,
                11,
                2.0
            )
            
            // Find contours
            val contours = mutableListOf<MatOfPoint>()
            Imgproc.findContours(threshold, contours, Mat(), Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE)
            
            // Storage devices typically have a regular pattern of rectangular features
            val rectangularFeatures = contours.filter { contour ->
                val rect = Imgproc.boundingRect(contour)
                val area = rect.width * rect.height
                
                // Approximate the contour to a polygon
                val approxCurve = MatOfPoint2f()
                MatOfPoint2f(*contour.toArray()).convertTo(approxCurve, CvType.CV_32F)
                Imgproc.approxPolyDP(approxCurve, approxCurve, 0.04 * Imgproc.arcLength(approxCurve, true), true)
                
                // Check if it's rectangular (4 vertices)
                val isRectangular = approxCurve.total() == 4L
                
                isRectangular && area > 100
            }
            
            // Calculate feature density and regularity
            if (rectangularFeatures.isEmpty()) return 0.0
            
            // Check for regular spacing between features
            val rects = rectangularFeatures.map { Imgproc.boundingRect(it) }
            val sortedByX = rects.sortedBy { it.x }
            
            var regularSpacing = 0
            for (i in 0 until sortedByX.size - 1) {
                val current = sortedByX[i]
                val next = sortedByX[i + 1]
                
                val spacing = next.x - (current.x + current.width)
                
                // Check if this spacing is similar to previous spacings
                if (i > 0) {
                    val prevCurrent = sortedByX[i - 1]
                    val prevSpacing = current.x - (prevCurrent.x + prevCurrent.width)
                    
                    if (Math.abs(spacing - prevSpacing) < 10) {
                        regularSpacing++
                    }
                }
            }
            
            val regularityScore = if (regularSpacing > 0) {
                Math.min(regularSpacing / 2.0, 1.0)
            } else {
                0.0
            }
            
            // Calculate density score
            val density = rectangularFeatures.size.toDouble() / (grayRoi.width() * grayRoi.height()) * 10000
            val densityScore = Math.min(density / 2.0, 1.0)
            
            return (regularityScore * 0.7 + densityScore * 0.3).coerceIn(0.0, 1.0)
            
        } catch (e: Exception) {
            logger.warn(e) { "Error detecting storage housing features" }
            return 0.0
        }
    }
    
    /**
     * Determines storage device type and extracts properties
     */
    private fun determineStorageType(roi: Mat): Pair<String, Map<String, String>> {
        val properties = mutableMapOf<String, String>()
        var storageType = "storage_device"
        
        try {
            // Convert to grayscale
            val grayRoi = Mat()
            Imgproc.cvtColor(roi, grayRoi, Imgproc.COLOR_BGR2GRAY)
            
            // Count potential disk drives
            val diskCount = countPotentialDisks(grayRoi)
            properties["disk_count"] = diskCount.toString()
            
            // Determine device type based on features
            storageType = when {
                diskCount >= 8 -> "storage_array"
                diskCount >= 4 -> "nas"
                diskCount >= 1 -> "disk_enclosure"
                else -> "storage_device"
            }
            
            // Estimate storage capacity based on disk count
            // This is just a rough estimate for visualization purposes
            if (diskCount > 0) {
                val estimatedCapacity = diskCount * 10 // Assume 10TB per disk as an example
                properties["estimated_capacity_tb"] = estimatedCapacity.toString()
            }
            
            // Estimate device size
            val size = when {
                roi.width() > 300 && roi.height() > 200 -> "large"
                roi.width() > 150 || roi.height() > 100 -> "medium"
                else -> "small"
            }
            properties["size"] = size
            
            // Detect form factor
            val aspectRatio = roi.width().toDouble() / roi.height()
            val formFactor = when {
                aspectRatio > 3.0 -> "1U_rack"
                aspectRatio > 1.5 -> "2U_rack"
                aspectRatio in 0.8..1.2 -> "tower"
                else -> "custom"
            }
            properties["form_factor"] = formFactor
            
        } catch (e: Exception) {
            logger.warn(e) { "Error determining storage type" }
        }
        
        return Pair(storageType, properties)
    }
    
    /**
     * Counts potential disk drives in an image
     */
    private fun countPotentialDisks(grayMat: Mat): Int {
        try {
            // Apply adaptive threshold
            val threshold = Mat()
            Imgproc.adaptiveThreshold(
                grayMat,
                threshold,
                255.0,
                Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
                Imgproc.THRESH_BINARY,
                11,
                2.0
            )
            
            // Find contours
            val contours = mutableListOf<MatOfPoint>()
            Imgproc.findContours(threshold, contours, Mat(), Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE)
            
            // Filter for disk-like contours
            val diskContours = contours.filter { contour ->
                val rect = Imgproc.boundingRect(contour)
                val area = rect.width * rect.height
                val aspectRatio = rect.width.toDouble() / rect.height
                
                // Disk drives typically have specific aspect ratios
                area in 200.0..5000.0 && aspectRatio in 1.5..4.0
            }
            
            // Group nearby contours that might be part of the same disk
            val diskRects = diskContours.map { Imgproc.boundingRect(it) }
            val groupedDisks = mutableListOf<Rect>()
            val used = BooleanArray(diskRects.size)
            
            for (i in diskRects.indices) {
                if (used[i]) continue
                
                val current = diskRects[i]
                val merged = Rect(current)
                used[i] = true
                
                // Check for overlapping or nearby rectangles
                var mergedAny = true
                while (mergedAny) {
                    mergedAny = false
                    
                    for (j in diskRects.indices) {
                        if (used[j]) continue
                        
                        val other = diskRects[j]
                        
                        // Check if rectangles are close or overlapping
                        val dx = Math.abs(merged.x + merged.width / 2 - (other.x + other.width / 2))
                        val dy = Math.abs(merged.y + merged.height / 2 - (other.y + other.height / 2))
                        
                        if (dx < merged.width && dy < merged.height) {
                            // Merge rectangles
                            val minX = Math.min(merged.x, other.x)
                            val minY = Math.min(merged.y, other.y)
                            val maxX = Math.max(merged.x + merged.width, other.x + other.width)
                            val maxY = Math.max(merged.y + merged.height, other.y + other.height)
                            
                            merged.x = minX
                            merged.y = minY
                            merged.width = maxX - minX
                            merged.height = maxY - minY
                            
                            used[j] = true
                            mergedAny = true
                        }
                    }
                }
                
                groupedDisks.add(merged)
            }
            
            return groupedDisks.size
            
        } catch (e: Exception) {
            logger.warn(e) { "Error counting potential disks" }
            return 0
        }
    }
    
    /**
     * Determines storage device status based on visual indicators
     */
    private fun determineStorageStatus(roi: Mat): ComponentStatus {
        try {
            // Convert to HSV for better color analysis
            val hsvRoi = Mat()
            Imgproc.cvtColor(roi, hsvRoi, Imgproc.COLOR_BGR2HSV)
            
            // Look for green LEDs (healthy)
            val greenMask = Mat()
            Core.inRange(hsvRoi, Scalar(40.0, 100.0, 100.0), Scalar(80.0, 255.0, 255.0), greenMask)
            val greenLEDs = Core.countNonZero(greenMask)
            
            // Look for amber/yellow LEDs (warning)
            val amberMask = Mat()
            Core.inRange(hsvRoi, Scalar(20.0, 100.0, 100.0), Scalar(35.0, 255.0, 255.0), amberMask)
            val amberLEDs = Core.countNonZero(amberMask)
            
            // Look for red LEDs (critical)
            val redMask = Mat()
            Core.inRange(hsvRoi, Scalar(0.0, 100.0, 100.0), Scalar(10.0, 255.0, 255.0), redMask)
            val redLEDs = Core.countNonZero(redMask)
            
            return when {
                redLEDs > greenLEDs && redLEDs > amberLEDs -> ComponentStatus.CRITICAL
                amberLEDs > greenLEDs -> ComponentStatus.WARNING
                greenLEDs > 0 -> ComponentStatus.HEALTHY
                else -> ComponentStatus.UNKNOWN
            }
        } catch (e: Exception) {
            logger.warn(e) { "Error determining storage status" }
            return ComponentStatus.UNKNOWN
        }
    }
    
    /**
     * Performs non-maximum suppression on template matching results
     */
    private fun nonMaxSuppression(matches: List<Point>, width: Int, height: Int): List<Point> {
        if (matches.isEmpty()) return emptyList()
        
        // Sort matches by position (top-left to bottom-right)
        val sortedMatches = matches.sortedWith(compareBy({ it.y }, { it.x }))
        val result = mutableListOf<Point>()
        
        // Keep track of suppressed points
        val suppressed = BooleanArray(sortedMatches.size)
        
        for (i in sortedMatches.indices) {
            if (suppressed[i]) continue
            
            result.add(sortedMatches[i])
            
            // Suppress nearby points
            for (j in i + 1 until sortedMatches.size) {
                val dx = Math.abs(sortedMatches[i].x - sortedMatches[j].x)
                val dy = Math.abs(sortedMatches[i].y - sortedMatches[j].y)
                
                // If points are close (within half template size), suppress the second point
                if (dx < width / 2 && dy < height / 2) {
                    suppressed[j] = true
                }
            }
        }
        
        return result
    }
}

private class LEDDetector : ComponentDetector {
    override fun detect(mat: Mat): List<ComponentDetection> {
        val detections = mutableListOf<ComponentDetection>()
        
        // Convert to HSV for better color detection
        val hsvMat = Mat()
        Imgproc.cvtColor(mat, hsvMat, Imgproc.COLOR_BGR2HSV)
        
        // Detect green LEDs (healthy status)
        val lowerGreen = Scalar(40.0, 50.0, 50.0)
        val upperGreen = Scalar(80.0, 255.0, 255.0)
        val greenMask = Mat()
        Core.inRange(hsvMat, lowerGreen, upperGreen, greenMask)
        
        // Find contours of green areas
        val contours = mutableListOf<MatOfPoint>()
        val hierarchy = Mat()
        Imgproc.findContours(greenMask, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)
        
        contours.forEach { contour ->
            val area = Imgproc.contourArea(contour)
            if (area in 10.0..100.0) { // LED size range
                val boundingRect = Imgproc.boundingRect(contour)
                detections.add(ComponentDetection(
                    boundingBox = BoundingBox(boundingRect.x, boundingRect.y, boundingRect.width, boundingRect.height),
                    confidence = 0.9,
                    status = ComponentStatus.HEALTHY,
                    properties = mapOf(
                        "color" to "GREEN",
                        "status" to "ON"
                    )
                ))
            }
        }
        
        // Detect red LEDs (error status)
        val lowerRed = Scalar(0.0, 50.0, 50.0)
        val upperRed = Scalar(10.0, 255.0, 255.0)
        val redMask = Mat()
        Core.inRange(hsvMat, lowerRed, upperRed, redMask)
        
        val redContours = mutableListOf<MatOfPoint>()
        Imgproc.findContours(redMask, redContours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)
        
        redContours.forEach { contour ->
            val area = Imgproc.contourArea(contour)
            if (area in 10.0..100.0) {
                val boundingRect = Imgproc.boundingRect(contour)
                detections.add(ComponentDetection(
                    boundingBox = BoundingBox(boundingRect.x, boundingRect.y, boundingRect.width, boundingRect.height),
                    confidence = 0.9,
                    status = ComponentStatus.CRITICAL,
                    properties = mapOf(
                        "color" to "RED",
                        "status" to "ON"
                    )
                ))
            }
        }
        
        return detections
    }
}

private class DisplayDetector : ComponentDetector {
    override fun detect(mat: Mat): List<ComponentDetection> {
        val detections = mutableListOf<ComponentDetection>()
        
        // Detect rectangular display areas with text
        val grayMat = Mat()
        Imgproc.cvtColor(mat, grayMat, Imgproc.COLOR_BGR2GRAY)
        
        // Apply edge detection
        val edges = Mat()
        Imgproc.Canny(grayMat, edges, 50.0, 150.0)
        
        // Find rectangular contours that could be displays
        val contours = mutableListOf<MatOfPoint>()
        val hierarchy = Mat()
        Imgproc.findContours(edges, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)
        
        contours.forEach { contour ->
            val area = Imgproc.contourArea(contour)
            if (area > 500) { // Minimum display size
                val boundingRect = Imgproc.boundingRect(contour)
                val aspectRatio = boundingRect.width.toDouble() / boundingRect.height
                
                // Displays typically have certain aspect ratios
                if (aspectRatio in 1.2..2.5) {
                    detections.add(ComponentDetection(
                        boundingBox = BoundingBox(boundingRect.x, boundingRect.y, boundingRect.width, boundingRect.height),
                        confidence = 0.6,
                        status = ComponentStatus.HEALTHY,
                        properties = mapOf("type" to "lcd_display")
                    ))
                }
            }
        }
        
        return detections
    }
}

private class CableDetector : ComponentDetector {
    override fun detect(mat: Mat): List<ComponentDetection> {
        val detections = mutableListOf<ComponentDetection>()
        
        // Detect cables using line detection
        val grayMat = Mat()
        Imgproc.cvtColor(mat, grayMat, Imgproc.COLOR_BGR2GRAY)
        
        val edges = Mat()
        Imgproc.Canny(grayMat, edges, 50.0, 150.0)
        
        // Use HoughLines to detect cable-like lines
        val lines = Mat()
        Imgproc.HoughLinesP(edges, lines, 1.0, PI / 180, 50, 30.0, 10.0)
        
        for (i in 0 until lines.rows()) {
            val line = lines.get(i, 0)
            val x1 = line[0].toInt()
            val y1 = line[1].toInt()
            val x2 = line[2].toInt()
            val y2 = line[3].toInt()
            
            val length = sqrt((x2 - x1).toDouble().pow(2) + (y2 - y1).toDouble().pow(2))
            
            if (length > 50) { // Minimum cable length
                val minX = minOf(x1, x2)
                val minY = minOf(y1, y2)
                val width = abs(x2 - x1)
                val height = abs(y2 - y1)
                
                detections.add(ComponentDetection(
                    boundingBox = BoundingBox(minX, minY, maxOf(width, 10), maxOf(height, 10)),
                    confidence = 0.5,
                    status = ComponentStatus.HEALTHY,
                    properties = mapOf(
                        "type" to "ETHERNET",
                        "connected" to "true",
                        "length" to length.toString()
                    )
                ))
            }
        }
        
        return detections
    }
}

private class RackDetector : ComponentDetector {
    override fun detect(mat: Mat): List<ComponentDetection> {
        val detections = mutableListOf<ComponentDetection>()
        
        // Detect the overall rack structure
        val grayMat = Mat()
        Imgproc.cvtColor(mat, grayMat, Imgproc.COLOR_BGR2GRAY)
        
        val edges = Mat()
        Imgproc.Canny(grayMat, edges, 50.0, 150.0)
        
        val contours = mutableListOf<MatOfPoint>()
        val hierarchy = Mat()
        Imgproc.findContours(edges, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)
        
        // Find the largest rectangular contour (likely the rack frame)
        val largestContour = contours.maxByOrNull { Imgproc.contourArea(it) }
        
        if (largestContour != null) {
            val area = Imgproc.contourArea(largestContour)
            if (area > mat.width() * mat.height() * 0.3) { // At least 30% of image
                val boundingRect = Imgproc.boundingRect(largestContour)
                detections.add(ComponentDetection(
                    boundingBox = BoundingBox(boundingRect.x, boundingRect.y, boundingRect.width, boundingRect.height),
                    confidence = 0.8,
                    status = ComponentStatus.HEALTHY,
                    properties = mapOf("type" to "server_rack")
                ))
            }
        }
        
        return detections
    }
}

// Visual Anomaly Detector Interface
private interface VisualAnomalyDetector {
    fun detectAnomalies(mat: Mat, baseline: Mat?): List<VisualAnomaly>
}