package app.pausa.ui.apps

import androidx.annotation.StringRes
import app.pausa.R

/** Texts for the actions that were refused or failed, shared by the screens that change rules. They are string ids so they follow the language. */
object RuleMessages {
    @StringRes const val LOCKED = R.string.msg_rule_locked
    @StringRes const val SAVE_FAILED = R.string.msg_save_failed
}
