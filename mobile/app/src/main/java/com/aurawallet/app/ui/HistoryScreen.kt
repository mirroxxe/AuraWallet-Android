package com.aurawallet.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aurawallet.app.data.repository.HistoryRecord
import com.aurawallet.app.data.repository.HistoryStatus
import com.aurawallet.app.ui.theme.Aura
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy, HH:mm").withZone(ZoneId.systemDefault())
fun formatTime(epochMs: Long): String = TIME_FORMAT.format(Instant.ofEpochMilli(epochMs))

@Composable
private fun ScreenScaffold(title: String, onBack: () -> Unit, action: @Composable () -> Unit = {}, content: @Composable () -> Unit) {
    val c = Aura.colors
    Column(Modifier.fillMaxSize().background(c.bg).windowInsetsPadding(WindowInsets.safeDrawing)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Назад", tint = c.ink)
            }
            Text(title, style = MaterialTheme.typography.titleLarge, color = c.ink, modifier = Modifier.weight(1f).padding(start = 4.dp))
            action()
        }
        content()
    }
}

@Composable
private fun StatusChip(status: HistoryStatus) {
    val c = Aura.colors
    val (text, bg) = when (status) {
        HistoryStatus.DONE -> "готово" to c.positive.copy(alpha = 0.14f)
        HistoryStatus.PENDING -> "очікує" to c.butter
        HistoryStatus.SENDING -> "надсилається" to c.lilac
    }
    Text(text, style = MaterialTheme.typography.labelMedium, color = c.ink,
        modifier = Modifier.clip(CircleShape).background(bg).padding(horizontal = 10.dp, vertical = 4.dp))
}

@Composable
private fun ConfirmDialog(title: String, text: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = { onConfirm(); onDismiss() }) { Text(confirm, color = Aura.colors.negative) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Скасувати") } },
    )
}

@Composable
fun HistoryScreen(vm: HistoryViewModel, onBack: () -> Unit, onOpen: (Long) -> Unit) {
    val c = Aura.colors
    val records by vm.records.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    var askClear by remember { mutableStateOf(false) }
    var askDelete by remember { mutableStateOf<HistoryRecord?>(null) }

    ScreenScaffold("Історія", onBack, action = {
        if (!records.isNullOrEmpty()) TextButton(onClick = { askClear = true }) { Text("Очистити", color = c.negative) }
    }) {
        when {
            records == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = c.ink) }
            records!!.isEmpty() -> EmptyHistory()
            else -> LazyColumn(
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                message?.let { m ->
                    item {
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.lilac).padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Text(m, style = MaterialTheme.typography.bodyMedium, color = c.ink, modifier = Modifier.weight(1f))
                            TextButton(onClick = vm::dismissMessage) { Text("OK", color = c.ink) }
                        }
                    }
                }
                item {
                    Text("${records!!.size} записів · новіші зверху · зберігаються на цьому пристрої",
                        style = MaterialTheme.typography.labelMedium, color = c.ink3)
                }
                items(records!!, key = { it.id }) { r ->
                    HistoryRow(r, onOpen = { onOpen(r.id) }, onSend = { vm.send(r) }, onDelete = { askDelete = r })
                }
            }
        }
    }
    if (askClear) ConfirmDialog("Очистити історію?", "Усі записи буде видалено з пристрою. Цю дію не можна скасувати.",
        "Очистити", onConfirm = { vm.clear() }, onDismiss = { askClear = false })
    askDelete?.let { r ->
        ConfirmDialog("Видалити запис?", "«${r.inputText}» буде видалено з історії.", "Видалити",
            onConfirm = { vm.delete(r.id) }, onDismiss = { askDelete = null })
    }
}

@Composable
private fun EmptyHistory() {
    val c = Aura.colors
    Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(64.dp).clip(RoundedCornerShape(20.dp)).background(c.lilac), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.History, contentDescription = null, tint = c.ink)
        }
        Spacer(Modifier.height(16.dp))
        Text("Історія порожня", style = MaterialTheme.typography.titleLarge, color = c.ink)
        Spacer(Modifier.height(6.dp))
        Text("Тут з'являться результати розпізнавання. Вони зберігаються на пристрої й не зникають після перезапуску.",
            style = MaterialTheme.typography.bodyMedium, color = c.ink2)
    }
}

@Composable
private fun HistoryRow(r: HistoryRecord, onOpen: () -> Unit, onSend: () -> Unit, onDelete: () -> Unit) {
    val c = Aura.colors
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.surface).border(1.dp, c.line, RoundedCornerShape(20.dp))
            .clickable(enabled = r.status == HistoryStatus.DONE, onClick = onOpen).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(r.inputText, style = MaterialTheme.typography.titleMedium, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusChip(r.status)
                val res = r.result
                val money = if (res?.amount != null) " · " + formatMoney(res.amount, res.currency, res.isIncome) else ""
                Text(
                    r.displayLabel?.let { label -> "→ $label$money" } ?: "результату ще немає",
                    style = MaterialTheme.typography.bodyMedium, color = c.ink2, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Text(formatTime(r.createdAt) + if (r.confirmed) " · збережено як операцію" else "",
                style = MaterialTheme.typography.labelMedium, color = c.ink3)
        }
        when (r.status) {
            HistoryStatus.PENDING -> Button(onClick = onSend, shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = c.ink, contentColor = c.onInk)) {
                Icon(Icons.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Надіслати")
            }
            HistoryStatus.SENDING -> CircularProgressIndicator(Modifier.size(22.dp), color = c.ink, strokeWidth = 2.dp)
            HistoryStatus.DONE -> {}
        }
        Box(Modifier.size(40.dp).clip(CircleShape).clickable(enabled = r.status != HistoryStatus.SENDING, onClick = onDelete),
            contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.DeleteOutline, contentDescription = "Видалити", tint = c.ink3)
        }
    }
}

@Composable
fun HistoryDetailsScreen(vm: HistoryViewModel, id: Long, onBack: () -> Unit) {
    val c = Aura.colors
    val record by remember(id) { vm.record(id) }.collectAsState(initial = null)
    var askDelete by remember { mutableStateOf(false) }
    ScreenScaffold("Деталі", onBack) {
        val r = record
        if (r == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Запис не знайдено", color = c.ink2) }
            return@ScreenScaffold
        }
        LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusChip(r.status); Spacer(Modifier.width(8.dp))
                    Text(formatTime(r.createdAt), style = MaterialTheme.typography.labelMedium, color = c.ink3)
                }
            }
            item { DetailBlock("Запит", r.inputText) }
            r.result?.let { res ->
                item { DetailBlock("Категорія AI", "${res.label} · впевненість ${(res.confidence * 100).toInt()}%" + if (res.lowConfidence) " (низька)" else "") }
                val chosen = r.chosenLabel
                if (chosen != null && r.chosenCategory != res.category) item { DetailBlock("Обрано користувачем", chosen) }
                item {
                    DetailBlock("Сума і магазин", listOfNotNull(
                        res.amount?.let { formatMoney(it, res.currency, res.isIncome) }, res.merchant, if (res.isIncome) "дохід" else "витрата",
                    ).joinToString(" · "))
                }
                item { DetailBlock("Пояснення", res.explanation) }
                if (res.alternatives.isNotEmpty()) item {
                    DetailBlock("Альтернативи", res.alternatives.joinToString { "${it.label} ${(it.confidence * 100).toInt()}%" })
                }
                item { DetailBlock("Обробка на сервері", "${"%.1f".format(res.processingMs)} мс · збережено як історичний результат") }
            }
            item {
                Button(onClick = { askDelete = true }, modifier = Modifier.fillMaxWidth().height(48.dp), shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = c.stone, contentColor = c.negative)) { Text("Видалити запис") }
            }
        }
    }
    if (askDelete) ConfirmDialog("Видалити запис?", "Запис буде видалено з історії на цьому пристрої.", "Видалити",
        onConfirm = { vm.delete(id); onBack() }, onDismiss = { askDelete = false })
}

@Composable
private fun DetailBlock(title: String, value: String) {
    val c = Aura.colors
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(c.surface).border(1.dp, c.line, RoundedCornerShape(18.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.labelMedium, color = c.ink3)
        Text(value, style = MaterialTheme.typography.bodyLarge, color = c.ink)
    }
}
