package com.bond.mail.background

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.bond.mail.MailApplication
import com.bond.mail.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** A focused, transparent notification destination so Android permits clipboard access. */
class CopyVerificationCodeActivity : ComponentActivity() {
    private var started = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus || started) return
        started = true
        lifecycleScope.launch {
            try {
                val container = (application as MailApplication).container
                val message = intent.getStringExtra("message_id")?.let { container.repository.messageNow(it) }
                if (message != null && window.decorView.hasWindowFocus()) {
                    val copied = container.copyVerificationCode(message)
                    if (copied && android.os.Build.VERSION.SDK_INT < 33) {
                        Toast.makeText(this@CopyVerificationCodeActivity, R.string.verification_code_copied, Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                Toast.makeText(this@CopyVerificationCodeActivity, R.string.verification_code_copy_failed, Toast.LENGTH_SHORT).show()
            } finally {
                finish()
            }
        }
    }
}
