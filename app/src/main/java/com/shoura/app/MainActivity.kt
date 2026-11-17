package com.shoura.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.File
import java.io.IOException

class MainActivity : AppCompatActivity() {
    private val prefs by lazy { getSharedPreferences("shoura", MODE_PRIVATE) }
    private var language = "ar"
    private var postType = "text"
    private var selectedUri: Uri? = null
    private var selectedFile: File? = null
    private var recorder: MediaRecorder? = null
    private var recordingForMessage = false
    private var chatReceiverId = 0
    private var conversationBody: LinearLayout? = null
    private var feed: LinearLayout? = null
    private var composer: EditText? = null
    private var notificationSwitch: Switch? = null
    private var locationSwitch: Switch? = null

    private val mediaPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@registerForActivityResult
        selectedUri = uri
        selectedFile = null
        Toast.makeText(this, t("تم اختيار ${displayName(uri)}", "Selected ${displayName(uri)}"), Toast.LENGTH_SHORT).show()
    }
    private val microphonePermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) beginRecording() else Toast.makeText(this, t("اسمح باستخدام الميكروفون لتسجيل الصوت.", "Allow microphone access to record audio."), Toast.LENGTH_LONG).show()
    }
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        prefs.edit().putBoolean("notifications_enabled", granted).apply()
        notificationSwitch?.isChecked = granted
    }
    private val locationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        prefs.edit().putBoolean("location_enabled", granted).apply()
        locationSwitch?.isChecked = granted
    }

    private val api by lazy { RetrofitClient.api(this) }
    private fun t(ar: String, en: String) = if (language == "ar") ar else en
    private val userId: Int get() = prefs.getInt("user_id", 0)

    override fun onCreate(savedInstanceState: Bundle?) {
        language = prefs.getString("language", "ar") ?: "ar"
        setTheme(if (prefs.getBoolean("dark", false)) com.google.android.material.R.style.Theme_MaterialComponents_DayNight_NoActionBar else com.google.android.material.R.style.Theme_MaterialComponents_Light_NoActionBar)
        AppCompatDelegate.setDefaultNightMode(if (prefs.getBoolean("dark", false)) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO)
        super.onCreate(savedInstanceState)
        render()
    }

    private fun render() {
        if (prefs.getString("access_token", null).isNullOrBlank()) renderAuth() else renderFeed()
    }

    private fun renderAuth(register: Boolean = false) {
        val (bg, card, fg, muted) = colors()
        val page = column(bg).apply { setPadding(dp(24), dp(28), dp(24), dp(24)) }
        page.addView(label(if (language == "ar") "شورى" else "Shoura", 30, fg, true))
        page.addView(label(t("مساحة للأفكار والحوارات", "A place for ideas and conversations"), 15, muted).apply { setPadding(0, dp(4), 0, dp(24)) })
        val cardView = column(card).apply { setPadding(dp(18), dp(18), dp(18), dp(18)) }
        cardView.addView(label(if (register) t("إنشاء حساب", "Create account") else t("تسجيل الدخول", "Sign in"), 21, fg, true))
        val name = if (register) input(t("الاسم", "Name"), fg, muted).also { cardView.addView(it) } else null
        val handle = if (register) input(t("اسم المستخدم", "Username"), fg, muted).also { cardView.addView(it) } else null
        val email = input(t("البريد الإلكتروني", "Email"), fg, muted).apply { inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS }
        val password = input(t("كلمة المرور (12 حرفًا على الأقل)", "Password (at least 12 characters)"), fg, muted).apply { inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD }
        cardView.addView(email); cardView.addView(password)
        cardView.addView(button(if (register) t("إنشاء الحساب", "Create account") else t("دخول", "Sign in")) {
            val emailValue = email.text.toString().trim()
            val passwordValue = password.text.toString()
            if (emailValue.isBlank() || passwordValue.length < 12 || (register && (name?.text.isNullOrBlank() || handle?.text.isNullOrBlank()))) {
                toast(t("أكمل الحقول، وكلمة المرور 12 حرفًا على الأقل.", "Complete the fields; password must be at least 12 characters.")); return@button
            }
            val request = if (register) api.register(RegisterRequest(name!!.text.toString().trim(), handle!!.text.toString().trim(), emailValue, passwordValue))
                else api.login(LoginRequest(emailValue, passwordValue))
            request.enqueue(object : Callback<AuthResponse> {
                override fun onResponse(call: Call<AuthResponse>, response: Response<AuthResponse>) {
                    val result = response.body()
                    if (response.isSuccessful && result != null) {
                        prefs.edit().putString("access_token", result.accessToken).putInt("user_id", result.user.id)
                            .putString("user_name", result.user.name).putString("user_handle", result.user.handle).apply()
                        renderFeed()
                    } else toast(response.errorBody()?.string()?.take(180) ?: t("تعذر تسجيل الدخول. تحقق من البيانات.", "Could not sign in. Check your details."))
                }
                override fun onFailure(call: Call<AuthResponse>, error: Throwable) = toast(networkError(error))
            })
        })
        cardView.addView(button(if (register) t("لديك حساب؟ سجّل الدخول", "Already have an account? Sign in") else t("إنشاء حساب جديد", "Create a new account"), outlined = true) { renderAuth(!register) })
        cardView.addView(label(t("تسجيل Google يتطلب إعداد Firebase للمشروع.", "Google sign-in requires Firebase project setup."), 12, muted).apply { setPadding(0, dp(14), 0, 0) })
        page.addView(cardView)
        setContentView(ScrollView(this).apply { addView(page) })
        applyDirection(page)
    }

    private fun renderFeed() {
        val (bg, card, fg, muted) = colors()
        val page = column(bg).apply { setPadding(dp(18), dp(14), dp(18), dp(28)) }
        val top = row().apply { gravity = Gravity.CENTER_VERTICAL }
        top.addView(label(if (language == "ar") "شورى" else "Shoura", 26, fg, true).apply { layoutParams = LinearLayout.LayoutParams(0, -2, 1f) })
        top.addView(action("⚙", fg) { showPrivacySettings(fg, muted) })
        top.addView(action(if (language == "ar") "EN" else "ع", fg) {
            language = if (language == "ar") "en" else "ar"; prefs.edit().putString("language", language).apply(); renderFeed()
        })
        top.addView(action(if (prefs.getBoolean("dark", false)) "☀" else "☾", fg) {
            val dark = !prefs.getBoolean("dark", false); prefs.edit().putBoolean("dark", dark).apply()
            AppCompatDelegate.setDefaultNightMode(if (dark) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO)
        })
        top.addView(action(t("خروج", "Log out"), muted) { prefs.edit().clear().apply(); renderAuth() })
        page.addView(top)
        page.addView(label(t("مساحة للأفكار والحوارات", "A place for ideas and conversations"), 14, muted).apply { setPadding(0, dp(3), 0, dp(14)) })
        val nav = row().apply { setBackgroundColor(card); setPadding(dp(4), dp(4), dp(4), dp(4)) }
        nav.addView(button(t("الخلاصة", "Feed"), outlined = true) { renderFeed() }, LinearLayout.LayoutParams(0, -2, 1f))
        nav.addView(button(t("الرسائل", "Messages"), outlined = true) { showContacts() }, LinearLayout.LayoutParams(0, -2, 1f))
        page.addView(nav)

        page.addView(label(t("اكتب شيئًا يستحق المشاركة", "Share something worth discussing"), 17, fg, true).apply { setPadding(0, dp(18), 0, dp(8)) })
        val compose = column(card).apply { setPadding(dp(14), dp(12), dp(14), dp(12)) }
        composer = input(t("ما الذي يدور في بالك؟", "What's on your mind?"), fg, muted).apply {
            minLines = 3; gravity = Gravity.TOP or Gravity.START
            textDirection = if (language == "ar") View.TEXT_DIRECTION_RTL else View.TEXT_DIRECTION_LTR
        }
        compose.addView(composer, LinearLayout.LayoutParams(-1, dp(84)))
        val actions = row().apply { gravity = Gravity.CENTER_VERTICAL }
        actions.addView(button(typeLabel(), outlined = true) { choosePostType() })
        actions.addView(button(t("ملف", "File"), outlined = true) { chooseMedia() })
        actions.addView(button(t("🎙 تسجيل", "🎙 Record"), outlined = true) { toggleRecording(false) })
        actions.addView(button(t("نشر", "Post")) { publishPost() }, LinearLayout.LayoutParams(0, -2, 1f))
        compose.addView(actions); page.addView(compose)
        page.addView(label(t("أحدث المنشورات", "Latest posts"), 19, fg, true).apply { setPadding(0, dp(22), 0, dp(9)) })
        feed = column(bg); page.addView(feed)
        setContentView(ScrollView(this).apply { addView(page) })
        applyDirection(page)
        fetchPosts()
    }

    private fun fetchPosts() {
        val target = feed ?: return
        target.removeAllViews()
        target.addView(label(t("جارٍ تحميل المنشورات…", "Loading posts…"), 14, Color.GRAY).apply { setPadding(0, dp(12), 0, dp(12)) })
        loadPostPage(target, null, null, clear = true)
    }

    private fun loadPostPage(target: LinearLayout, cursor: Int?, cursorCreatedAt: String?, clear: Boolean) {
        api.getPosts(cursor, cursorCreatedAt).enqueue(object : Callback<FeedPage> {
            override fun onResponse(call: Call<FeedPage>, response: Response<FeedPage>) {
                if (!response.isSuccessful) { target.removeAllViews(); target.addView(label(t("تعذر تحميل الخلاصة (${response.code()})", "Could not load feed (${response.code()})"), 14, Color.RED)); return }
                if (clear) target.removeAllViews()
                val page = response.body() ?: return
                val posts = page.items
                if (posts.isEmpty()) target.addView(label(t("لا توجد منشورات بعد. كن أول من ينشر!", "No posts yet. Be the first to post!"), 14, Color.GRAY))
                posts.forEach { renderPost(target, it) }
                if (page.hasMore && page.nextCursor != null && page.nextCursorCreatedAt != null) target.addView(button(t("تحميل المزيد", "Load more"), outlined = true) {
                    target.removeViewAt(target.childCount - 1)
                    loadPostPage(target, page.nextCursor, page.nextCursorCreatedAt, clear = false)
                })
            }
            override fun onFailure(call: Call<FeedPage>, error: Throwable) {
                target.removeAllViews(); target.addView(label(networkError(error), 14, Color.RED))
            }
        })
    }

    private fun renderPost(parent: LinearLayout, post: FeedPost) {
        val (_, card, fg, muted) = colors()
        val item = column(card).apply { setPadding(dp(16), dp(14), dp(16), dp(14)) }
        val author = post.user?.name ?: "@${post.user?.handle ?: post.userId}"
        item.addView(label("●  $author  ·  ${post.createdAt.take(10)}", 13, muted, true))
        if (post.content.isNotBlank()) item.addView(label(post.content, 16, fg).apply { setPadding(0, dp(10), 0, dp(8)) })
        val url = post.mediaUrl?.let { if (it.startsWith("http")) it else RetrofitClient.baseUrl.trimEnd('/') + it }
        if (url != null && post.mediaType == "video") {
            val player = VideoView(this).apply { setVideoURI(Uri.parse(url)); setOnPreparedListener { it.isLooping = false } }
            item.addView(player, LinearLayout.LayoutParams(-1, dp(210)))
            item.addView(button(t("تشغيل الفيديو", "Play video"), outlined = true) { player.start() })
            item.addView(button(t("✨ اشرح المقطع بالعربي", "✨ Explain this video"), outlined = true) { requestAi(post.mediaUrl!!, video = true) })
        } else if (url != null && post.mediaType == "audio") {
            item.addView(button(t("▶ تشغيل الفويس", "▶ Play voice"), outlined = true) { playAudio(url) })
            item.addView(button(t("ترجمة الفويس للعربية", "Translate voice to Arabic"), outlined = true) { requestAi(post.mediaUrl!!, video = false) })
        }
        item.addView(label("♡ ${post.likesCount}     ↻ ${post.retweetsCount}", 13, muted).apply { setPadding(0, dp(12), 0, 0) })
        parent.addView(item); parent.addView(View(this), LinearLayout.LayoutParams(1, dp(9)))
    }

    private fun choosePostType() {
        val names = arrayOf(t("تغريدة نصية", "Text post"), t("مقطع فيديو", "Video"), t("رسالة صوتية", "Voice note"))
        AlertDialog.Builder(this).setTitle(t("نوع المنشور", "Post type")).setItems(names) { _, selected ->
            postType = listOf("text", "video", "audio")[selected]
            if (postType != "text") chooseMedia()
            else { selectedUri = null; selectedFile = null }
        }.show()
    }

    private fun chooseMedia() {
        val types = when (postType) {
            "video" -> arrayOf("video/*")
            "audio" -> arrayOf("audio/*")
            else -> arrayOf("video/*", "audio/*")
        }
        mediaPicker.launch(types)
    }

    private fun publishPost() {
        val content = composer?.text?.toString()?.trim().orEmpty()
        if (content.isEmpty() && selectedUri == null && selectedFile == null) { toast(t("اكتب نصًا أو اختر ملفًا.", "Write a post or choose a file.")); return }
        val file = selectedFile
        val uri = selectedUri
        if (file != null) uploadAndCreate(file, content)
        else if (uri != null) copyUriToCache(uri) { staged -> if (staged == null) toast(t("تعذرت قراءة الملف.", "Could not read the selected file.")) else uploadAndCreate(staged, content) }
        else createPost(content, null, "text")
    }

    private fun uploadAndCreate(file: File, content: String) {
        upload(file) { media ->
            if (media == null) return@upload
            createPost(content, media.url, media.mediaType)
        }
    }

    private fun createPost(content: String, mediaUrl: String?, mediaType: String) {
        api.createPost(CreatePostRequest(content, mediaUrl, mediaType)).enqueue(object : Callback<FeedPost> {
            override fun onResponse(call: Call<FeedPost>, response: Response<FeedPost>) {
                if (response.isSuccessful) {
                    composer?.text?.clear(); selectedUri = null; selectedFile = null; postType = "text"
                    toast(t("تم نشر المنشور.", "Post published.")); fetchPosts()
                } else toast(response.errorBody()?.string()?.take(180) ?: t("تعذر النشر (${response.code()})", "Could not post (${response.code()})"))
            }
            override fun onFailure(call: Call<FeedPost>, error: Throwable) = toast(networkError(error))
        })
    }

    private fun copyUriToCache(uri: Uri, complete: (File?) -> Unit) {
        Thread {
            var staged: File? = null
            try {
                val name = displayName(uri)
                val extension = name.substringAfterLast('.', "bin").take(8).lowercase()
                val mime = contentResolver.getType(uri) ?: "application/octet-stream"
                if (mime.startsWith("video/")) postType = "video" else if (mime.startsWith("audio/")) postType = "audio"
                if (extension !in setOf("mp4", "webm", "mov", "mp3", "m4a", "aac", "wav", "ogg", "opus")) throw IOException("Unsupported media type")
                staged = File(cacheDir, "upload-${System.currentTimeMillis()}.$extension")
                contentResolver.openInputStream(uri)?.use { input -> staged!!.outputStream().use { output -> input.copyTo(output) } } ?: throw IOException("Cannot open file")
                if (staged!!.length() > 100L * 1024 * 1024) throw IOException("File exceeds 100 MB")
            } catch (_: Exception) { staged?.delete(); staged = null }
            runOnUiThread { complete(staged) }
        }.start()
    }

    private fun upload(file: File, complete: (MediaUpload?) -> Unit) {
        val ext = file.extension.lowercase()
        val mime = when (ext) {
            "mp4" -> if (postType == "video") "video/mp4" else "audio/mp4"
            "webm" -> "video/webm"; "mov" -> "video/quicktime"
            "mp3" -> "audio/mp3"; "m4a" -> "audio/m4a"; "aac" -> "audio/aac"
            "wav" -> "audio/wav"; "ogg" -> "audio/ogg"; "opus" -> "audio/opus"
            else -> "application/octet-stream"
        }
        val part = MultipartBody.Part.createFormData("file", file.name, file.asRequestBody(mime.toMediaTypeOrNull()))
        api.uploadMedia(part).enqueue(object : Callback<MediaUpload> {
            override fun onResponse(call: Call<MediaUpload>, response: Response<MediaUpload>) {
                val result = response.body()
                if (!response.isSuccessful || result == null) { toast(response.errorBody()?.string()?.take(180) ?: t("فشل رفع الملف.", "Media upload failed.")); complete(null) }
                else complete(result)
            }
            override fun onFailure(call: Call<MediaUpload>, error: Throwable) { toast(networkError(error)); complete(null) }
        })
    }

    private fun toggleRecording(forMessage: Boolean) {
        recordingForMessage = forMessage
        if (recorder != null) { finishRecording(); return }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) beginRecording()
        else microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
    }

    @Suppress("DEPRECATION")
    private fun beginRecording() {
        val file = File(cacheDir, "voice-${System.currentTimeMillis()}.m4a")
        try {
            recorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(file.absolutePath)
                prepare(); start()
            }
            selectedFile = file; selectedUri = null
            toast(t("جارٍ التسجيل… اضغط زر التسجيل مرة أخرى للإيقاف.", "Recording… tap record again to stop."))
        } catch (_: Exception) { recorder?.release(); recorder = null; file.delete(); toast(t("تعذر بدء التسجيل.", "Could not start recording.")) }
    }

    private fun finishRecording() {
        val active = recorder ?: return
        try { active.stop() } catch (_: RuntimeException) { selectedFile?.delete(); selectedFile = null }
        active.release(); recorder = null
        if (recordingForMessage) sendVoiceMessage() else {
            postType = "audio"; toast(t("تم تسجيل الفويس. اضغط نشر لإرساله.", "Voice recorded. Tap Post to send it."))
        }
    }

    private fun showContacts() {
        api.getUsers().enqueue(object : Callback<List<PublicContact>> {
            override fun onResponse(call: Call<List<PublicContact>>, response: Response<List<PublicContact>>) {
                if (!response.isSuccessful) { toast(t("تعذر تحميل المستخدمين. سجّل الدخول مجددًا.", "Could not load users. Sign in again.")); return }
                val users = response.body().orEmpty()
                if (users.isEmpty()) { toast(t("لا يوجد مستخدمون آخرون حتى الآن.", "No other users yet.")); return }
                AlertDialog.Builder(this@MainActivity).setTitle(t("اختر محادثة", "Choose a conversation"))
                    .setItems(users.map { "${it.name}  @${it.handle}" }.toTypedArray()) { _, index -> showConversation(users[index]) }.show()
            }
            override fun onFailure(call: Call<List<PublicContact>>, error: Throwable) = toast(networkError(error))
        })
    }

    private fun showConversation(contact: PublicContact) {
        chatReceiverId = contact.id
        val (_, card, fg, muted) = colors()
        val root = column(card).apply { setPadding(dp(14), dp(10), dp(14), dp(10)) }
        val scroll = ScrollView(this)
        conversationBody = column(card)
        scroll.addView(conversationBody)
        root.addView(scroll, LinearLayout.LayoutParams(-1, dp(300)))
        val entry = input(t("اكتب رسالة", "Write a message"), fg, muted)
        root.addView(entry)
        val actions = row()
        actions.addView(button(t("إرسال", "Send")) { sendTextMessage(contact.id, entry) })
        actions.addView(button(t("🎙 فويس", "🎙 Voice"), outlined = true) { toggleRecording(true) })
        root.addView(actions)
        val dialog = AlertDialog.Builder(this).setTitle("${contact.name}  @${contact.handle}")
            .setView(root).setNegativeButton(t("إغلاق", "Close"), null).create()
        dialog.show()
        loadConversation(contact.id)
    }

    private fun loadConversation(otherId: Int) {
        api.getMessages(otherId).enqueue(object : Callback<List<ChatMessage>> {
            override fun onResponse(call: Call<List<ChatMessage>>, response: Response<List<ChatMessage>>) {
                val body = conversationBody ?: return
                body.removeAllViews()
                if (!response.isSuccessful) { body.addView(label(t("تعذر تحميل المحادثة.", "Could not load conversation."), 13, Color.RED)); return }
                response.body().orEmpty().forEach { message ->
                    val (_, _, fg, muted) = colors()
                    val item = column(Color.TRANSPARENT)
                    item.addView(label(if (message.senderId == userId) t("أنت", "You") else t("${message.senderId}", "${message.senderId}"), 12, muted, true))
                    if (message.content.isNotBlank()) item.addView(label(message.content, 15, fg))
                    if (message.mediaType == "audio" && message.mediaUrl != null) {
                        val url = if (message.mediaUrl.startsWith("http")) message.mediaUrl else RetrofitClient.baseUrl.trimEnd('/') + message.mediaUrl
                        item.addView(button(t("▶ تشغيل الفويس", "▶ Play voice"), outlined = true) { playAudio(url) })
                    }
                    item.setPadding(0, dp(6), 0, dp(6)); body.addView(item)
                }
            }
            override fun onFailure(call: Call<List<ChatMessage>>, error: Throwable) = toast(networkError(error))
        })
    }

    private fun sendTextMessage(receiverId: Int, entry: EditText) {
        val text = entry.text.toString().trim()
        if (text.isBlank()) return
        api.sendMessage(SendMessageRequest(receiverId, text)).enqueue(object : Callback<ChatMessage> {
            override fun onResponse(call: Call<ChatMessage>, response: Response<ChatMessage>) {
                if (response.isSuccessful) { entry.text.clear(); loadConversation(receiverId) }
                else toast(t("تعذر إرسال الرسالة (${response.code()}).", "Message failed (${response.code()})."))
            }
            override fun onFailure(call: Call<ChatMessage>, error: Throwable) = toast(networkError(error))
        })
    }

    private fun sendVoiceMessage() {
        val file = selectedFile ?: return
        upload(file) { media ->
            if (media == null) return@upload
            api.sendMessage(SendMessageRequest(chatReceiverId, mediaUrl = media.url, mediaType = "audio"))
                .enqueue(object : Callback<ChatMessage> {
                    override fun onResponse(call: Call<ChatMessage>, response: Response<ChatMessage>) {
                        if (response.isSuccessful) { selectedFile = null; loadConversation(chatReceiverId) }
                        else toast(t("تعذر إرسال الفويس.", "Could not send voice note."))
                    }
                    override fun onFailure(call: Call<ChatMessage>, error: Throwable) = toast(networkError(error))
                })
        }
    }

    private fun playAudio(url: String) {
        try {
            MediaPlayer().apply {
                setDataSource(url); setOnPreparedListener { it.start() }
                setOnErrorListener { player, _, _ -> player.release(); toast(t("تعذر تشغيل الصوت.", "Could not play audio.")); true }
                prepareAsync()
            }
        } catch (_: Exception) { toast(t("تعذر تشغيل الصوت.", "Could not play audio.")) }
    }

    private fun requestAi(mediaUrl: String, video: Boolean) {
        val confirmation = if (video)
            t("سيُرسل هذا المقطع إلى Gemini لتحليله وإرجاع شرح بالعربية. هل تريد المتابعة؟", "This clip will be sent to Gemini for analysis and an Arabic explanation. Continue?")
        else t("سيُرسل هذا الفويس إلى Gemini لتفريغه وترجمته إلى العربية. هل تريد المتابعة؟", "This voice note will be sent to Gemini for transcription and Arabic translation. Continue?")
        AlertDialog.Builder(this).setTitle(t("معالجة بالذكاء الاصطناعي", "AI processing")).setMessage(confirmation)
            .setNegativeButton(t("إلغاء", "Cancel"), null)
            .setPositiveButton(t("متابعة", "Continue")) { _, _ -> runAi(mediaUrl, video) }.show()
    }

    private fun runAi(mediaUrl: String, video: Boolean) {
        val progress = AlertDialog.Builder(this).setMessage(t("جارٍ التحليل…", "Analyzing…")).setCancelable(false).create()
        progress.show()
        val request = AiMediaRequest(mediaUrl)
        val call = if (video) api.explainVideo(request) else api.translateAudio(request)
        call.enqueue(object : Callback<AiResult> {
            override fun onResponse(call: Call<AiResult>, response: Response<AiResult>) {
                progress.dismiss()
                val result = response.body()
                if (response.isSuccessful && result != null)
                    AlertDialog.Builder(this@MainActivity).setTitle(if (video) t("شرح المقطع", "Video explanation") else t("تفريغ وترجمة الصوت", "Voice transcript and translation"))
                        .setMessage(result.text).setPositiveButton(t("تم", "Done"), null).show()
                else toast(response.errorBody()?.string()?.take(250) ?: t("تعذر إكمال التحليل (${response.code()}).", "AI analysis failed (${response.code()})."))
            }
            override fun onFailure(call: Call<AiResult>, error: Throwable) { progress.dismiss(); toast(networkError(error)) }
        })
    }

    private fun showPrivacySettings(fg: Int, muted: Int) {
        val content = column(Color.TRANSPARENT).apply { setPadding(dp(20), dp(12), dp(20), dp(8)) }
        content.addView(label(t("الخصوصية والأذونات", "Privacy and permissions"), 19, fg, true))
        notificationSwitch = Switch(this).apply {
            text = t("الإشعارات", "Notifications"); isChecked = prefs.getBoolean("notifications_enabled", false)
            setOnCheckedChangeListener { _, enabled ->
                if (enabled && Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                    notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                else prefs.edit().putBoolean("notifications_enabled", enabled).apply()
            }
        }
        locationSwitch = Switch(this).apply {
            text = t("السماح بالموقع التقريبي", "Allow approximate location")
            isChecked = prefs.getBoolean("location_enabled", false) && ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
            setOnCheckedChangeListener { _, enabled ->
                if (enabled && ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED)
                    locationPermission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                else prefs.edit().putBoolean("location_enabled", enabled).apply()
            }
        }
        content.addView(notificationSwitch); content.addView(locationSwitch)
        content.addView(label(t("لا يقرأ التطبيق موقعك إلا بعد إضافة ميزة تستخدمه.", "The app will not read location until a feature is added that uses it."), 12, muted))
        content.addView(button(t("حذف الحساب والبيانات", "Delete account and data")) {
            AlertDialog.Builder(this).setTitle(t("حذف الحساب؟", "Delete account?"))
                .setMessage(t("سيؤدي ذلك إلى حذف الحساب والمنشورات والرسائل نهائيًا.", "This permanently deletes your account, posts, and messages."))
                .setNegativeButton(t("إلغاء", "Cancel"), null)
                .setPositiveButton(t("حذف نهائيًا", "Delete permanently")) { _, _ ->
                    api.deleteMyAccount().enqueue(object : Callback<Void> {
                        override fun onResponse(call: Call<Void>, response: Response<Void>) {
                            if (response.isSuccessful) { prefs.edit().clear().apply(); renderAuth(); toast(t("تم حذف الحساب.", "Account deleted.")) }
                            else toast(t("تعذر حذف الحساب (${response.code()}).", "Could not delete account (${response.code()})."))
                        }
                        override fun onFailure(call: Call<Void>, error: Throwable) = toast(networkError(error))
                    })
                }.show()
        })
        AlertDialog.Builder(this).setView(content).setPositiveButton(t("تم", "Done"), null).show()
    }

    private fun typeLabel() = when (postType) { "video" -> t("🎬 فيديو", "🎬 Video"); "audio" -> t("🎙 فويس", "🎙 Voice"); else -> t("✎ تغريدة", "✎ Post") }
    private fun colors(): Quad {
        val dark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        return Quad(if (dark) Color.rgb(17, 20, 27) else Color.rgb(247, 248, 252), if (dark) Color.rgb(28, 33, 43) else Color.WHITE,
            if (dark) Color.rgb(239, 242, 250) else Color.rgb(31, 39, 57), if (dark) Color.rgb(163, 173, 193) else Color.rgb(111, 122, 145))
    }

    private fun column(color: Int) = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(color); layoutDirection = if (language == "ar") View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR }
    private fun row() = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; layoutDirection = if (language == "ar") View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR }
    private fun label(value: String, size: Int, color: Int, bold: Boolean = false) = TextView(this).apply { text = value; textSize = size.toFloat(); setTextColor(color); if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD) }
    private fun input(hintText: String, fg: Int, muted: Int) = EditText(this).apply { hint = hintText; setTextColor(fg); setHintTextColor(muted); setPadding(dp(10), dp(8), dp(10), dp(8)) }
    private fun button(title: String, outlined: Boolean = false, click: () -> Unit) = Button(this).apply { text = title; isAllCaps = false; textSize = 13f; setOnClickListener { click() }; if (outlined) setTextColor(Color.rgb(71, 103, 213)) }
    private fun action(title: String, color: Int, click: () -> Unit) = TextView(this).apply { text = title; textSize = 14f; setTextColor(color); gravity = Gravity.CENTER; setPadding(dp(8), dp(8), dp(8), dp(8)); setOnClickListener { click() } }
    private fun applyDirection(view: View) { view.layoutDirection = if (language == "ar") View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR }
    private fun displayName(uri: Uri): String = contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else "media" } ?: "media"
    private fun networkError(error: Throwable) = t("تعذر الاتصال بالخادم: ${error.localizedMessage ?: ""}", "Could not connect to server: ${error.localizedMessage ?: ""}")
    private fun toast(value: String) = Toast.makeText(this, value, Toast.LENGTH_LONG).show()
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    override fun onStop() {
        if (recorder != null) { try { recorder?.stop() } catch (_: Exception) { }; recorder?.release(); recorder = null }
        super.onStop()
    }
    private data class Quad(val bg: Int, val card: Int, val fg: Int, val muted: Int)
}
