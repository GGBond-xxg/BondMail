package com.bond.mail.data.mail

import org.junit.Assert.*
import org.junit.Test

class VerificationCodeDetectorTest {
    @Test fun commonInternationalHintsAndLocalizedDigits() {
        listOf(
            "Your verification code is 482631", "認証コードは482631です", "인증 코드는 482631",
            "Votre code de vérification est 482631", "Ihr Sicherheitscode ist 482631",
            "Su código de verificación es 482631", "Seu código de verificação é 482631",
            "Il codice di verifica è 482631", "Kode verifikasi adalah 482631",
            "Ваш код подтверждения: 482631", "رمز التحقق هو ٤٨٢٦٣١",
            "รหัสยืนยันคือ ๔๘๒๖๓๑", "सत्यापन कोड है ४८२६३१",
        ).forEach { body -> assertEquals(body, "482631", VerificationCodeDetector.detect("Notice", body)) }
        assertEquals("482631", VerificationCodeDetector.detect("", "", "<p>Code de vérification :</p><strong>482631</strong>"))
    }

    @Test fun customTemplatesMatchBeforeAfterAndLiteralPunctuation() {
        val rule = VerificationRule(template = "您的登录口令为：{code}")
        assertEquals("003821", VerificationCodeDetector.testRule("您的登录口令为：００３８２１", rule))
        assertEquals("003821", VerificationCodeDetector.detect("", "您的登录口令为：003821", rules = listOf(rule)))
        assertEquals("003821", VerificationCodeDetector.detect("", "", "<p>您的登录口令为：</p><b>003821</b>", listOf(rule)))
        assertEquals("ABCXYZ", VerificationCodeDetector.testRule("ABCXYZ is your access token", VerificationRule(
            template = "{code} is your access token", alphabet = CodeAlphabet.LETTERS)))
        assertEquals("A1B2C3D4", VerificationCodeDetector.testRule("Access [token]:\nA1B2C3D4", VerificationRule(
            template = "Access [token]: {code}", length = 8, alphabet = CodeAlphabet.ALPHANUMERIC)))
        assertNull(VerificationCodeDetector.testRule("Access token: A1B2C3D4", VerificationRule(
            template = "Access [token]: {code}", length = 8, alphabet = CodeAlphabet.ALPHANUMERIC)))
        assertNull(VerificationCodeDetector.detect("", "您的登录口令为：003821", rules = listOf(rule.copy(enabled = false))))
    }

    @Test fun customTemplatesValidateAndDoNotGuessConflicts() {
        listOf("{code}", "No placeholder", "{code} and {code}", "a{code}").forEach {
            assertFalse(VerificationRule(template = it).isValid())
        }
        assertFalse(VerificationRule(template = "Access: {code}", length = 100).isValid())
        val rule = VerificationRule(template = "Access: {code}")
        assertNull(VerificationCodeDetector.testRule("Access: 1234567", rule))
        assertNull(VerificationCodeDetector.testRule("Access: 123456 and Access: 654321", rule))
        assertNull(VerificationCodeDetector.detect("Verification code: 111111", "Access: 222222", rules = listOf(rule)))
        assertNull(VerificationCodeDetector.testRule("Access: 123.45", rule))
    }

    @Test fun subjectAndBodyAndLeadingZeros() {
        assertEquals("003821", VerificationCodeDetector.detect("您的验证码003821请勿泄露", ""))
        assertEquals("003821", VerificationCodeDetector.detect("验证码：003821", ""))
        assertEquals("003821", VerificationCodeDetector.detect("验证码：003821", "您的验证码为：003821"))
        assertEquals("765432", VerificationCodeDetector.detect("Welcome", "Your verification code is 765432"))
        assertEquals("482631", VerificationCodeDetector.detect("482631 is your verification code", ""))
        assertEquals("871625", VerificationCodeDetector.detect("登入確認", "驗證碼為：871625\n有效期為5分鐘。"))
        assertEquals("8G3K2P", VerificationCodeDetector.detect("Security code: 8G3K2P", ""))
        assertEquals("654321", VerificationCodeDetector.detect("Sign-in", "One-time password: 654 321"))
        assertEquals("654321", VerificationCodeDetector.detect("Sign-in", "OTP: 654-321"))
    }

    @Test fun withdrawalAmountAndAddressAreNotTheCode() {
        val body = "您好，交易员：\n您正在申请提币，提币金额：376.517 USDT\n提币地址：0x1234567890abcdef1234567890abcdef12345678\n" +
            "请仔细确认您的提币地址。\n验证码为：\n482631\n验证码有效时间为 5 分钟。"
        assertEquals("482631", VerificationCodeDetector.detect("[Example]提币验证码", body))
    }

    @Test fun htmlTagsEntitiesAndFullWidthDigits() {
        assertEquals("003821", VerificationCodeDetector.detect("通知", "", "<style>.code{color:#123456}</style><p>验证码为：</p><b>００３８２１</b><p>5分钟内有效</p>"))
        assertEquals("234567", VerificationCodeDetector.detect("Login", "", "<p>Your verification code:&nbsp;<span>234567</span></p>"))
        assertEquals("003821", VerificationCodeDetector.detect("通知", "验证码：００３８２１"))
    }

    @Test fun ordinaryNumbersAndLinksStayHidden() {
        listOf(
            "订单号：123456", "Payment: 1000 USD", "Phone: +86 13812345678", "Order shipped 654321",
            "验证码已发送至尾号为1234的手机", "验证码有效时间 2026-10-13", "Verification code: https://example.com/123456",
            "验证码：376.517 USDT", "验证码：0x123456abcdef", "验证码：123456789012", "Your coupon code: SAVE2026",
            "验证码错误，请拨打客服电话 123456", "Verification code: support@example123456.com",
        ).forEach { assertNull(it, VerificationCodeDetector.detect("Notification", it)) }
    }

    @Test fun competingCodesAndQuotedHistoryAreNotGuessed() {
        assertNull(VerificationCodeDetector.detect("验证码：123456", "验证码：654321"))
        assertNull(VerificationCodeDetector.detect("Re: hello", "On Monday wrote:\nVerification code: 123456"))
        assertNull(VerificationCodeDetector.detect("Hello", "", "<blockquote>Verification code: 123456</blockquote>"))
        assertNull(VerificationCodeDetector.detect("Verification code", "1234 dollars withdrawn on 2026-10-13"))
    }
}
