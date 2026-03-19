package ru.matveyb9.diy.thermometerapp.data.model

import android.net.Uri

sealed interface ExportResult {
    data class Success(val uri: Uri, val format: ExportFormat) : ExportResult
    data class Failure(val reason: String) : ExportResult
}
