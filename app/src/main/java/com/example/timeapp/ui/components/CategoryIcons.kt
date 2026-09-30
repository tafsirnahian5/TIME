package com.example.timeapp.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.Book
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Coffee
import androidx.compose.material.icons.rounded.DirectionsBus
import androidx.compose.material.icons.rounded.FamilyRestroom
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LocalHospital
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Redeem
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.Work
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp

/**
 * The icons a category can carry (the PDF: "categories … with colors and icons"; "show
 * text/icons alongside every color"). Stored by key, so a key the app no longer knows simply
 * falls back to the colour dot.
 */
object CategoryIcons {
    val all: List<Pair<String, ImageVector>> = listOf(
        "work" to Icons.Rounded.Work, "code" to Icons.Rounded.Code, "school" to Icons.Rounded.School,
        "book" to Icons.Rounded.Book, "build" to Icons.Rounded.Build, "fitness" to Icons.Rounded.FitnessCenter,
        "health" to Icons.Rounded.LocalHospital, "home" to Icons.Rounded.Home, "family" to Icons.Rounded.FamilyRestroom,
        "food" to Icons.Rounded.Restaurant, "coffee" to Icons.Rounded.Coffee, "shopping" to Icons.Rounded.ShoppingBag,
        "transport" to Icons.Rounded.DirectionsBus, "travel" to Icons.Rounded.Flight, "phone" to Icons.Rounded.PhoneAndroid,
        "bills" to Icons.Rounded.Payments, "bank" to Icons.Rounded.AccountBalance, "savings" to Icons.Rounded.Savings,
        "fun" to Icons.Rounded.Movie, "gift" to Icons.Rounded.Redeem, "pets" to Icons.Rounded.Pets,
    )

    fun of(key: String?): ImageVector? = key?.let { k -> all.firstOrNull { it.first == k }?.second }
}

/**
 * How a list shows a record's category (Phase 20C): its icon in its colour, or the colour dot
 * when it has no icon. [size] is the icon's; the dot is drawn at half of it, as before.
 */
@androidx.compose.runtime.Composable
fun CategoryMark(color: Long?, icon: String?, size: androidx.compose.ui.unit.Dp = 16.dp) {
    val tint = color?.let { androidx.compose.ui.graphics.Color(it) } ?: com.example.timeapp.ui.theme.TimeThemeTokens.colors.acc
    val vector = CategoryIcons.of(icon)
    if (vector != null) {
        androidx.compose.material3.Icon(vector, contentDescription = null, tint = tint, modifier = androidx.compose.ui.Modifier.size(size))
    } else {
        androidx.compose.foundation.layout.Box(
            androidx.compose.ui.Modifier.size(size / 2).background(tint, androidx.compose.foundation.shape.CircleShape),
        )
    }
}
