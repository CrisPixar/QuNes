package com.qns.ui.theme

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.Icon
import com.qns.R

/**
 * Состояние furry-темы. Чтение через snapshot state внутри композиции
 * перекомпоновывает иконки при смене темы (без сетевых загрузок — картинки лежат в drawable).
 */
object FurryTheme {
    var enabled by mutableStateOf(false)
}

/**
 * Возвращает drawable-resource для boykisser-картинки, детерминированно выбирая
 * один из 5 по seed (имя иконки). Одна и та же картинка для одного экрана/иконки
 * стабильна при каждой recomposition (без мерцания).
 */
fun furryRes(seed: String): Int {
    val hash = (seed + "-qns").hashCode()
    return when (Math.floorMod(hash, 5)) {
        0 -> R.drawable.boykisser_1
        1 -> R.drawable.boykisser_2
        2 -> R.drawable.boykisser_3
        3 -> R.drawable.boykisser_4
        else -> R.drawable.boykisser_5
    }
}

/**
 * Иконка, которая в furry-теме заменяет Material-иконку на boykisser-картинку.
 * В обычных темах рисует обычный Material icon.
 */
@Composable
fun FurryIcon(seed: String, fallback: ImageVector, contentDescription: String?, modifier: Modifier = Modifier) {
    if (!FurryTheme.enabled) {
        Icon(fallback, contentDescription, modifier = modifier)
        return
    }
    Image(
        painter = painterResource(furryRes(seed)),
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = ContentScale.Crop,
    )
}
