package com.example.timeapp.ui.components


import com.example.timeapp.data.isPartialTakaInput
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType

/**
 * The big in-card amount, as drawn on AddCostScreen's "Estimated total" and
 * RecordPaymentScreen's "Amount actually paid" — but editable.
 *
 * Both screens previously rendered this as a plain `Text("  $amount")` over a state
 * variable nothing ever assigned to. It looked exactly like the mock and was completely
 * inert: a planned cost could only ever be saved as Tk 0, and a payment could only be
 * recorded for the full remaining balance. This keeps the mock's typography and puts a
 * real `BasicTextField` behind it, filtered through [isPartialTakaInput] so the field
 * accepts a decimal point while it is being typed but never a malformed amount.
 */
@Composable
fun InlineAmountInput(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    currencyStyle: androidx.compose.ui.text.TextStyle = androidx.compose.material3.MaterialTheme.typography.titleLarge,
) {
    val colors = com.example.timeapp.ui.theme.TimeThemeTokens.colors
    androidx.compose.foundation.layout.Row(
        verticalAlignment = androidx.compose.ui.Alignment.Bottom,
        modifier = modifier,
    ) {
        Text(com.example.timeapp.data.DisplayFormats.currencyLabel, style = currencyStyle, color = colors.acc)
        androidx.compose.foundation.text.BasicTextField(
            value = value,
            onValueChange = { new -> if (isPartialTakaInput(new)) onValueChange(new) },
            textStyle = androidx.compose.material3.MaterialTheme.typography.displaySmall.copy(
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
            ),
            singleLine = true,
            cursorBrush = androidx.compose.ui.graphics.SolidColor(colors.acc),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier
                .padding(start = com.example.timeapp.ui.theme.Dimens.s8)
                .widthIn(min = 120.dp),
            decorationBox = { inner ->
                if (value.isEmpty()) {
                    Text(
                        "0",
                        style = androidx.compose.material3.MaterialTheme.typography.displaySmall,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                inner()
            },
        )
    }
}
