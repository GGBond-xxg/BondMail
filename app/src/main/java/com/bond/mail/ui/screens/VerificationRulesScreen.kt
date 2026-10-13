package com.bond.mail.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.bond.mail.data.mail.*
import com.bond.mail.ui.i18n.tr
import com.bond.mail.ui.theme.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun VerificationRulesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val store = remember(context) { VerificationRuleStore(context) }
    val rules by store.changes.collectAsState(initial = store.rules())
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<VerificationRule?>(null) }
    var saving by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    fun persist(updated: List<VerificationRule>, closeEditor: Boolean = false) {
        if (saving) return
        saving = true; failed = false
        scope.launch {
            try { store.save(updated); if (closeEditor) editing = null }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { failed = true }
            finally { saving = false }
        }
    }
    Surface(color = MaterialTheme.bondSurfaces.page) {
        Column(Modifier.fillMaxSize()) {
            BondTopAppBar(tr("verification_rules"), navigationIcon = {
                BondIconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, tr("back")) }
            })
            LazyColumn(Modifier.weight(1f).navigationBarsPadding(), contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                item {
                    Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.bondSurfaces.content) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(tr("verification_rules_help"), style = MaterialTheme.typography.bodyMedium)
                            Text(tr("verification_rules_example"), style = MaterialTheme.typography.bodyMedium)
                            Text(tr("verification_rules_languages"), style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                item { BondSecondaryButton(onClick = { editing = VerificationRule(template = "") },
                    enabled = !saving && rules.size < VerificationRuleStore.MAX_RULES, modifier = Modifier.fillMaxWidth()) {
                    Text(tr("verification_rule_add"))
                } }
                if (failed) item { Text(tr("verification_rule_save_failed"), color = MaterialTheme.colorScheme.error) }
                if (rules.isEmpty()) item { Text(tr("verification_rules_empty"), style = MaterialTheme.typography.bodyMedium) }
                items(rules, key = { it.id }) { rule ->
                    Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.bondSurfaces.content) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(rule.template, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                                BondSwitch(checked = rule.enabled, onCheckedChange = { enabled ->
                                    persist(rules.map { if (it.id == rule.id) it.copy(enabled = enabled) else it })
                                }, enabled = !saving)
                            }
                            Text(tr("verification_rule_summary", rule.length.toString(), tr(rule.alphabet.labelKey())),
                                style = MaterialTheme.typography.bodySmall)
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                BondSecondaryButton(onClick = { editing = rule }, enabled = !saving) { Text(tr("edit")) }
                                BondFormAction(onClick = { persist(rules.filterNot { it.id == rule.id }) }, enabled = !saving) { Text(tr("delete")) }
                            }
                        }
                    }
                }
            }
        }
    }
    editing?.let { rule ->
        VerificationRuleEditor(rule, saving, failed, onDismiss = { if (!saving) { editing = null; failed = false } },
            onSave = { changed -> persist(rules.filterNot { it.id == changed.id } + changed, closeEditor = true) })
    }
}

private fun CodeAlphabet.labelKey() = when (this) {
    CodeAlphabet.DIGITS -> "verification_rule_digits"
    CodeAlphabet.ALPHANUMERIC -> "verification_rule_alphanumeric"
    CodeAlphabet.LETTERS -> "verification_rule_letters"
}

@Composable
private fun VerificationRuleEditor(rule: VerificationRule, saving: Boolean, failed: Boolean,
    onDismiss: () -> Unit, onSave: (VerificationRule) -> Unit) {
    var template by remember(rule.id) { mutableStateOf(rule.template) }
    var length by remember(rule.id) { mutableIntStateOf(rule.length) }
    var alphabet by remember(rule.id) { mutableStateOf(rule.alphabet) }
    var sample by remember(rule.id) { mutableStateOf("") }
    var result by remember { mutableStateOf<String?>(null) }
    var tested by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    val draft = rule.copy(template = template.trim(), length = length, alphabet = alphabet)
    val scope = rememberCoroutineScope()
    val exampleTemplate = tr("verification_rule_example_template")
    val exampleMail = tr("verification_rule_example_mail")
    LaunchedEffect(template, length, alphabet, sample) { tested = false; result = null }
    BondAlertDialog(onDismissRequest = onDismiss, title = { Text(tr("verification_rule_edit")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(tr("verification_rule_template_help"), style = MaterialTheme.typography.bodySmall)
                BondFormField(template, { template = it.take(160) }, Modifier.fillMaxWidth(),
                    label = { Text(tr("verification_rule_template")) }, enabled = !saving && !testing,
                    minLines = 2, maxLines = 4, isError = template.isNotBlank() && !draft.isValid())
                BondFormAction(onClick = { template = exampleTemplate; sample = exampleMail; length = 6; alphabet = CodeAlphabet.DIGITS },
                    enabled = !saving && !testing) { Text(tr("verification_rule_fill_example")) }
                RuleChoice(tr("verification_rule_length"), length.toString(), (4..12).toList(),
                    { it.toString() }, enabled = !saving && !testing) { length = it }
                RuleChoice(tr("verification_rule_alphabet"), tr(alphabet.labelKey()), CodeAlphabet.entries,
                    { tr(it.labelKey()) }, enabled = !saving && !testing) { alphabet = it }
                BondFormField(sample, { sample = it.take(8_000) }, Modifier.fillMaxWidth(),
                    label = { Text(tr("verification_rule_sample")) }, minLines = 3, maxLines = 6, enabled = !saving && !testing)
                BondSecondaryButton(onClick = {
                    testing = true
                    val capturedDraft = draft
                    val capturedSample = sample
                    scope.launch {
                        try { result = withContext(Dispatchers.Default) { VerificationCodeDetector.testRule(capturedSample, capturedDraft) }; tested = true }
                        finally { testing = false }
                    }
                }, enabled = draft.isValid() && sample.isNotBlank() && !testing && !saving) { Text(tr("verification_rule_test")) }
                if (tested) Text(if (result != null) tr("verification_rule_match", result!!) else tr("verification_rule_no_match"),
                    color = if (result != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                Text(tr("verification_rule_local_note"), style = MaterialTheme.typography.bodySmall)
                if (failed) Text(tr("verification_rule_save_failed"), color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = { BondFormAction(onClick = { onSave(draft) }, enabled = draft.isValid() && !saving && !testing) { Text(tr("save")) } },
        dismissButton = { BondFormAction(onClick = onDismiss, enabled = !saving) { Text(tr("cancel")) } },
    )
}

@Composable
private fun <T> RuleChoice(title: String, selected: String, options: List<T>, label: @Composable (T) -> String,
    enabled: Boolean, onSelect: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.labelMedium)
        Box {
            BondSecondaryButton(onClick = { expanded = true }, enabled = enabled) { Text(selected) }
            BondDropdownMenu(expanded, { expanded = false }) {
                options.forEach { option -> DropdownMenuItem(text = { Text(label(option)) },
                    onClick = { onSelect(option); expanded = false }) }
            }
        }
    }
}
