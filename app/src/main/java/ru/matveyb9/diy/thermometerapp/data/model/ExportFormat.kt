package ru.matveyb9.diy.thermometerapp.data.model

enum class ExportFormat(
    val label: String,
    val mimeType: String,
    val extension: String,
) {
    CSV(label = "CSV", mimeType = "text/csv",        extension = "csv"),
    PNG(label = "PNG", mimeType = "image/png",        extension = "png"),
    PDF(label = "PDF", mimeType = "application/pdf",  extension = "pdf"),
}
