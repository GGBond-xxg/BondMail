package com.bond.mail

/** Fixed launcher entry points. No account or message identifiers are accepted from the launcher. */
enum class LauncherShortcut(val intentAction: String) {
    COMPOSE("com.bond.mail.shortcut.COMPOSE"),
    ADD_ACCOUNT("com.bond.mail.shortcut.ADD_ACCOUNT"),
    REFRESH("com.bond.mail.shortcut.REFRESH");

    companion object {
        fun fromAction(action: String?): LauncherShortcut? = entries.firstOrNull { it.intentAction == action }
    }
}

data class LauncherShortcutRequest(val shortcut: LauncherShortcut, val sequence: Long)
