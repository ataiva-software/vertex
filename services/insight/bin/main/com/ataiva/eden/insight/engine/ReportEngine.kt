package com.ataiva.eden.insight.engine

import com.ataiva.eden.insight.model.*
import com.ataiva.eden.insight.repository.AnalyticsQueryRepository
import com.ataiva.eden.insight.repository.QueryExecutionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.poi.ss.usermodel.CellStyle
import org.apache.poi.ss.usermodel.FillPatternType
import org.apache.poi.ss.usermodel.IndexedColors
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.slf4j.LoggerFactory
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.Paths
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVPrinter
import com.itextpdf.kernel.colors.ColorConstants
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.Cell
import com.itextpdf.layout.element.Paragraph
import com.itextpdf.layout.element.Table
import com.itextpdf.layout.property.TextAlignment
import com.itextpdf.layout.property.UnitValue
import com.itextpdf.kernel.geom.PageSize
import com.itextpdf.layout.element.Image
import com.itextpdf.io.image.ImageDataFactory
import com.itextpdf.layout.borders.Border
import com.itextpdf.layout.property.HorizontalAlignment
import org.jfree.chart.ChartFactory
import org.jfree.chart.ChartUtils
import org.jfree.chart.plot.PlotOrientation
import org.jfree.data.category.DefaultCategoryDataset
import org.jfree.data.general.DefaultPieDataset
import org.jfree.data.xy.XYSeries
import org.jfree.data.xy.XYSeriesCollection
import java.awt.Color
import java.io.ByteArrayInputStream
import java.util.*

/**
 * ReportEngine is responsible for generating reports in various formats
 * with proper formatting, styling, and data visualization.
 */
class ReportEngine(
    private val analyticsEngine: AnalyticsEngine,
    private val analyticsQueryRepository: AnalyticsQueryRepository,
    private val queryExecutionRepository: QueryExecutionRepository,
    private val reportOutputPath: String
) {
    private val logger = LoggerFactory.getLogger(ReportEngine::class.java)
    private val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    init {
        // Ensure output directory exists
        val outputDir = File(reportOutputPath)
        if (!outputDir.exists()) {
            outputDir.mkdirs()
        }
    }

    /**
     * Generate a report based on the provided template and parameters
     */
    suspend fun generateReport(
        report: Report,
        template: ReportTemplate,
        parameters: Map<String, String>,
        format: ReportFormat
    ): String = withContext(Dispatchers.IO) {
        try {
            logger.info("Generating report: ${report.name} in format: $format")
            
            // Process template to get content with parameters substituted
            val processedTemplate = processTemplate(template, parameters)
            
            // Get data for the report by executing associated queries
            val reportData = getReportData(report, template, parameters)
            
            // Generate the report in the requested format
            val outputPath = when (format) {
                ReportFormat.PDF -> generatePdfReport(report, processedTemplate, reportData)
                ReportFormat.EXCEL -> generateExcelReport(report, processedTemplate, reportData)
                ReportFormat.CSV -> generateCsvReport(report, processedTemplate, reportData)
                ReportFormat.HTML -> generateHtmlReport(report, processedTemplate, reportData)
                ReportFormat.JSON -> generateJsonReport(report, processedTemplate, reportData)
            }
            
            logger.info("Report generated successfully: $outputPath")
            return@withContext outputPath
            
        } catch (e: Exception) {
            logger.error("Error generating report: ${e.message}", e)
            throw ReportGenerationException("Failed to generate report: ${e.message}", e)
        }
    }

    /**
     * Process the template and substitute parameters
     */
    private fun processTemplate(template: ReportTemplate, parameters: Map<String, String>): ProcessedTemplate {
        var content = template.templateContent
        
        // Replace parameters in template
        parameters.forEach { (key, value) ->
            content = content.replace("{{$key}}", value)
            content = content.replace("\${$key}", value)
        }
        
        // Add timestamp
        content = content.replace("{{timestamp}}", LocalDateTime.now().format(dateTimeFormatter))
        
        // Parse template to extract sections, queries, and charts
        return parseTemplate(content)
    }
    
    /**
     * Parse the template content to extract sections, queries, and charts
     */
    private fun parseTemplate(content: String): ProcessedTemplate {
        val sections = mutableListOf<TemplateSection>()
        val queries = mutableListOf<String>()
        val charts = mutableListOf<ChartDefinition>()
        
        // Simple parsing logic - in a real implementation, this would be more robust
        val lines = content.lines()
        
        var currentSection: TemplateSection? = null
        var inSection = false
        
        for (line in lines) {
            when {
                line.startsWith("## SECTION:") -> {
                    if (currentSection != null) {
                        sections.add(currentSection)
                    }
                    val title = line.substringAfter("## SECTION:").trim()
                    currentSection = TemplateSection(title, mutableListOf())
                    inSection = true
                }
                line.startsWith("## END SECTION") -> {
                    if (currentSection != null) {
                        sections.add(currentSection)
                        currentSection = null
                    }
                    inSection = false
                }
                line.startsWith("## QUERY:") -> {
                    val queryId = line.substringAfter("## QUERY:").trim()
                    queries.add(queryId)
                }
                line.startsWith("## CHART:") -> {
                    val chartDef = line.substringAfter("## CHART:").trim().split(",")
                    if (chartDef.size >= 3) {
                        val type = chartDef[0].trim()
                        val title = chartDef[1].trim()
                        val dataSource = chartDef[2].trim()
                        charts.add(ChartDefinition(type, title, dataSource))
                    }
                }
                inSection && currentSection != null -> {
                    currentSection.content.add(line)
                }
            }
        }
        
        // Add the last section if there is one
        if (currentSection != null) {
            sections.add(currentSection)
        }
        
        return ProcessedTemplate(content, sections, queries, charts)
    }
    
    /**
     * Get data for the report by executing associated queries
     */
    private suspend fun getReportData(
        report: Report,
        template: ReportTemplate,
        parameters: Map<String, String>
    ): Map<String, List<Map<String, Any>>> {
        val data = mutableMapOf<String, List<Map<String, Any>>>()
        
        // Extract query IDs from template
        val processedTemplate = processTemplate(template, parameters)
        
        // Execute each query and store results
        for (queryId in processedTemplate.queries) {
            try {
                val query = analyticsQueryRepository.findById(queryId)
                if (query != null) {
                    val result = analyticsEngine.executeQuery(query, parameters)
                    data[queryId] = result.data
                } else {
                    logger.warn("Query not found: $queryId")
                    data[queryId] = emptyList()
                }
            } catch (e: Exception) {
                logger.error("Error executing query $queryId: ${e.message}", e)
                data[queryId] = emptyList()
            }
        }
        
        return data
    }
    
    /**
     * Generate a PDF report
     */
    private fun generatePdfReport(
        report: Report,
        template: ProcessedTemplate,
        data: Map<String, List<Map<String, Any>>>
    ): String {
        val filename = "report_${report.id}_${System.currentTimeMillis()}.pdf"
        val outputPath = File(reportOutputPath, filename).absolutePath
        
        PdfWriter(outputPath).use { writer ->
            val pdf = PdfDocument(writer)
            val document = Document(pdf, PageSize.A4)
            
            // Add title
            document.add(
                Paragraph(report.name)
                    .setFontSize(24f)
                    .setBold()
                    .setTextAlignment(TextAlignment.CENTER)
            )
            
            // Add timestamp
            document.add(
                Paragraph("Generated: ${LocalDateTime.now().format(dateTimeFormatter)}")
                    .setFontSize(10f)
                    .setTextAlignment(TextAlignment.RIGHT)
                    .setItalic()
            )
            
            if (report.description != null) {
                document.add(
                    Paragraph(report.description)
                        .setFontSize(12f)
                        .setTextAlignment(TextAlignment.CENTER)
                        .setMarginBottom(20f)
                )
            }
            
            // Process each section
            for (section in template.sections) {
                document.add(
                    Paragraph(section.title)
                        .setFontSize(18f)
                        .setBold()
                        .setMarginTop(15f)
                )
                
                // Process section content
                for (line in section.content) {
                    when {
                        line.contains("{{QUERY:") -> {
                            val queryId = line.substringAfter("{{QUERY:").substringBefore("}}").trim()
                            val queryData = data[queryId] ?: emptyList()
                            
                            if (queryData.isNotEmpty()) {
                                // Create table for query results
                                val columns = queryData.first().keys.toList()
                                val table = Table(UnitValue.createPercentArray(columns.size))
                                    .useAllAvailableWidth()
                                    .setMarginTop(10f)
                                    .setMarginBottom(10f)
                                
                                // Add header row
                                for (column in columns) {
                                    table.addHeaderCell(
                                        Cell().add(Paragraph(column))
                                            .setBackgroundColor(ColorConstants.LIGHT_GRAY)
                                            .setBold()
                                            .setTextAlignment(TextAlignment.CENTER)
                                    )
                                }
                                
                                // Add data rows
                                for (row in queryData) {
                                    for (column in columns) {
                                        val value = row[column]?.toString() ?: ""
                                        table.addCell(
                                            Cell().add(Paragraph(value))
                                                .setTextAlignment(TextAlignment.LEFT)
                                        )
                                    }
                                }
                                
                                document.add(table)
                            } else {
                                document.add(
                                    Paragraph("No data available for query: $queryId")
                                        .setItalic()
                                )
                            }
                        }
                        line.contains("{{CHART:") -> {
                            val chartDef = line.substringAfter("{{CHART:").substringBefore("}}").trim().split(",")
                            if (chartDef.size >= 3) {
                                val type = chartDef[0].trim()
                                val title = chartDef[1].trim()
                                val dataSource = chartDef[2].trim()
                                
                                val chartData = data[dataSource] ?: emptyList()
                                if (chartData.isNotEmpty()) {
                                    val chartImage = generateChart(type, title, chartData)
                                    if (chartImage != null) {
                                        val image = Image(ImageDataFactory.create(chartImage))
                                            .setWidth(UnitValue.createPercentValue(80f))
                                            .setHorizontalAlignment(HorizontalAlignment.CENTER)
                                        document.add(image)
                                    }
                                }
                            }
                        }
                        else -> {
                            document.add(Paragraph(line).setMarginBottom(5f))
                        }
                    }
                }
            }
            
            document.close()
        }
        
        return outputPath
    }
    
    /**
     * Generate an Excel report
     */
    private fun generateExcelReport(
        report: Report,
        template: ProcessedTemplate,
        data: Map<String, List<Map<String, Any>>>
    ): String {
        val filename = "report_${report.id}_${System.currentTimeMillis()}.xlsx"
        val outputPath = File(reportOutputPath, filename).absolutePath
        
        XSSFWorkbook().use { workbook ->
            // Create summary sheet
            val summarySheet = workbook.createSheet("Summary")
            var rowNum = 0
            
            // Add title
            val titleRow = summarySheet.createRow(rowNum++)
            val titleCell = titleRow.createCell(0)
            titleCell.setCellValue(report.name)
            
            val titleStyle = workbook.createCellStyle()
            val titleFont = workbook.createFont()
            titleFont.bold = true
            titleFont.fontHeightInPoints = 16
            titleStyle.setFont(titleFont)
            titleCell.cellStyle = titleStyle
            
            // Add timestamp
            val timestampRow = summarySheet.createRow(rowNum++)
            val timestampCell = timestampRow.createCell(0)
            timestampCell.setCellValue("Generated: ${LocalDateTime.now().format(dateTimeFormatter)}")
            
            // Add description if available
            if (report.description != null) {
                rowNum++ // Empty row
                val descRow = summarySheet.createRow(rowNum++)
                val descCell = descRow.createCell(0)
                descCell.setCellValue(report.description)
            }
            
            // Create sheets for each query result
            for ((queryId, queryData) in data) {
                if (queryData.isNotEmpty()) {
                    val sheet = workbook.createSheet(queryId.take(31)) // Excel sheet name limit
                    var dataRowNum = 0
                    
                    // Create header row
                    val headerRow = sheet.createRow(dataRowNum++)
                    val columns = queryData.first().keys.toList()
                    
                    val headerStyle = workbook.createCellStyle()
                    headerStyle.fillForegroundColor = IndexedColors.GREY_25_PERCENT.index
                    headerStyle.fillPattern = FillPatternType.SOLID_FOREGROUND
                    
                    val headerFont = workbook.createFont()
                    headerFont.bold = true
                    headerStyle.setFont(headerFont)
                    
                    for ((colIdx, column) in columns.withIndex()) {
                        val cell = headerRow.createCell(colIdx)
                        cell.setCellValue(column)
                        cell.cellStyle = headerStyle
                    }
                    
                    // Add data rows
                    for (row in queryData) {
                        val dataRow = sheet.createRow(dataRowNum++)
                        for ((colIdx, column) in columns.withIndex()) {
                            val cell = dataRow.createCell(colIdx)
                            val value = row[column]
                            when (value) {
                                is Number -> cell.setCellValue(value.toDouble())
                                is Boolean -> cell.setCellValue(value)
                                is Date -> cell.setCellValue(value)
                                else -> cell.setCellValue(value?.toString() ?: "")
                            }
                        }
                    }
                    
                    // Auto-size columns
                    for (colIdx in columns.indices) {
                        sheet.autoSizeColumn(colIdx)
                    }
                }
            }
            
            // Write the workbook to file
            FileOutputStream(outputPath).use { outputStream ->
                workbook.write(outputStream)
            }
        }
        
        return outputPath
    }
    
    /**
     * Generate a CSV report
     */
    private fun generateCsvReport(
        report: Report,
        template: ProcessedTemplate,
        data: Map<String, List<Map<String, Any>>>
    ): String {
        val filename = "report_${report.id}_${System.currentTimeMillis()}.csv"
        val outputPath = File(reportOutputPath, filename).absolutePath
        
        // For CSV, we'll use the first query result as the main data
        val mainQueryId = template.queries.firstOrNull()
        val queryData = mainQueryId?.let { data[it] } ?: emptyList()
        
        if (queryData.isNotEmpty()) {
            val columns = queryData.first().keys.toList()
            
            Files.newBufferedWriter(Paths.get(outputPath)).use { writer ->
                CSVPrinter(writer, CSVFormat.DEFAULT.withHeader(*columns.toTypedArray())).use { printer ->
                    for (row in queryData) {
                        printer.printRecord(columns.map { row[it] })
                    }
                }
            }
        } else {
            // Create empty CSV with header
            Files.newBufferedWriter(Paths.get(outputPath)).use { writer ->
                writer.write("No data available for report: ${report.name}")
            }
        }
        
        return outputPath
    }
    
    /**
     * Generate an HTML report
     */
    private fun generateHtmlReport(
        report: Report,
        template: ProcessedTemplate,
        data: Map<String, List<Map<String, Any>>>
    ): String {
        val filename = "report_${report.id}_${System.currentTimeMillis()}.html"
        val outputPath = File(reportOutputPath, filename).absolutePath
        
        val htmlBuilder = StringBuilder()
        
        // Start HTML document
        htmlBuilder.append("""
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <title>${report.name}</title>
                <style>
                    body { font-family: Arial, sans-serif; margin: 20px; }
                    h1 { color: #333366; }
                    h2 { color: #336699; margin-top: 20px; }
                    .timestamp { color: #666666; font-style: italic; font-size: 0.8em; }
                    table { border-collapse: collapse; width: 100%; margin: 15px 0; }
                    th { background-color: #f2f2f2; padding: 8px; text-align: left; border: 1px solid #ddd; }
                    td { padding: 8px; border: 1px solid #ddd; }
                    tr:nth-child(even) { background-color: #f9f9f9; }
                    .chart-container { margin: 20px 0; text-align: center; }
                </style>
            </head>
            <body>
                <h1>${report.name}</h1>
                <p class="timestamp">Generated: ${LocalDateTime.now().format(dateTimeFormatter)}</p>
        """.trimIndent())
        
        // Add description if available
        if (report.description != null) {
            htmlBuilder.append("<p>${report.description}</p>")
        }
        
        // Process each section
        for (section in template.sections) {
            htmlBuilder.append("<h2>${section.title}</h2>")
            
            // Process section content
            for (line in section.content) {
                when {
                    line.contains("{{QUERY:") -> {
                        val queryId = line.substringAfter("{{QUERY:").substringBefore("}}").trim()
                        val queryData = data[queryId] ?: emptyList()
                        
                        if (queryData.isNotEmpty()) {
                            val columns = queryData.first().keys.toList()
                            
                            htmlBuilder.append("<table>")
                            
                            // Add header row
                            htmlBuilder.append("<tr>")
                            for (column in columns) {
                                htmlBuilder.append("<th>$column</th>")
                            }
                            htmlBuilder.append("</tr>")
                            
                            // Add data rows
                            for (row in queryData) {
                                htmlBuilder.append("<tr>")
                                for (column in columns) {
                                    val value = row[column]?.toString() ?: ""
                                    htmlBuilder.append("<td>$value</td>")
                                }
                                htmlBuilder.append("</tr>")
                            }
                            
                            htmlBuilder.append("</table>")
                        } else {
                            htmlBuilder.append("<p><em>No data available for query: $queryId</em></p>")
                        }
                    }
                    line.contains("{{CHART:") -> {
                        val chartDef = line.substringAfter("{{CHART:").substringBefore("}}").trim().split(",")
                        if (chartDef.size >= 3) {
                            val type = chartDef[0].trim()
                            val title = chartDef[1].trim()
                            val dataSource = chartDef[2].trim()
                            
                            val chartData = data[dataSource] ?: emptyList()
                            if (chartData.isNotEmpty()) {
                                val chartImage = generateChart(type, title, chartData)
                                if (chartImage != null) {
                                    val base64Image = Base64.getEncoder().encodeToString(chartImage)
                                    htmlBuilder.append("""
                                        <div class="chart-container">
                                            <h3>$title</h3>
                                            <img src="data:image/png;base64,$base64Image" alt="$title">
                                        </div>
                                    """.trimIndent())
                                }
                            }
                        }
                    }
                    else -> {
                        if (line.isNotEmpty()) {
                            htmlBuilder.append("<p>$line</p>")
                        }
                    }
                }
            }
        }
        
        // End HTML document
        htmlBuilder.append("""
            </body>
            </html>
        """.trimIndent())
        
        // Write HTML to file
        Files.write(Paths.get(outputPath), htmlBuilder.toString().toByteArray())
        
        return outputPath
    }
    
    /**
     * Generate a JSON report
     */
    private fun generateJsonReport(
        report: Report,
        template: ProcessedTemplate,
        data: Map<String, List<Map<String, Any>>>
    ): String {
        val filename = "report_${report.id}_${System.currentTimeMillis()}.json"
        val outputPath = File(reportOutputPath, filename).absolutePath
        
        val jsonReport = mapOf(
            "report" to mapOf(
                "id" to report.id,
                "name" to report.name,
                "description" to report.description,
                "generated_at" to LocalDateTime.now().format(dateTimeFormatter)
            ),
            "data" to data
        )
        
        // Use kotlinx.serialization to convert to JSON
        val jsonString = kotlinx.serialization.json.Json { 
            prettyPrint = true 
            ignoreUnknownKeys = true
        }.encodeToString(kotlinx.serialization.json.JsonElement.serializer(), 
            kotlinx.serialization.json.buildJsonObject {
                put("report", kotlinx.serialization.json.buildJsonObject {
                    put("id", kotlinx.serialization.json.JsonPrimitive(report.id))
                    put("name", kotlinx.serialization.json.JsonPrimitive(report.name))
                    report.description?.let { put("description", kotlinx.serialization.json.JsonPrimitive(it)) }
                    put("generated_at", kotlinx.serialization.json.JsonPrimitive(LocalDateTime.now().format(dateTimeFormatter)))
                })
                
                put("data", kotlinx.serialization.json.buildJsonObject {
                    data.forEach { (queryId, queryData) ->
                        put(queryId, kotlinx.serialization.json.buildJsonArray {
                            queryData.forEach { row ->
                                add(kotlinx.serialization.json.buildJsonObject {
                                    row.forEach { (key, value) ->
                                        when (value) {
                                            is Number -> put(key, kotlinx.serialization.json.JsonPrimitive(value))
                                            is Boolean -> put(key, kotlinx.serialization.json.JsonPrimitive(value))
                                            is String -> put(key, kotlinx.serialization.json.JsonPrimitive(value))
                                            else -> put(key, kotlinx.serialization.json.JsonPrimitive(value.toString()))
                                        }
                                    }
                                })
                            }
                        })
                    }
                })
            }
        )
        
        // Write JSON to file
        Files.write(Paths.get(outputPath), jsonString.toByteArray())
        
        return outputPath
    }
    
    /**
     * Generate a chart image based on the provided data
     */
    private fun generateChart(type: String, title: String, data: List<Map<String, Any>>): ByteArray? {
        return try {
            when (type.lowercase()) {
                "bar" -> generateBarChart(title, data)
                "pie" -> generatePieChart(title, data)
                "line" -> generateLineChart(title, data)
                else -> null
            }
        } catch (e: Exception) {
            logger.error("Error generating chart: ${e.message}", e)
            null
        }
    }
    
    /**
     * Generate a bar chart
     */
    private fun generateBarChart(title: String, data: List<Map<String, Any>>): ByteArray {
        val dataset = DefaultCategoryDataset()
        
        // Assume data has category and value columns
        for (row in data) {
            val category = row["category"]?.toString() ?: row.keys.first { it != "value" }.let { row[it]?.toString() } ?: "Unknown"
            val value = (row["value"] as? Number)?.toDouble() ?: 0.0
            dataset.addValue(value, "Series 1", category)
        }
        
        val chart = ChartFactory.createBarChart(
            title,
            "Category",
            "Value",
            dataset,
            PlotOrientation.VERTICAL,
            true,
            true,
            false
        )
        
        // Customize chart appearance
        val plot = chart.categoryPlot
        plot.renderer.setSeriesPaint(0, Color(70, 130, 180))
        
        val outputStream = ByteArrayOutputStream()
        ChartUtils.writeChartAsPNG(outputStream, chart, 600, 400)
        return outputStream.toByteArray()
    }
    
    /**
     * Generate a pie chart
     */
    private fun generatePieChart(title: String, data: List<Map<String, Any>>): ByteArray {
        val dataset = DefaultPieDataset<String>()
        
        // Assume data has label and value columns
        for (row in data) {
            val label = row["label"]?.toString() ?: row.keys.first { it != "value" }.let { row[it]?.toString() } ?: "Unknown"
            val value = (row["value"] as? Number)?.toDouble() ?: 0.0
            dataset.setValue(label, value)
        }
        
        val chart = ChartFactory.createPieChart(
            title,
            dataset,
            true,
            true,
            false
        )
        
        val outputStream = ByteArrayOutputStream()
        ChartUtils.writeChartAsPNG(outputStream, chart, 600, 400)
        return outputStream.toByteArray()
    }
    
    /**
     * Generate a line chart
     */
    private fun generateLineChart(title: String, data: List<Map<String, Any>>): ByteArray {
        val dataset = XYSeriesCollection()
        val series = XYSeries("Data")
        
        // Assume data has x and y columns, or timestamp and value
        for (row in data) {
            val x = when {
                row.containsKey("x") -> (row["x"] as? Number)?.toDouble() ?: 0.0
                row.containsKey("timestamp") -> (row["timestamp"] as? Number)?.toDouble() ?: 0.0
                else -> 0.0
            }
            
            val y = when {
                row.containsKey("y") -> (row["y"] as? Number)?.toDouble() ?: 0.0
                row.containsKey("value") -> (row["value"] as? Number)?.toDouble() ?: 0.0
                else -> 0.0
            }
            
            series.add(x, y)
        }
        
        dataset.addSeries(series)
        
        val chart = ChartFactory.createXYLineChart(
            title,
            "X",
            "Y",
            dataset,
            PlotOrientation.VERTICAL,
            true,
            true,
            false
        )
        
        val outputStream = ByteArrayOutputStream()
        ChartUtils.writeChartAsPNG(outputStream, chart, 600, 400)
        return outputStream.toByteArray()
    }
    
    /**
     * Helper classes for template processing
     */
    data class ProcessedTemplate(
        val content: String,
        val sections: List<TemplateSection>,
        val queries: List<String>,
        val charts: List<ChartDefinition>
    )
    
    data class TemplateSection(
        val title: String,
        val content: MutableList<String>
    )
    
    data class ChartDefinition(
        val type: String,
        val title: String,
        val dataSource: String
    )
}

/**
 * Custom exception for report generation errors
 */
class ReportGenerationException(message: String, cause: Throwable? = null) : Exception(message, cause)