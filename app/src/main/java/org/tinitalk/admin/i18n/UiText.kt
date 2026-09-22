package org.tinitalk.admin.i18n

import androidx.annotation.StringRes

/** Resolve messages at display time so an open screen follows language changes. */
data class UiText(@param:StringRes val resource: Int, val arguments: List<Any> = emptyList()) {
    fun resolve(): String = appString(resource, *arguments.toTypedArray())
}

fun uiText(@StringRes resource: Int, vararg arguments: Any): UiText =
    UiText(resource, arguments.toList())
