package com.cusil.neoeffects

import android.content.Context
import android.util.Base64
import org.json.JSONObject
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

// Local accounts (stored on this device only). Passwords are never stored in plain text.
object AuthStore {
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("neo_auth", Context.MODE_PRIVATE)

    private fun users(ctx: Context): JSONObject {
        return try {
            JSONObject(prefs(ctx).getString("users", "{}") ?: "{}")
        } catch (e: Exception) {
            JSONObject()
        }
    }

    private fun hash(pass: String, salt: ByteArray): String {
        val spec = PBEKeySpec(pass.toCharArray(), salt, 10000, 256)
        val f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
        return Base64.encodeToString(f.generateSecret(spec).encoded, Base64.NO_WRAP)
    }

    fun current(ctx: Context): Pair<String, String>? {
        val email = prefs(ctx).getString("session", null) ?: return null
        val u = users(ctx).optJSONObject(email) ?: return null
        return Pair(u.optString("name", "Creator"), email)
    }

    fun signUp(ctx: Context, name: String, emailIn: String, pass: String, pass2: String): String? {
        val email = emailIn.trim().lowercase()
        if (name.trim().length < 2) return "Enter your name"
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) return "Enter a valid email"
        if (pass.length < 6) return "Password must be at least 6 characters"
        if (pass != pass2) return "Passwords do not match"
        val all = users(ctx)
        if (all.has(email)) return "This email already has an account. Sign in instead."
        val salt = ByteArray(16)
        SecureRandom().nextBytes(salt)
        val u = JSONObject()
        u.put("name", name.trim())
        u.put("salt", Base64.encodeToString(salt, Base64.NO_WRAP))
        u.put("hash", hash(pass, salt))
        all.put(email, u)
        prefs(ctx).edit().putString("users", all.toString()).putString("session", email).apply()
        return null
    }

    fun signIn(ctx: Context, emailIn: String, pass: String): String? {
        val email = emailIn.trim().lowercase()
        val u = users(ctx).optJSONObject(email) ?: return "No account found for this email"
        val salt = Base64.decode(u.optString("salt"), Base64.NO_WRAP)
        if (hash(pass, salt) != u.optString("hash")) return "Wrong password"
        prefs(ctx).edit().putString("session", email).apply()
        return null
    }

    fun signOut(ctx: Context) {
        prefs(ctx).edit().remove("session").apply()
    }
}
