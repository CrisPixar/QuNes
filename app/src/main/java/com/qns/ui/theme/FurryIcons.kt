package com.qns.ui.theme

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Icon
import com.qns.R

/**
 * Состояние furry-темы. Используем явный MutableState<Boolean> — это безопаснее
 * при обращении из Java (ThemeRepository) и однозначно публикует изменения
 * в snapshot для Compose.
 *
 * Kotlin `var by mutableStateOf(...)` создаёт синтетический сеттер через
 * рефлексию, который из Java выглядит как `FurryTheme.INSTANCE.setEnabled(...)`
 * и теоретически может не оповестить snapshot, если вызывается не из
 * Compose-контекста. Поэтому вынесено в явный объект.
 */
object FurryTheme {
    private val state = mutableStateOf(false)
    val enabled: Boolean get() = state.value
    fun setEnabled(value: Boolean) { state.value = value }
    fun isEnabled(): Boolean = state.value
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
fun FurryIcon(
    seed: String,
    fallback: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    round: Boolean = false,
) {
    if (!FurryTheme.enabled) {
        Icon(fallback, contentDescription, modifier = modifier)
        return
    }
    val finalModifier = if (round) modifier.clip(CircleShape) else modifier
    Image(
        painter = painterResource(furryRes(seed)),
        contentDescription = contentDescription,
        modifier = finalModifier,
        contentScale = ContentScale.Crop,
    )
}

/**
 * Круглый аватар: в furry-теме — boykisser-картинка, иначе — инициалы на
 * цветном кружке (используется в списке чатов).
 */
@Composable
fun FurryAvatar(
    seed: String,
    initials: String,
    modifier: Modifier = Modifier,
) {
    if (!FurryTheme.enabled) {
        Surface(
            modifier = modifier,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Box(
                modifier = Modifier.size(48.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    initials,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        return
    }
    Image(
        painter = painterResource(furryRes(seed)),
        contentDescription = null,
        modifier = modifier.clip(CircleShape),
        contentScale = ContentScale.Crop,
    )
}
