package com.notesheet.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.print.PrintAttributes
import android.print.PrintManager
import android.print.pdf.PrintedPdfDocument
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

/**
 * Draws all records into a simple multi-page table PDF and can either
 * hand it to Android's Print framework or save/share it as a .pdf file.
 */
object PdfExporter {

    private const val PAGE_WIDTH = 842  // A4 landscape points
    private const val PAGE_HEIGHT = 595
    private val COLS = listOf("Bed No", "Patient Name", "Primary Consultant", "Details", "Date")
    private val WEIGHTS = listOf(0.09f, 0.18f, 0.18f, 0.42f, 0.13f)

    fun buildPdf(context: Context, records: List<PatientRecord>): File {
        val doc = PdfDocument()
        val margin = 24
        val usableWidth = PAGE_WIDTH - margin * 2
        val headerPaint = Paint().apply { textSize = 11f; isFakeBoldText = true; color = Color.WHITE }
        val cellPaint = Paint().apply { textSize = 10f; color = Color.BLACK }
        val linePaint = Paint().apply { color = Color.LTGRAY; strokeWidth = 1f }
        val headerBgPaint = Paint().apply { color = Color.rgb(30, 58, 95) }

        var pageNumber = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
        var canvas = page.canvas
        var y = margin

        fun drawHeader() {
            canvas.drawRect(margin.toFloat(), y.toFloat(), (PAGE_WIDTH - margin).toFloat(), (y + 22).toFloat(), headerBgPaint)
            var x = margin.toFloat()
            for (idx in COLS.indices) {
                canvas.drawText(COLS[idx], x + 4, (y + 15).toFloat(), headerPaint)
                x += usableWidth * WEIGHTS[idx]
            }
            y += 26
        }

        drawHeader()

        for (record in records) {
            val rowValues = listOf(record.bedNumber, record.patientName, record.primaryConsultant, record.details, record.dateAdded)
            val rowHeight = 20
            if (y + rowHeight > PAGE_HEIGHT - margin) {
                doc.finishPage(page)
                pageNumber++
                page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
                canvas = page.canvas
                y = margin
                drawHeader()
            }
            var x = margin.toFloat()
            for (idx in rowValues.indices) {
                val colWidth = usableWidth * WEIGHTS[idx]
                val text = truncateToWidth(rowValues[idx], cellPaint, colWidth - 6)
                canvas.drawText(text, x + 4, (y + 14).toFloat(), cellPaint)
                x += colWidth
            }
            canvas.drawLine(margin.toFloat(), (y + rowHeight).toFloat(), (PAGE_WIDTH - margin).toFloat(), (y + rowHeight).toFloat(), linePaint)
            y += rowHeight
        }

        doc.finishPage(page)

        val outDir = File(context.cacheDir, "pdf").apply { mkdirs() }
        val outFile = File(outDir, "NoteSheet_${System.currentTimeMillis()}.pdf")
        FileOutputStream(outFile).use { doc.writeTo(it) }
        doc.close()
        return outFile
    }

    fun shareUri(context: Context, file: File) =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    fun printRecords(context: Context, records: List<PatientRecord>) {
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
        val jobName = "NoteSheet_${System.currentTimeMillis()}"
        val adapter = object : android.print.PrintDocumentAdapter() {
            var pdfDocument: PrintedPdfDocument? = null

            override fun onLayout(
                oldAttributes: PrintAttributes?,
                newAttributes: PrintAttributes,
                cancellationSignal: android.os.CancellationSignal?,
                callback: LayoutResultCallback,
                extras: android.os.Bundle?
            ) {
                pdfDocument = PrintedPdfDocument(context, newAttributes)
                if (cancellationSignal?.isCanceled == true) {
                    callback.onLayoutCancelled(); return
                }
                val info = android.print.PrintDocumentInfo.Builder(jobName)
                    .setContentType(android.print.PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                    .build()
                callback.onLayoutFinished(info, true)
            }

            override fun onWrite(
                pages: Array<out android.print.PageRange>?,
                destination: android.os.ParcelFileDescriptor,
                cancellationSignal: android.os.CancellationSignal?,
                callback: WriteResultCallback
            ) {
                val builtFile = buildPdf(context, records)
                java.io.FileInputStream(builtFile).use { input ->
                    java.io.FileOutputStream(destination.fileDescriptor).use { output ->
                        input.copyTo(output)
                    }
                }
                callback.onWriteFinished(arrayOf(android.print.PageRange.ALL_PAGES))
            }
        }
        printManager.print(jobName, adapter, PrintAttributes.Builder().build())
    }

    private fun truncateToWidth(text: String, paint: Paint, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        var end = text.length
        while (end > 0 && paint.measureText(text.substring(0, end) + "...") > maxWidth) end--
        return text.substring(0, end) + "..."
    }
}
