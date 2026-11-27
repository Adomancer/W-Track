package wtrack.app.feature.common

import android.content.Context
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource

sealed interface UiText {
    data class Dynamic(val value: String) : UiText

    class Resource(
        @param:StringRes val resId: Int,
        vararg val args: Any,
    ) : UiText {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Resource) return false
            return resId == other.resId && args.contentEquals(other.args)
        }

        override fun hashCode(): Int = 31 * resId + args.contentHashCode()

        override fun toString(): String = "Resource(resId=$resId, args=${args.contentToString()})"
    }

    class PluralResource(
        @param:PluralsRes val resId: Int,
        val quantity: Int,
        vararg val args: Any,
    ) : UiText {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is PluralResource) return false
            return resId == other.resId && quantity == other.quantity && args.contentEquals(other.args)
        }

        override fun hashCode(): Int = 31 * (31 * resId + quantity) + args.contentHashCode()

        override fun toString(): String =
            "PluralResource(resId=$resId, quantity=$quantity, args=${args.contentToString()})"
    }

    @Composable
    fun asString(): String = when (this) {
        is Dynamic -> value
        is Resource -> {
            val resolved = args.map { arg -> if (arg is UiText) arg.asString() else arg }.toTypedArray()
            if (resolved.isEmpty()) stringResource(resId) else stringResource(resId, *resolved)
        }
        is PluralResource -> {
            val resolved = args.map { arg -> if (arg is UiText) arg.asString() else arg }.toTypedArray()
            if (resolved.isEmpty()) {
                pluralStringResource(resId, quantity)
            } else {
                pluralStringResource(resId, quantity, *resolved)
            }
        }
    }

    fun asString(context: Context): String = when (this) {
        is Dynamic -> value
        is Resource -> {
            val resolved = args.map { arg -> if (arg is UiText) arg.asString(context) else arg }.toTypedArray()
            if (resolved.isEmpty()) context.getString(resId) else context.getString(resId, *resolved)
        }
        is PluralResource -> {
            val resolved = args.map { arg -> if (arg is UiText) arg.asString(context) else arg }.toTypedArray()
            if (resolved.isEmpty()) {
                context.resources.getQuantityString(resId, quantity)
            } else {
                context.resources.getQuantityString(resId, quantity, *resolved)
            }
        }
    }

    companion object {
        fun fromError(error: Throwable, @StringRes fallbackResId: Int): UiText {
            val msg = error.message?.trim()
            return if (!msg.isNullOrEmpty()) Dynamic(msg) else Resource(fallbackResId)
        }
    }
}
