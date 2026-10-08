package com.example.calculator

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.math.BigDecimal

data class CalculationRecord(
    val id: Long = System.currentTimeMillis() + (0..1000).random(),
    val expression: String,
    val result: String
)

/**
 * Normal, realistic, polished modern calculator UI with memory and history log support.
 *
 * Features:
 * - Basic arithmetic (+, -, ×, ÷, %, ., AC, backspace)
 * - Memory operations: MC (Memory Clear), MR (Memory Recall), M+ (Memory Add), M- (Memory Subtract)
 * - Calculation History Log overlay drawer / panel
 * - Secret trigger evaluation (e.g. 2 + 9 =) triggers biometric vault immediately without logging or revealing secrets.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalculatorScreen(
    onSecretTriggered: () -> Unit,
    secretExpression: String,
    onOpenSettings: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val engine = remember { CalculatorEngine() }

    var expression by remember { mutableStateOf("") }
    var displayResult by remember { mutableStateOf("0") }
    var hasEvaluated by remember { mutableStateOf(false) }

    // Memory register
    var memoryValue by remember { mutableStateOf<BigDecimal?>(null) }

    // History Log
    var historyList by remember { mutableStateOf(listOf<CalculationRecord>()) }
    var showHistorySheet by remember { mutableStateOf(false) }

    fun onDigit(d: String) {
        if (hasEvaluated) {
            expression = d
            displayResult = d
            hasEvaluated = false
        } else {
            expression += d
            displayResult = expression
        }
    }

    fun onOperator(op: String) {
        hasEvaluated = false
        if (expression.isEmpty()) {
            if (op == "-") {
                expression = "-"
                displayResult = "-"
            }
            return
        }
        val lastChar = expression.last()
        if (lastChar in "+-×÷") {
            expression = expression.dropLast(1) + op
        } else {
            expression += op
        }
        displayResult = expression
    }

    fun onDecimal() {
        if (hasEvaluated) {
            expression = "0."
            displayResult = expression
            hasEvaluated = false
            return
        }
        val lastOpIndex = expression.indexOfLast { it in "+-×÷" }
        val currentSegment = if (lastOpIndex >= 0) expression.substring(lastOpIndex + 1) else expression
        if (!currentSegment.contains(".")) {
            expression = if (currentSegment.isEmpty()) expression + "0." else expression + "."
            displayResult = expression
        }
    }

    fun onClear() {
        expression = ""
        displayResult = "0"
        hasEvaluated = false
    }

    fun onBackspace() {
        if (hasEvaluated) {
            onClear()
            return
        }
        if (expression.isNotEmpty()) {
            expression = expression.dropLast(1)
            displayResult = if (expression.isEmpty()) "0" else expression
        }
    }

    fun onPercent() {
        if (expression.isNotEmpty()) {
            try {
                val evaluated = engine.evaluate(expression)
                val num = evaluated.toDoubleOrNull()
                if (num != null) {
                    val percentVal = (num / 100.0).toString()
                    expression = percentVal
                    displayResult = percentVal
                    hasEvaluated = true
                }
            } catch (ignored: Exception) {}
        }
    }

    fun onEquals() {
        if (expression.isEmpty()) return

        // Check secret trigger: 2 + 9 =
        if (engine.isSecretTrigger(expression, secretExpression)) {
            // Secret operation recognized! Immediately trigger biometric without showing normal result or saving to history!
            onSecretTriggered()
            return
        }

        // Normal calculation evaluation
        val result = engine.evaluate(expression)
        val fullExpr = expression
        displayResult = result
        expression = result
        hasEvaluated = true

        // Record to history if valid evaluation
        if (result != "Error") {
            historyList = listOf(CalculationRecord(expression = fullExpr, result = result)) + historyList.take(49)
        }
    }

    // Memory functions
    fun onMemoryClear() {
        memoryValue = null
    }

    fun onMemoryRecall() {
        memoryValue?.let { mem ->
            val memStr = mem.stripTrailingZeros().toPlainString()
            if (hasEvaluated) {
                expression = memStr
                displayResult = memStr
                hasEvaluated = false
            } else {
                val lastOpIndex = expression.indexOfLast { it in "+-×÷" }
                expression = if (lastOpIndex >= 0) {
                    expression.substring(0, lastOpIndex + 1) + memStr
                } else {
                    memStr
                }
                displayResult = expression
            }
        }
    }

    fun onMemoryAdd() {
        try {
            val currVal = BigDecimal(engine.evaluate(if (expression.isNotEmpty()) expression else displayResult))
            memoryValue = (memoryValue ?: BigDecimal.ZERO).add(currVal)
        } catch (ignored: Exception) {}
    }

    fun onMemorySubtract() {
        try {
            val currVal = BigDecimal(engine.evaluate(if (expression.isNotEmpty()) expression else displayResult))
            memoryValue = (memoryValue ?: BigDecimal.ZERO).subtract(currVal)
        } catch (ignored: Exception) {}
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Calculation History button only - no settings icon visible
                IconButton(
                    onClick = { showHistorySheet = true },
                    modifier = Modifier.testTag("calculator_history_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = "Calculation History",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.Bottom
        ) {
            // Display Area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.BottomEnd
            ) {
                Column(
                    horizontalAlignment = Alignment.End,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (memoryValue != null) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.padding(bottom = 6.dp)
                        ) {
                            Text(
                                text = "M",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    if (expression.isNotEmpty() && hasEvaluated) {
                        Text(
                            text = expression,
                            fontSize = 22.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.End
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                    Text(
                        text = displayResult,
                        fontSize = if (displayResult.length > 9) 42.sp else 58.sp,
                        fontWeight = FontWeight.Light,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.End,
                        modifier = Modifier.testTag("calculator_display")
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Memory Row: MC, MR, M+, M-
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 480.dp)
                    .align(Alignment.CenterHorizontally),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MemoryButton("MC", enabled = memoryValue != null) { onMemoryClear() }
                MemoryButton("MR", enabled = memoryValue != null) { onMemoryRecall() }
                MemoryButton("M+", enabled = true) { onMemoryAdd() }
                MemoryButton("M-", enabled = true) { onMemorySubtract() }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Keypad Grid
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 480.dp)
                    .align(Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Row 1: AC, Backspace, %, ÷
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CalcButton("AC", Modifier.weight(1f), CalcButtonType.FUNCTION) { onClear() }
                    CalcIconButton(Icons.Default.Backspace, "Backspace", Modifier.weight(1f), CalcButtonType.FUNCTION) { onBackspace() }
                    CalcButton("%", Modifier.weight(1f), CalcButtonType.FUNCTION) { onPercent() }
                    CalcButton("÷", Modifier.weight(1f), CalcButtonType.OPERATOR) { onOperator("÷") }
                }

                // Row 2: 7, 8, 9, ×
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CalcButton("7", Modifier.weight(1f), CalcButtonType.DIGIT) { onDigit("7") }
                    CalcButton("8", Modifier.weight(1f), CalcButtonType.DIGIT) { onDigit("8") }
                    CalcButton("9", Modifier.weight(1f), CalcButtonType.DIGIT) { onDigit("9") }
                    CalcButton("×", Modifier.weight(1f), CalcButtonType.OPERATOR) { onOperator("×") }
                }

                // Row 3: 4, 5, 6, -
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CalcButton("4", Modifier.weight(1f), CalcButtonType.DIGIT) { onDigit("4") }
                    CalcButton("5", Modifier.weight(1f), CalcButtonType.DIGIT) { onDigit("5") }
                    CalcButton("6", Modifier.weight(1f), CalcButtonType.DIGIT) { onDigit("6") }
                    CalcButton("−", Modifier.weight(1f), CalcButtonType.OPERATOR) { onOperator("-") }
                }

                // Row 4: 1, 2, 3, +
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CalcButton("1", Modifier.weight(1f), CalcButtonType.DIGIT) { onDigit("1") }
                    CalcButton("2", Modifier.weight(1f), CalcButtonType.DIGIT) { onDigit("2") }
                    CalcButton("3", Modifier.weight(1f), CalcButtonType.DIGIT) { onDigit("3") }
                    CalcButton("+", Modifier.weight(1f), CalcButtonType.OPERATOR) { onOperator("+") }
                }

                // Row 5: 0, ., =
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CalcButton("0", Modifier.weight(2.05f), CalcButtonType.DIGIT) { onDigit("0") }
                    CalcButton(".", Modifier.weight(1f), CalcButtonType.DIGIT) { onDecimal() }
                    CalcButton("=", Modifier.weight(1f), CalcButtonType.EQUALS) { onEquals() }
                }
            }
        }
    }

    // Calculation History Bottom Sheet
    if (showHistorySheet) {
        ModalBottomSheet(
            onDismissRequest = { showHistorySheet = false },
            sheetState = rememberModalBottomSheetState()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Calculation History",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    if (historyList.isNotEmpty()) {
                        IconButton(
                            onClick = { historyList = emptyList() },
                            modifier = Modifier.testTag("clear_history_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteSweep,
                                contentDescription = "Clear History",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (historyList.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No history yet",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 350.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(historyList, key = { it.id }) { record ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        expression = record.result
                                        displayResult = record.result
                                        hasEvaluated = true
                                        showHistorySheet = false
                                    },
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    horizontalAlignment = Alignment.End
                                ) {
                                    Text(
                                        text = record.expression,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "= ${record.result}",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

enum class CalcButtonType {
    DIGIT,
    OPERATOR,
    FUNCTION,
    EQUALS
}

@Composable
fun CalcButton(
    text: String,
    modifier: Modifier = Modifier,
    type: CalcButtonType = CalcButtonType.DIGIT,
    onClick: () -> Unit
) {
    val containerColor = when (type) {
        CalcButtonType.DIGIT -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        CalcButtonType.FUNCTION -> MaterialTheme.colorScheme.surfaceVariant
        CalcButtonType.OPERATOR -> MaterialTheme.colorScheme.primaryContainer
        CalcButtonType.EQUALS -> MaterialTheme.colorScheme.primary
    }

    val contentColor = when (type) {
        CalcButtonType.DIGIT -> MaterialTheme.colorScheme.onSurface
        CalcButtonType.FUNCTION -> MaterialTheme.colorScheme.onSurfaceVariant
        CalcButtonType.OPERATOR -> MaterialTheme.colorScheme.onPrimaryContainer
        CalcButtonType.EQUALS -> MaterialTheme.colorScheme.onPrimary
    }

    Surface(
        onClick = onClick,
        modifier = modifier
            .aspectRatio(if (text == "0") 2.05f else 1f)
            .testTag("calc_button_$text"),
        shape = CircleShape,
        color = containerColor,
        contentColor = contentColor,
        tonalElevation = 2.dp
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Text(
                text = text,
                fontSize = if (type == CalcButtonType.OPERATOR || type == CalcButtonType.EQUALS) 30.sp else 26.sp,
                fontWeight = if (type == CalcButtonType.OPERATOR || type == CalcButtonType.EQUALS) FontWeight.SemiBold else FontWeight.Normal,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun CalcIconButton(
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    type: CalcButtonType = CalcButtonType.FUNCTION,
    onClick: () -> Unit
) {
    val containerColor = MaterialTheme.colorScheme.surfaceVariant
    val contentColor = MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        onClick = onClick,
        modifier = modifier
            .aspectRatio(1f)
            .testTag("calc_button_$contentDescription"),
        shape = CircleShape,
        color = containerColor,
        contentColor = contentColor,
        tonalElevation = 2.dp
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
fun RowScope.MemoryButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    FilledTonalButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .weight(1f)
            .height(38.dp)
            .testTag("memory_button_$text"),
        contentPadding = PaddingValues(0.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(
            text = text,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}
