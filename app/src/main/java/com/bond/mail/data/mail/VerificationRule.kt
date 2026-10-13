package com.bond.mail.data.mail

import java.util.UUID

/** A literal mail template, not executable regular-expression syntax. */
data class VerificationRule(
    val id: String = UUID.randomUUID().toString(),
    val template: String,
    val length: Int = 6,
    val alphabet: CodeAlphabet = CodeAlphabet.DIGITS,
    val enabled: Boolean = true,
) {
    fun isValid(): Boolean = template.length <= 160 && length in 4..12 &&
        template.split("{code}").size == 2 && template.replace("{code}", "").trim().length >= 2
}

enum class CodeAlphabet(val characters: String) {
    DIGITS("0-9"), ALPHANUMERIC("A-Za-z0-9"), LETTERS("A-Za-z"),
}
