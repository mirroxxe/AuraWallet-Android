package com.aurawallet.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Laptop
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PhoneIphone
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aurawallet.app.data.AppError
import com.aurawallet.app.data.Categorization
import com.aurawallet.app.data.SavedTransaction
import com.aurawallet.app.ui.theme.Aura
import java.text.NumberFormat
import java.util.Locale

private val EXAMPLES = listOf(
    "Кава в Aroma Kava 85 грн",
    "Uklon до роботи 142",
    "Сільпо 486,30",
    "Netflix 299 грн",
    "зарплата 38 000 грн",
)

@Composable
fun CategorizeScreen(vm: CategorizeViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val c = Aura.colors

    Box(
        Modifier
            .fillMaxSize()
            .background(c.bg)
            .background(
                Brush.verticalGradient(
                    0f to c.gradLilac.copy(alpha = 0.55f),
                    0.18f to c.gradBlush.copy(alpha = 0.35f),
                    0.36f to c.gradButter.copy(alpha = 0.18f),
                    0.55f to Color.Transparent,
                ),
            ),
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .imePadding(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { Header(state.server, onRetryHealth = vm::checkServer) }
            item {
                InputCard(
                    value = state.input,
                    error = state.inputError,
                    canSubmit = state.canSubmit,
                    loading = state.result is ResultState.Loading,
                    onChange = vm::onInputChange,
                    onSubmit = vm::submit,
                    onExample = vm::useExample,
                )
            }
            item {
                AnimatedContent(
                    targetState = state.result,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    contentKey = { it::class },
                    label = "result",
                ) { result ->
                    when (result) {
                        ResultState.Idle -> IdleHint()
                        ResultState.Loading -> LoadingCard()
                        is ResultState.Success -> ResultCard(result, onChoose = vm::chooseCategory, onSave = vm::save, onReset = vm::reset)
                        is ResultState.Error -> ErrorCard(result.error, onRetry = vm::retry)
                    }
                }
            }
            if (state.saved.isNotEmpty()) {
                item { Text("Збережено", style = MaterialTheme.typography.titleLarge, color = c.ink) }
                items(state.saved, key = { it.id }) { SavedRow(it) }
            }
        }
    }
}

@Composable
private fun Header(server: ServerStatus, onRetryHealth: () -> Unit) {
    val c = Aura.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("AI-помічник", style = MaterialTheme.typography.labelMedium, color = c.ink2)
            Text("Нова операція", style = MaterialTheme.typography.headlineLarge, color = c.ink)
        }
        val (dot, label) = when (server) {
            ServerStatus.Checking -> c.ink3 to "Перевірка…"
            ServerStatus.Online -> c.positive to "Сервер онлайн"
            ServerStatus.Offline -> c.negative to "Офлайн"
        }
        Row(
            Modifier
                .clip(CircleShape)
                .background(c.surface.copy(alpha = 0.8f))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = c.ink2)
            if (server == ServerStatus.Offline) {
                Spacer(Modifier.width(4.dp))
                TextButton(onClick = onRetryHealth, contentPadding = PaddingValues(0.dp)) {
                    Text("Ще раз", style = MaterialTheme.typography.labelMedium, color = c.ink)
                }
            }
        }
    }
}

@Composable
private fun AuraCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val c = Aura.colors
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(c.surface)
            .border(1.dp, c.line, RoundedCornerShape(24.dp))
            .padding(20.dp),
    ) { content() }
}

@Composable
private fun InputCard(
    value: String,
    error: String?,
    canSubmit: Boolean,
    loading: Boolean,
    onChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onExample: (String) -> Unit,
) {
    val c = Aura.colors
    AuraCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Опишіть витрату чи дохід — AI визначить категорію, суму й магазин.",
                style = MaterialTheme.typography.bodyMedium, color = c.ink2)
            OutlinedTextField(
                value = value,
                onValueChange = onChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Напр.: Кава в Aroma Kava 85 грн", color = c.ink3) },
                isError = error != null,
                supportingText = {
                    Row {
                        Text(error ?: " ", color = if (error != null) c.negative else c.ink3, modifier = Modifier.weight(1f))
                        Text("${value.length}/${CategorizeViewModel.MAX_LENGTH}", color = c.ink3)
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSubmit() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = c.ink, unfocusedBorderColor = c.line, cursorColor = c.ink,
                    focusedTextColor = c.ink, unfocusedTextColor = c.ink,
                ),
            )
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                EXAMPLES.forEach { ex ->
                    Text(
                        ex,
                        style = MaterialTheme.typography.labelMedium,
                        color = c.ink,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(c.stone)
                            .clickableSafe(enabled = !loading) { onExample(ex) }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }
            Button(
                onClick = onSubmit,
                enabled = canSubmit,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = c.ink, contentColor = c.onInk,
                    disabledContainerColor = c.line, disabledContentColor = c.ink3,
                ),
            ) {
                if (loading) {
                    CircularProgressIndicator(Modifier.size(18.dp), color = c.onInk, strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("Розпізнаю…", style = MaterialTheme.typography.labelLarge)
                } else {
                    Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Розпізнати", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun IdleHint() {
    val c = Aura.colors
    Text(
        "Можна писати як завгодно: «таксі додому 180», «Rozetka навушники \$45», «переказ мамі 500».",
        style = MaterialTheme.typography.bodyMedium,
        color = c.ink3,
        modifier = Modifier.padding(horizontal = 4.dp),
    )
}

@Composable
private fun LoadingCard() {
    val c = Aura.colors
    AuraCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("AI аналізує опис…", style = MaterialTheme.typography.titleMedium, color = c.ink)
            LinearProgressIndicator(Modifier.fillMaxWidth().clip(CircleShape), color = c.ink, trackColor = c.stone)
        }
    }
}

@Composable
private fun ResultCard(
    state: ResultState.Success,
    onChoose: (String, String) -> Unit,
    onSave: () -> Unit,
    onReset: () -> Unit,
) {
    val c = Aura.colors
    val r = state.result
    AuraCard {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CategoryTile(state.chosenCategory, size = 52)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(state.chosenLabel, style = MaterialTheme.typography.titleLarge, color = c.ink)
                    Text(
                        listOfNotNull(r.merchant, if (r.isIncome) "Дохід" else "Витрата").joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium, color = c.ink2,
                    )
                }
                if (r.amount != null) {
                    Text(
                        formatMoney(r.amount, r.currency, r.isIncome),
                        style = MaterialTheme.typography.titleLarge,
                        color = if (r.isIncome) c.positive else c.ink,
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row {
                    Text("Впевненість AI", style = MaterialTheme.typography.labelMedium, color = c.ink2, modifier = Modifier.weight(1f))
                    Text("${(r.confidence * 100).toInt()}%", style = MaterialTheme.typography.labelMedium, color = c.ink)
                }
                LinearProgressIndicator(
                    progress = { r.confidence.toFloat() },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                    color = if (r.lowConfidence) c.negative else c.ink,
                    trackColor = c.stone,
                    drawStopIndicator = {},
                )
            }

            Text(
                r.explanation,
                style = MaterialTheme.typography.bodyMedium,
                color = c.ink2,
                modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(c.lilac.copy(alpha = 0.6f)).padding(12.dp),
            )

            val options = buildList {
                if (!r.lowConfidence) add(Categorization.Alternative(r.category, r.label, r.confidence))
                addAll(r.alternatives)
            }
            if (options.size > 1) {
                Text(
                    if (r.lowConfidence) "Оберіть категорію:" else "Не та категорія? Змініть:",
                    style = MaterialTheme.typography.labelMedium, color = c.ink2,
                )
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    options.forEach { alt ->
                        FilterChip(
                            selected = alt.category == state.chosenCategory,
                            onClick = { onChoose(alt.category, alt.label) },
                            label = { Text("${alt.label} · ${(alt.confidence * 100).toInt()}%") },
                            leadingIcon = { Icon(categoryIcon(alt.category), contentDescription = null, modifier = Modifier.size(16.dp)) },
                            shape = CircleShape,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = c.ink, selectedLabelColor = c.onInk, selectedLeadingIconColor = c.onInk,
                                containerColor = c.stone, labelColor = c.ink, iconColor = c.ink,
                            ),
                            border = null,
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = onSave,
                    enabled = !(r.lowConfidence && state.chosenCategory == "other"),
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = c.ink, contentColor = c.onInk,
                        disabledContainerColor = c.line, disabledContentColor = c.ink3),
                ) { Text("Зберегти", style = MaterialTheme.typography.labelLarge) }
                TextButton(onClick = onReset) { Text("Скасувати", color = c.ink2) }
            }
            Text("Оброблено за ${"%.1f".format(r.processingMs)} мс", style = MaterialTheme.typography.labelMedium, color = c.ink3)
        }
    }
}

@Composable
private fun ErrorCard(error: AppError, onRetry: () -> Unit) {
    val c = Aura.colors
    AuraCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(c.blush),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.CloudOff, contentDescription = null, tint = c.ink) }
                Spacer(Modifier.width(12.dp))
                Text(
                    when (error) {
                        is AppError.Server -> "Не вдалося розпізнати"
                        AppError.BadResponse -> "Незрозуміла відповідь"
                        else -> "Немає з'єднання"
                    },
                    style = MaterialTheme.typography.titleMedium, color = c.ink,
                )
            }
            Text(error.userMessage, style = MaterialTheme.typography.bodyMedium, color = c.ink2)
            Button(
                onClick = onRetry,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = c.ink, contentColor = c.onInk),
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Повторити", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun SavedRow(tx: SavedTransaction) {
    val c = Aura.colors
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(c.surface).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryTile(tx.category, size = 40)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(tx.label, style = MaterialTheme.typography.titleMedium, color = c.ink)
            Text(tx.text, style = MaterialTheme.typography.bodyMedium, color = c.ink2, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (tx.amount != null) {
            Text(formatMoney(tx.amount, tx.currency, tx.isIncome), style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold, color = if (tx.isIncome) c.positive else c.ink)
        }
    }
}

@Composable
private fun CategoryTile(category: String, size: Int) {
    val c = Aura.colors
    val bg = when (category) {
        "groceries", "coffee", "clothing", "mobile", "salary", "cashback" -> c.butter
        "cafes", "housing", "gifts", "entertainment" -> c.blush
        "transfers", "other" -> c.stone
        else -> c.lilac
    }
    Box(
        Modifier.size(size.dp).clip(RoundedCornerShape((size * 0.32).dp)).background(bg),
        contentAlignment = Alignment.Center,
    ) {
        Icon(categoryIcon(category), contentDescription = null, tint = c.ink, modifier = Modifier.size((size * 0.46).dp))
    }
}

private fun categoryIcon(category: String): ImageVector = when (category) {
    "groceries" -> Icons.Filled.ShoppingCart
    "cafes" -> Icons.Filled.Restaurant
    "coffee" -> Icons.Filled.LocalCafe
    "transport" -> Icons.Filled.DirectionsCar
    "entertainment" -> Icons.Filled.ConfirmationNumber
    "subscriptions" -> Icons.Filled.Tv
    "housing" -> Icons.Filled.Home
    "health" -> Icons.Filled.Favorite
    "clothing" -> Icons.Filled.Checkroom
    "gifts" -> Icons.Filled.CardGiftcard
    "travel" -> Icons.Filled.Flight
    "mobile" -> Icons.Filled.PhoneIphone
    "shopping" -> Icons.Filled.ShoppingBag
    "transfers" -> Icons.Filled.SwapHoriz
    "salary" -> Icons.Filled.Work
    "freelance" -> Icons.Filled.Laptop
    "cashback" -> Icons.Filled.Savings
    else -> Icons.Filled.MoreHoriz
}

fun formatMoney(amount: Double, currency: String?, isIncome: Boolean): String {
    val symbol = when (currency) { "USD" -> "$"; "EUR" -> "€"; "PLN" -> "zł"; else -> "₴" }
    val nf = NumberFormat.getNumberInstance(Locale("uk", "UA")).apply {
        minimumFractionDigits = if (amount % 1.0 == 0.0) 0 else 2
        maximumFractionDigits = 2
    }
    return (if (isIncome) "+" else "−") + symbol + nf.format(amount)
}

/** clickable, що не реагує під час завантаження. */
private fun Modifier.clickableSafe(enabled: Boolean, onClick: () -> Unit): Modifier =
    this.clickable(enabled = enabled, onClick = onClick)
