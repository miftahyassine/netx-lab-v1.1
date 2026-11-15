package com.netxlab.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.netxlab.app.ui.theme.Mono

val LocalShowInfo = compositionLocalOf { true }

fun copyText(ctx: Context, text: String) {
    (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("NETX LAB", text))
    Toast.makeText(ctx, "Copied", Toast.LENGTH_SHORT).show()
}

fun shareText(ctx: Context, text: String, subject: String = "NETX LAB export") {
    val i = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text); putExtra(Intent.EXTRA_SUBJECT, subject) }
    ctx.startActivity(Intent.createChooser(i, "Share / export").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

@Composable
fun InfoIcon(title: String, text: String) {
    if (!LocalShowInfo.current) return
    var open by remember { mutableStateOf(false) }
    IconButton(onClick = { open = true }, modifier = Modifier.size(28.dp)) {
        Icon(Icons.Default.Info, contentDescription = "Information", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
    }
    if (open) AlertDialog(
        onDismissRequest = { open = false },
        confirmButton = { TextButton(onClick = { open = false }) { Text("OK") } },
        title = { Text(title) }, text = { Text(text) }
    )
}

@Composable
fun SectionTitle(text: String, info: String? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.titleMedium)
        if (info != null) { Spacer(Modifier.width(4.dp)); InfoIcon(text, info) }
    }
}

@Composable
fun NxCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.large
    ) { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp), content = content) }
}

@Composable
fun ToolColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp), content = content
    )
}

@Composable
fun Field(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier = Modifier.fillMaxWidth(), singleLine: Boolean = true) {
    OutlinedTextField(value = value, onValueChange = onChange, label = { Text(label) }, singleLine = singleLine, modifier = modifier)
}

@Composable
fun OutputBox(text: String, title: String = "Output") {
    if (text.isBlank()) return
    val ctx = LocalContext.current
    NxCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            IconButton(onClick = { copyText(ctx, text) }) { Icon(Icons.Default.ContentCopy, "Copy") }
            IconButton(onClick = { shareText(ctx, text) }) { Icon(Icons.Default.Share, "Share / export") }
        }
        SelectionContainer {
            Text(text, style = Mono)
        }
    }
}

@Composable
fun KeyValue(k: String, v: String?) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(k, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.width(12.dp))
        Text(v ?: "—", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f, fill = false))
    }
}

@Composable
fun ErrorText(msg: String?) {
    if (msg != null) Text(msg, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
}

@Composable
fun ScopeConfirm(checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Text("I own this target or have explicit permission to test it.", style = MaterialTheme.typography.bodyMedium)
    }
}
