package ru.matveyb9.diy.thermometerapp.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import ru.matveyb9.diy.thermometerapp.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExportRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        private const val AUTHORITY_SUFFIX = ".fileprovider"
        private const val EXPORTS_DIR      = "exports"
        private const val PDF_PAGE_WIDTH   = 1200
        private const val PDF_PAGE_HEIGHT  = 900
        private const val PDF_MARGIN       = 40f
        private const val PDF_TITLE_SIZE   = 28f
        private const val PDF_META_SIZE    = 18f
        private val DATE_FILE    = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())
        private val DATE_DISPLAY = SimpleDateFormat("yyyy-MM-dd HH:mm:ss",  Locale.getDefault())
    }

    suspend fun export(
        format: ExportFormat,
        history: List<TemperatureRecord>,
        unit: TemperatureUnit,
        chartBitmap: ImageBitmap?,
    ): ExportResult = withContext(Dispatchers.IO) {
        runCatching {
            val dir      = File(context.cacheDir, EXPORTS_DIR).also { it.mkdirs() }
            val fileName = "thermometer_${DATE_FILE.format(Date())}.${format.extension}"
            val file     = File(dir, fileName)

            when (format) {
                ExportFormat.CSV -> writeCsv(file, history, unit)
                ExportFormat.PNG -> writePng(file, chartBitmap)
                ExportFormat.PDF -> writePdf(file, history, unit, chartBitmap)
            }

            val uri = FileProvider.getUriForFile(
                context,
                context.packageName + AUTHORITY_SUFFIX,
                file,
            )
            ExportResult.Success(uri = uri, format = format)
        }.getOrElse { ExportResult.Failure(it.message ?: "Неизвестная ошибка") }
    }

    // ── CSV ──────────────────────────────────────────────────────────────────

    private fun writeCsv(file: File, history: List<TemperatureRecord>, unit: TemperatureUnit) {
        val unitLabel = unit.symbol()
        FileOutputStream(file).bufferedWriter().use { writer ->
            writer.write("timestamp,elapsed_sec,temperature_$unitLabel\n")
            history.forEach { record ->
                val ts   = DATE_DISPLAY.format(Date(record.timestamp))
                val temp = "%.2f".format(record.temperatureCelsius.toDisplayTemp(unit))
                writer.write("$ts,${record.elapsedSeconds},$temp\n")
            }
        }
    }

    // ── PNG ──────────────────────────────────────────────────────────────────

    private fun writePng(file: File, chartBitmap: ImageBitmap?) {
        val bitmap = chartBitmap?.asAndroidBitmap()
            ?: error("Нет изображения графика для экспорта в PNG")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
    }

    // ── PDF ──────────────────────────────────────────────────────────────────

    private fun writePdf(
        file: File,
        history: List<TemperatureRecord>,
        unit: TemperatureUnit,
        chartBitmap: ImageBitmap?,
    ) {
        val unitLabel = unit.symbol()
        val document  = PdfDocument()
        val pageInfo  = PdfDocument.PageInfo.Builder(PDF_PAGE_WIDTH, PDF_PAGE_HEIGHT, 1).create()
        val page      = document.startPage(pageInfo)
        val canvas: Canvas = page.canvas

        val titlePaint = Paint().apply { textSize = PDF_TITLE_SIZE; isFakeBoldText = true; color = Color.BLACK }
        val metaPaint  = Paint().apply { textSize = PDF_META_SIZE;  color = Color.DKGRAY }
        val notePaint  = Paint().apply { textSize = PDF_META_SIZE;  color = Color.GRAY }

        // Заголовок
        canvas.drawText(
            "Arduino Thermometer — График температуры",
            PDF_MARGIN, PDF_MARGIN + PDF_TITLE_SIZE,
            titlePaint,
        )

        // Метаданные
        val period = if (history.isNotEmpty()) {
            val from = DATE_DISPLAY.format(Date(history.first().timestamp))
            val to   = DATE_DISPLAY.format(Date(history.last().timestamp))
            "$from — $to"
        } else "нет данных"

        val line1Y = PDF_MARGIN + PDF_TITLE_SIZE + PDF_META_SIZE + 12f
        val line2Y = line1Y + PDF_META_SIZE + 8f
        canvas.drawText("Период: $period",                              PDF_MARGIN, line1Y, metaPaint)
        canvas.drawText("Точек: ${history.size}  |  Единицы: $unitLabel", PDF_MARGIN, line2Y, metaPaint)

        val topOffset = line2Y + 24f

        // FIX #7: если bitmap отсутствует — рисуем пояснительный текст вместо пустого документа
        if (chartBitmap != null) {
            val bmp             = chartBitmap.asAndroidBitmap()
            val availableWidth  = PDF_PAGE_WIDTH  - PDF_MARGIN * 2
            val availableHeight = PDF_PAGE_HEIGHT - topOffset - PDF_MARGIN
            val scaled = Bitmap.createScaledBitmap(
                bmp,
                availableWidth.toInt(),
                availableHeight.toInt(),
                true,
            )
            canvas.drawBitmap(scaled, PDF_MARGIN, topOffset, null)
        } else {
            canvas.drawText(
                "График недоступен — откройте вкладку «График» перед экспортом",
                PDF_MARGIN, topOffset + 40f,
                notePaint,
            )
        }

        document.finishPage(page)
        FileOutputStream(file).use { document.writeTo(it) }
        document.close()
    }
}
