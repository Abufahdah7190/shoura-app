package com.example.shoura_app

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class ProfileActivity : AppCompatActivity() {

    private lateinit var etEmail: EditText
    private lateinit var etPhone: EditText
    private lateinit var btnSaveChanges: Button
    private lateinit var btnLogout: Button
    private lateinit var btnDeleteAccount: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        etEmail = findViewById(R.id.etEmail)
        etPhone = findViewById(R.id.etPhone)
        btnSaveChanges = findViewById(R.id.btnSaveChanges)
        btnLogout = findViewById(R.id.btnLogout)
        btnDeleteAccount = findViewById(R.id.btnDeleteAccount)

        val prefs = getSharedPreferences("ShouraPrefs", MODE_PRIVATE)
        val userId = prefs.getInt("USER_ID", 1)
        etEmail.setText(prefs.getString("USER_EMAIL", ""))
        etPhone.setText(prefs.getString("USER_PHONE", ""))

        // 1. حفظ تعديلات الإيميل والجوال
        btnSaveChanges.setOnClickListener {
            val newEmail = etEmail.text.toString().trim()
            val newPhone = etPhone.text.toString().trim()
            val dto = UpdateUserDto(newEmail, newPhone)

            RetrofitClient.apiService.updateProfile(userId, dto).enqueue(object : Callback<ResponseBody> {
                override fun onResponse(call: Call<ResponseBody>, response: Response<ResponseBody>) {
                    if (response.isSuccessful) {
                        prefs.edit()
                            .putString("USER_EMAIL", newEmail)
                            .putString("USER_PHONE", newPhone)
                            .apply()
                        Toast.makeText(this@ProfileActivity, "تم تحديث البيانات بنجاح", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@ProfileActivity, "فشل التحديث", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                    Toast.makeText(this@ProfileActivity, "خطأ: ${t.message}", Toast.LENGTH_SHORT).show()
                }
            })
        }

        // 2. تسجيل الخروج
        btnLogout.setOnClickListener {
            performLogout()
        }

        // 3. تأكيد حذف الحساب
        btnDeleteAccount.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("تأكيد الحذف")
                .setMessage("هل أنت متأكد من رغبتك في حذف حسابك نهائياً؟")
                .setPositiveButton("نعم، احذف") { _, _ -> deleteUserAccount(userId) }
                .setNegativeButton("إلغاء", null)
                .show()
        }
    }

    private fun deleteUserAccount(userId: Int) {
        RetrofitClient.apiService.deleteAccount(userId).enqueue(object : Callback<ResponseBody> {
            override fun onResponse(call: Call<ResponseBody>, response: Response<ResponseBody>) {
                if (response.isSuccessful) {
                    Toast.makeText(this@ProfileActivity, "تم حذف الحساب", Toast.LENGTH_SHORT).show()
                    performLogout()
                } else {
                    Toast.makeText(this@ProfileActivity, "تعذر حذف الحساب", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                Toast.makeText(this@ProfileActivity, "خطأ: ${t.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun performLogout() {
        getSharedPreferences("ShouraPrefs", MODE_PRIVATE).edit().clear().apply()
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}