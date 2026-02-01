package com.mgomanager.app.ui.screens.logs

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.mgomanager.app.data.local.database.entities.LogEntity
import com.mgomanager.app.ui.theme.StatusGreen
import com.mgomanager.app.ui.theme.StatusOrange
import com.mgomanager.app.ui.theme.StatusRed
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogScreen(
    navController: NavController,
    viewModel: LogViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showDeleteDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Logs") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Zurück")
                    }
                },
                actions = {
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "Alle löschen")
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(uiState.logs) { log ->
                LogEntryCard(log)
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Alle Logs löschen?") },
            text = { Text("Diese Aktion kann nicht rückgängig gemacht werden.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearAllLogs()
                    showDeleteDialog = false
                }) {
                    Text("Löschen")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Abbrechen")
                }
            }
        )
    }
}

@Composable
private fun LogEntryCard(log: LogEntity) {
    var expanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val headerTime = SimpleDateFormat("HH:mm", Locale.GERMAN).format(Date(log.timestamp)) + " Uhr"
    val badge = logLevelBadge(log.level)

    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = { expanded = !expanded }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = headerTime,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                StatusBadge(text = badge.label, color = badge.color)
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = log.message,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "Anklicken für mehr Infos.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )

            if (expanded) {
                Spacer(modifier = Modifier.height(8.dp))
                Divider()
                Spacer(modifier = Modifier.height(8.dp))

                log.stackTrace?.let { stack ->
                    val lines = parseLogLines(stack)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        lines.forEach { line ->
                            when {
                                line.isSpacer -> Spacer(modifier = Modifier.height(4.dp))
                                line.isErrorDetail -> ErrorDetailLine(text = line.text)
                                line.status != null -> StatusLine(line = line)
                                line.isStepTitle -> StepTitleLine(text = line.text)
                                else -> Text(
                                    text = line.text,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    CopyButton(textToCopy = stack, context = context)
                } ?: run {
                    Text(
                        text = "Keine Details vorhanden.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(text: String, color: Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = color
    )
}

@Composable
private fun StatusLine(line: ParsedLine) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StatusIcon(status = line.status)
        Text(
            text = line.text,
            style = MaterialTheme.typography.bodySmall,
            color = statusColor(line.status)
        )
    }
}

@Composable
private fun StepTitleLine(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        fontWeight = FontWeight.Bold
    )
}

@Composable
private fun ErrorDetailLine(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.Error,
            contentDescription = "Fehlerdetails",
            tint = StatusRed
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = StatusRed
        )
    }
}

@Composable
private fun StatusIcon(status: LineStatus?) {
    val (icon, color, description) = when (status) {
        LineStatus.ERFOLG -> Triple(Icons.Default.CheckCircle, StatusGreen, "Status Erfolg")
        LineStatus.FEHLER -> Triple(Icons.Default.Error, StatusRed, "Status Fehler")
        LineStatus.WARNUNG -> Triple(Icons.Default.Warning, StatusOrange, "Status Warnung")
        LineStatus.USERABBRUCH -> Triple(Icons.Default.Cancel, StatusOrange, "Status Userabbruch")
        null -> Triple(Icons.Default.CheckCircle, Color.Transparent, "Status Unbekannt")
    }

    Icon(
        icon,
        contentDescription = description,
        tint = color
    )
}

@Composable
private fun CopyButton(textToCopy: String, context: Context) {
    TextButton(onClick = {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Log Details", textToCopy)
        clipboard.setPrimaryClip(clip)
    }) {
        Icon(Icons.Default.ContentCopy, contentDescription = "Log kopieren")
        Spacer(modifier = Modifier.width(6.dp))
        Text("Kopieren")
    }
}

private data class LevelBadge(val label: String, val color: Color)

private fun logLevelBadge(level: String): LevelBadge {
    return when (level.uppercase(Locale.GERMAN)) {
        "ERROR" -> LevelBadge("Fehler", StatusRed)
        "WARNING", "WARN" -> LevelBadge("Warnung", StatusOrange)
        else -> LevelBadge("Erfolg", StatusGreen)
    }
}

private enum class LineStatus {
    ERFOLG,
    FEHLER,
    WARNUNG,
    USERABBRUCH
}

private data class ParsedLine(
    val text: String,
    val status: LineStatus? = null,
    val isErrorDetail: Boolean = false,
    val isStepTitle: Boolean = false,
    val isSpacer: Boolean = false
)

private fun parseLogLines(stackTrace: String): List<ParsedLine> {
    val statusRegex = Regex("\\[(Erfolg|Fehler|Warnung|Userabbruch)\\]$")
    val lines = stackTrace.lines()
    val parsed = mutableListOf<ParsedLine>()

    lines.forEachIndexed { index, rawLine ->
        val line = rawLine.trimEnd()
        if (line.isBlank()) {
            parsed.add(ParsedLine(text = "", isSpacer = true))
            return@forEachIndexed
        }

        if (line.startsWith("Fehlerdetails:")) {
            parsed.add(
                ParsedLine(
                    text = line,
                    isErrorDetail = true
                )
            )
            return@forEachIndexed
        }

        val match = statusRegex.find(line)
        if (match != null) {
            val statusLabel = match.groupValues[1]
            val status = when (statusLabel) {
                "Erfolg" -> LineStatus.ERFOLG
                "Fehler" -> LineStatus.FEHLER
                "Warnung" -> LineStatus.WARNUNG
                "Userabbruch" -> LineStatus.USERABBRUCH
                else -> null
            }
            parsed.add(
                ParsedLine(
                    text = line.replace(statusRegex, "").trim(),
                    status = status
                )
            )
            return@forEachIndexed
        }

        val isStepTitle = Regex("^\\d+\\.").containsMatchIn(line)
        parsed.add(
            ParsedLine(
                text = line,
                isStepTitle = isStepTitle
            )
        )
    }

    return parsed
}

private fun statusColor(status: LineStatus?): Color {
    return when (status) {
        LineStatus.ERFOLG -> StatusGreen
        LineStatus.FEHLER -> StatusRed
        LineStatus.WARNUNG -> StatusOrange
        LineStatus.USERABBRUCH -> StatusOrange
        null -> Color.Unspecified
    }
}
