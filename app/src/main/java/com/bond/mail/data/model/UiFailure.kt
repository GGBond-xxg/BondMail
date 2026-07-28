package com.bond.mail.data.model

data class UiFailure(
    val key: String,
    val args: List<String> = emptyList(),
)

class DuplicateAccountException : IllegalStateException()
