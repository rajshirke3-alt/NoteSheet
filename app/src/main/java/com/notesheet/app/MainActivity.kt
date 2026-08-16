package com.notesheet.app

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var db: AppDatabase
    private lateinit var adapter: RecordAdapter
    private var editingRecord: PatientRecord? = null

    // Voice recognition state
    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false
    private var userStoppedManually = false
    private var parsedSoFar = ParsedFields()

    // Dialog field refs (set while dialog is open)
    private var dEtBed: EditText? = null
    private var dEtName: EditText? = null
    private var dEtConsultant: EditText? = null
    private var dEtDetails: EditText? = null
    private var dTvTranscript: TextView? = null
    private var dTvDateDisplay: TextView? = null
    private var dBtnMic: Button? = null
    private var dBtnStop: Button? = null

    private val recordAudioPermission =
        registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startListening() else
                Toast.makeText(this, "Microphone permission is required for voice entry", Toast.LENGTH_LONG).show()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        db = AppDatabase.getInstance(this)

        val rv = findViewById<RecyclerView>(R.id.rvRecords)
        rv.layoutManager = LinearLayoutManager(this)
        adapter = RecordAdapter(
            emptyList(),
            onClick = { record -> openDialog(record) },
            onLongClick = { record -> confirmDelete(record) }
        )
        rv.adapter = adapter

        db.recordDao().getAll().observe(this) { list ->
            adapter.updateData(list)
        }

        val etSearch = findViewById<EditText>(R.id.etSearch)
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val q = s?.toString().orEmpty()
                val liveData = if (q.isBlank()) db.recordDao().getAll() else db.recordDao().search(q)
                liveData.observe(this@MainActivity) { list -> adapter.updateData(list) }
            }
        })

        findViewById<Button>(R.id.btnAdd).setOnClickListener { openDialog(null) }
        findViewById<Button>(R.id.btnExport).setOnClickListener { exportOrPrint() }
    }

    // ---------------- Add / Edit dialog ----------------

    private fun openDialog(record: PatientRecord?) {
        editingRecord = record
        val view = layoutInflater.inflate(R.layout.dialog_add_record, null)
        dEtBed = view.findViewById(R.id.etBed)
        dEtName = view.findViewById(R.id.etName)
        dEtConsultant = view.findViewById(R.id.etConsultant)
        dEtDetails = view.findViewById(R.id.etDetails)
        dTvTranscript = view.findViewById(R.id.tvLiveTranscript)
        dTvDateDisplay = view.findViewById(R.id.tvDateDisplay)
        dBtnMic = view.findViewById(R.id.btnMic)
        dBtnStop = view.findViewById(R.id.btnStopMic)

        record?.let {
            dEtBed?.setText(it.bedNumber)
            dEtName?.setText(it.patientName)
            dEtConsultant?.setText(it.primaryConsultant)
            dEtDetails?.setText(it.details)
            dTvDateDisplay?.text = "Recorded on: ${it.dateAdded}"
            dTvDateDisplay?.visibility = View.VISIBLE
        }
        parsedSoFar = ParsedFields(
            bedNumber = dEtBed?.text?.toString().orEmpty(),
            patientName = dEtName?.text?.toString().orEmpty(),
            primaryConsultant = dEtConsultant?.text?.toString().orEmpty(),
            details = dEtDetails?.text?.toString().orEmpty()
        )

        val dialog = AlertDialog.Builder(this)
            .setTitle(if (record == null) "New Record" else "Edit Record")
            .setView(view)
            .setNegativeButton("Cancel") { d, _ ->
                stopListening()
                d.dismiss()
            }
            .create()

        dBtnMic?.setOnClickListener { requestMicAndStart() }
        dBtnStop?.setOnClickListener { stopListening() }

        view.findViewById<Button>(R.id.btnSave).setOnClickListener {
            stopListening()
            val bed = dEtBed?.text?.toString()?.trim().orEmpty()
            val name = dEtName?.text?.toString()?.trim().orEmpty()
            val consultant = dEtConsultant?.text?.toString()?.trim().orEmpty()
            val details = dEtDetails?.text?.toString()?.trim().orEmpty()

            if (bed.isEmpty() && name.isEmpty() && consultant.isEmpty() && details.isEmpty()) {
                Toast.makeText(this, "Nothing to save", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val dateStr = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date())

            lifecycleScope.launch {
                withContext(Dispatchers.IO) {
                    if (record == null) {
                        db.recordDao().insert(
                            PatientRecord(
                                bedNumber = bed,
                                patientName = name,
                                primaryConsultant = consultant,
                                details = details,
                                dateAdded = dateStr
                            )
                        )
                    } else {
                        db.recordDao().update(
                            record.copy(
                                bedNumber = bed,
                                patientName = name,
                                primaryConsultant = consultant,
                                details = details
                            )
                        )
                    }
                }
                Toast.makeText(this@MainActivity, "Saved", Toast.LENGTH_SHORT).show()
            }
            dialog.dismiss()
        }

        dialog.setOnDismissListener { stopListening() }
        dialog.show()
    }

    private fun confirmDelete(record: PatientRecord) {
        AlertDialog.Builder(this)
            .setTitle("Delete record?")
            .setMessage("Bed ${record.bedNumber} - ${record.patientName}")
            .setPositiveButton("Delete") { _, _ ->
                lifecycleScope.launch(Dispatchers.IO) { db.recordDao().delete(record) }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ---------------- Voice recognition (manual start/stop, order independent) ----------------

    private fun requestMicAndStart() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            recordAudioPermission.launch(Manifest.permission.RECORD_AUDIO)
        } else {
            startListening()
        }
    }

    private fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, "Speech recognition not available on this device", Toast.LENGTH_LONG).show()
            return
        }
        userStoppedManually = false
        isListening = true
        dBtnMic?.isEnabled = false
        dBtnStop?.isEnabled = true
        dBtnMic?.text = "🎤 Listening..."

        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}

                override fun onError(error: Int) {
                    // Recognizer times out after a pause - restart it automatically
                    // unless the user pressed Stop. This is what makes recording
                    // continue indefinitely until manually stopped.
                    if (!userStoppedManually) {
                        restartListening()
                    } else {
                        finishListeningUi()
                    }
                }

                override fun onResults(results: Bundle?) {
                    val text = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                    if (!text.isNullOrBlank()) {
                        applyParsedResult(text)
                    }
                    if (!userStoppedManually) {
                        restartListening()
                    } else {
                        finishListeningUi()
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val text = partialResults
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                    if (!text.isNullOrBlank()) {
                        dTvTranscript?.text = "Hearing: $text"
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            startListening(buildRecognizerIntent())
        }
    }

    private fun restartListening() {
        if (userStoppedManually) return
        speechRecognizer?.startListening(buildRecognizerIntent())
    }

    private fun buildRecognizerIntent(): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
        }

    private fun applyParsedResult(text: String) {
        parsedSoFar = VoiceParser.parse(text, parsedSoFar)
        dEtBed?.setText(parsedSoFar.bedNumber)
        dEtName?.setText(parsedSoFar.patientName)
        dEtConsultant?.setText(parsedSoFar.primaryConsultant)
        dEtDetails?.setText(parsedSoFar.details)
        dTvTranscript?.text = "Last heard: $text"
    }

    private fun stopListening() {
        userStoppedManually = true
        isListening = false
        speechRecognizer?.stopListening()
        speechRecognizer?.destroy()
        speechRecognizer = null
        finishListeningUi()
    }

    private fun finishListeningUi() {
        dBtnMic?.isEnabled = true
        dBtnStop?.isEnabled = false
        dBtnMic?.text = "🎤 Start Recording"
    }

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer?.destroy()
    }

    // ---------------- Export ----------------

    private fun exportOrPrint() {
        lifecycleScope.launch {
            val all = withContext(Dispatchers.IO) {
                db.recordDao().getAllSync()
            }
            if (all.isEmpty()) {
                Toast.makeText(this@MainActivity, "No records to export", Toast.LENGTH_SHORT).show()
                return@launch
            }
            AlertDialog.Builder(this@MainActivity)
                .setTitle("Export")
                .setItems(arrayOf("Print", "Save as PDF & Share")) { _, which ->
                    when (which) {
                        0 -> PdfExporter.printRecords(this@MainActivity, all)
                        1 -> {
                            val file = PdfExporter.buildPdf(this@MainActivity, all)
                            val uri = PdfExporter.shareUri(this@MainActivity, file)
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "application/pdf"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            startActivity(Intent.createChooser(shareIntent, "Share PDF"))
                        }
                    }
                }
                .show()
        }
    }
}
