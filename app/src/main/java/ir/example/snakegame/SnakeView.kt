package ir.example.snakegame

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min
import kotlin.random.Random

class SnakeView(context: Context, private val host: UiHost) : View(context) {

    interface UiHost {
        fun showNameInput(error: String?, onSubmit: (String) -> Unit)
        fun hideNameInput()
    }

    // ---------- Language ----------
    private val prefs: SharedPreferences =
        context.getSharedPreferences("snake_prefs", Context.MODE_PRIVATE)
    private var lang = prefs.getString("lang", "fa") ?: "fa"

    private val STR_FA = mapOf(
        "title" to "بازی مار", "play" to "شروع بازی", "shopSnakes" to "🐍 فروشگاه مارها",
        "shopGrounds" to "🗺️ فروشگاه زمین‌ها", "settings" to "⚙️ تنظیمات", "leaderboard" to "🏆 جدول رده‌بندی",
        "back" to "بازگشت", "snakesTab" to "🐍 مارها", "groundsTab" to "🗺️ زمین‌ها",
        "select" to "انتخاب", "selected" to "انتخاب‌شده", "score" to "امتیاز", "best" to "رکورد",
        "paused" to "مکث", "resume" to "ادامه بازی", "toMenu" to "بازگشت به منو", "gameOver" to "باختی!",
        "again" to "دوباره بازی کن", "language" to "زبان بازی", "fa" to "فارسی", "en" to "English",
        "hello" to "سلام", "empty" to "هنوز امتیازی ثبت نشده", "coins" to "سکه"
    )
    private val STR_EN = mapOf(
        "title" to "Snake Game", "play" to "Play", "shopSnakes" to "🐍 Snake Shop",
        "shopGrounds" to "🗺️ Ground Shop", "settings" to "⚙️ Settings", "leaderboard" to "🏆 Leaderboard",
        "back" to "Back", "snakesTab" to "🐍 Snakes", "groundsTab" to "🗺️ Grounds",
        "select" to "Select", "selected" to "Selected", "score" to "Score", "best" to "Best",
        "paused" to "Paused", "resume" to "Resume", "toMenu" to "Main Menu", "gameOver" to "Game Over!",
        "again" to "Play Again", "language" to "Game Language", "fa" to "فارسی", "en" to "English",
        "hello" to "Hi", "empty" to "No scores yet", "coins" to "Coins"
    )
    private fun tr(k: String): String = (if (lang == "en") STR_EN else STR_FA)[k] ?: k

    // ---------- Online leaderboard (dreamlo) ----------
    private val DREAMLO_PRIVATE = "F3p3gpQVz0aOqLPOgIf7KQ6oIF6yp_8E6EfyjQHXP0Ww"
    private val DREAMLO_PUBLIC = "6a9b13078f40bc135058564e"
    private val mainHandler = Handler(Looper.getMainLooper())
    private var onlineLeaderboard: MutableList<Pair<String, Int>> = loadLeaderboardCache()

    private fun fetchLeaderboard(after: (() -> Unit)? = null) {
        Thread {
            try {
                val conn = URL("http://dreamlo.com/lb/$DREAMLO_PUBLIC/json").openConnection() as HttpURLConnection
                conn.connectTimeout = 6000; conn.readTimeout = 6000
                val text = conn.inputStream.bufferedReader().readText()
                val list = parseDreamloJson(text)
                mainHandler.post { onlineLeaderboard = list.toMutableList(); saveLeaderboardCache(onlineLeaderboard); after?.invoke(); invalidate() }
            } catch (e: Exception) { mainHandler.post { after?.invoke() } }
        }.start()
    }

    private fun submitScoreOnline(name: String, score: Int) {
        Thread {
            try {
                val encoded = URLEncoder.encode(name, "UTF-8")
                val conn = URL("http://dreamlo.com/lb/$DREAMLO_PRIVATE/add/$encoded/$score").openConnection() as HttpURLConnection
                conn.connectTimeout = 6000
                conn.inputStream.bufferedReader().readText()
            } catch (e: Exception) { }
            mainHandler.post { fetchLeaderboard() }
        }.start()
    }

    private fun checkNameAvailability(name: String, callback: (Boolean) -> Unit) {
        Thread {
            try {
                val conn = URL("http://dreamlo.com/lb/$DREAMLO_PUBLIC/json").openConnection() as HttpURLConnection
                conn.connectTimeout = 6000
                val text = conn.inputStream.bufferedReader().readText()
                val taken = parseDreamloJson(text).any { it.first.equals(name, ignoreCase = true) }
                mainHandler.post { callback(!taken) }
            } catch (e: Exception) { mainHandler.post { callback(true) } }
        }.start()
    }

    private fun parseDreamloJson(json: String): List<Pair<String, Int>> {
        return try {
            val root = JSONObject(json)
            val lb = root.optJSONObject("dreamlo")?.optJSONObject("leaderboard") ?: return emptyList()
            val entryEl = lb.opt("entry") ?: return emptyList()
            val list = mutableListOf<Pair<String, Int>>()
            when (entryEl) {
                is JSONArray -> for (i in 0 until entryEl.length()) {
                    val o = entryEl.getJSONObject(i)
                    list.add(o.optString("name") to (o.optString("score", "0").toIntOrNull() ?: 0))
                }
                is JSONObject -> list.add(entryEl.optString("name") to (entryEl.optString("score", "0").toIntOrNull() ?: 0))
            }
            list.sortedByDescending { it.second }.take(10)
        } catch (e: Exception) { emptyList() }
    }

    private fun saveLeaderboardCache(list: List<Pair<String, Int>>) {
        prefs.edit().putString("cached_leaderboard", list.joinToString("|") { "${it.first},${it.second}" }).apply()
    }
    private fun loadLeaderboardCache(): MutableList<Pair<String, Int>> {
        val s = prefs.getString("cached_leaderboard", "") ?: ""
        if (s.isBlank()) return mutableListOf()
        return s.split("|").mapNotNull {
            val p = it.split(","); if (p.size == 2) p[0] to (p[1].toIntOrNull() ?: 0) else null
        }.toMutableList()
    }

    // ---------- Skins inspired by real snakes ----------
    data class Skin(val name: String, val nameEn: String, val c1: String, val c2: String, val c3: String, val pattern: String, val price: Int)
    private val skins = listOf(
        Skin("مار سبز باغی", "Garden Grass Snake", "#6B8E23", "#3D5B1F", "#A8C060", "solid", 0),
        Skin("کبرای مصری", "Egyptian Cobra", "#8B8558", "#4A4A2E", "#C9C48A", "band", 35),
        Skin("پیتون سلطنتی", "Royal Python", "#5C4033", "#2B1D14", "#8A6A4E", "blotch", 45),
        Skin("مار مرجانی", "Coral Snake", "#D62828", "#F4D35E", "#1A1A1A", "ring", 55),
        Skin("مار زنگی غربی", "Western Rattlesnake", "#A9825C", "#5C4526", "#D8C39A", "diamond", 50),
        Skin("بوآی زمردی", "Emerald Tree Boa", "#0B6E4F", "#083D2C", "#EAF4E8", "zigzag", 70),
        Skin("مار ذرت", "Corn Snake", "#E07A3E", "#8C3B1B", "#F5C56B", "blotch", 40),
        Skin("مار پادشاهی", "King Snake", "#1A1A1A", "#000000", "#F2F2F2", "band", 42),
        Skin("مامبای سیاه", "Black Mamba", "#4A4A52", "#232327", "#8A8A95", "solid", 75),
        Skin("وایپر شاخدار", "Horned Viper", "#C9B27C", "#8A7346", "#EFE4C4", "speckle", 60),
        Skin("مار آبی", "Water Moccasin", "#4B5D3A", "#252F1C", "#7C8F5F", "band", 38),
        Skin("مار شیری", "Milk Snake", "#B3202E", "#0D0D0D", "#F4C430", "ring", 58),
        Skin("تایپان ساحلی", "Coastal Taipan", "#A9702F", "#5E3A14", "#D9A85C", "solid", 65),
        Skin("افعی اروپایی", "European Adder", "#8A8A8A", "#4A4A4A", "#D6D6D6", "zigzag", 48),
        Skin("مار علفزار", "Garter Snake", "#2E5B2E", "#173617", "#D9C94A", "stripe", 28),
        Skin("بوآی شنی", "Sand Boa", "#C2A165", "#7A5F35", "#E8D6A8", "blotch", 44),
        Skin("پیتون برمه‌ای", "Burmese Python", "#6E5535", "#3A2A18", "#B79A6A", "blotch", 68),
        Skin("مار مسی‌سر", "Copperhead", "#B06A3A", "#6B3A1C", "#E0B487", "diamond", 52),
        Skin("مار طلایی افسانه‌ای", "Legendary Gold", "#FFD700", "#B8860B", "#FFF8DC", "sparkle", 150),
        Skin("مار یخی افسانه‌ای", "Legendary Frost", "#AEE7F0", "#5FB6C9", "#FFFFFF", "sparkle", 140)
    )
    data class Ground(val name: String, val nameEn: String, val bg: String, val line: String, val pattern: String, val price: Int)
    private val grounds = listOf(
        Ground("سنگ تیره", "Dark Stone", "#101418", "#1c2128", "plain", 0),
        Ground("شن بیابان", "Desert Sand", "#8a734a", "#a68856", "dots", 20),
        Ground("چمن", "Grass", "#2f4d2f", "#3d613d", "dots", 20),
        Ground("برف", "Snow", "#dfeaf0", "#c7d8e0", "dots", 25),
        Ground("گدازه", "Lava", "#3a1410", "#6b1f16", "crack", 35),
        Ground("زیر آب", "Underwater", "#0b3d4a", "#146b7d", "wave", 30)
    )
    private fun skinName(s: Skin) = if (lang == "en") s.nameEn else s.name
    private fun groundName(g: Ground) = if (lang == "en") g.nameEn else g.name

    private var coins = prefs.getInt("coins", 0)
    private var bestScore = prefs.getInt("best_score", 0)
    private var unlockedSnakes = (prefs.getStringSet("unlocked_snakes", setOf(skins[0].name)) ?: setOf(skins[0].name)).toMutableSet()
    private var selectedSnake = prefs.getString("selected_snake", skins[0].name) ?: skins[0].name
    private var unlockedGrounds = (prefs.getStringSet("unlocked_grounds", setOf(grounds[0].name)) ?: setOf(grounds[0].name)).toMutableSet()
    private var selectedGround = prefs.getString("selected_ground", grounds[0].name) ?: grounds[0].name
    private var playerName = prefs.getString("player_name", "") ?: ""

    private fun saveProgress() {
        prefs.edit()
            .putInt("coins", coins).putInt("best_score", bestScore)
            .putStringSet("unlocked_snakes", unlockedSnakes).putString("selected_snake", selectedSnake)
            .putStringSet("unlocked_grounds", unlockedGrounds).putString("selected_ground", selectedGround)
            .putString("player_name", playerName).putString("lang", lang)
            .apply()
    }
    private fun curSkin() = skins.first { it.name == selectedSnake }
    private fun curGround() = grounds.first { it.name == selectedGround }

    // ---------- Game state ----------
    enum class Screen { NAME, MENU, PLAYING, SHOP, SETTINGS, LEADERBOARD, GAME_OVER }
    private var screen = if (playerName.isBlank()) Screen.NAME else Screen.MENU
    private var shopTab = "snakes"
    private var paused = false

    private val cellCount = 17
    private var cellSize = 0f

    // Body is stored in UNBOUNDED virtual coordinates (never clamped) and only
    // wrapped into [0,cellCount) at render/collision time — this is what makes
    // crossing an edge smooth instead of glitchy.
    private var snake = ArrayDeque<Pair<Int, Int>>()
    private var prevSnake: List<Pair<Int, Int>> = emptyList()
    private var lastTickTime = SystemClock.uptimeMillis()
    private var direction = Direction.RIGHT
    private var pendingDirection = Direction.RIGHT
    private var food = Pair(5, 5)
    private var runScore = 0
    private var runCoins = 0
    private var touchStartX = 0f
    private var touchStartY = 0f

    enum class Direction { UP, DOWN, LEFT, RIGHT }
    private fun wrap(v: Int) = ((v % cellCount) + cellCount) % cellCount
    private fun wrapF(v: Float) = ((v % cellCount) + cellCount) % cellCount

    private val activeButtons = mutableListOf<Pair<RectF, () -> Unit>>()
    private var pauseIconRect: RectF? = null
    private var groundBitmap: android.graphics.Bitmap? = null

    private val bgPaint = Paint().apply { color = Color.parseColor("#101418") }
    private val overlayPaint = Paint().apply { color = Color.parseColor("#CC000000") }
    private val cardPaint = Paint().apply { color = Color.parseColor("#1c2128") }
    private val buttonPaint = Paint().apply { color = Color.parseColor("#4CAF50") }
    private val buttonDisabledPaint = Paint().apply { color = Color.parseColor("#33404a") }
    private val titlePaint = Paint().apply { color = Color.WHITE; textSize = 78f; isAntiAlias = true; textAlign = Paint.Align.CENTER; isFakeBoldText = true }
    private val textPaint = Paint().apply { color = Color.WHITE; textSize = 44f; isAntiAlias = true; textAlign = Paint.Align.CENTER }
    private val smallTextPaint = Paint().apply { color = Color.parseColor("#B0BEC5"); textSize = 34f; isAntiAlias = true; textAlign = Paint.Align.CENTER }
    private val buttonTextPaint = Paint().apply { color = Color.WHITE; textSize = 40f; isAntiAlias = true; textAlign = Paint.Align.CENTER; isFakeBoldText = true }

    private val tickLoop = object : Runnable {
        override fun run() {
            if (screen == Screen.PLAYING && !paused) {
                prevSnake = snake.toList()
                update()
                lastTickTime = SystemClock.uptimeMillis()
            }
            postDelayed(this, 150L)
        }
    }
    private val renderLoop = object : Runnable {
        override fun run() { invalidate(); postDelayed(this, 16L) }
    }

    init {
        post(tickLoop); post(renderLoop)
        fetchLeaderboard()
        if (screen == Screen.NAME) requestNameEntry(null)
    }

    private fun requestNameEntry(error: String?) {
        host.showNameInput(error) { entered -> attemptRegisterName(entered) }
    }
    private fun attemptRegisterName(name: String) {
        if (name.isBlank()) { requestNameEntry(if (lang == "en") "Enter a name" else "یه اسم بنویس"); return }
        checkNameAvailability(name) { available ->
            if (available) {
                playerName = name; saveProgress(); host.hideNameInput(); screen = Screen.MENU; invalidate()
            } else {
                requestNameEntry(if (lang == "en") "Name already taken" else "این اسم قبلاً گرفته شده، یکی دیگه بنویس")
            }
        }
    }

    // ---------- Game logic ----------
    private fun startGame() {
        snake.clear()
        val start = cellCount / 2
        snake.addLast(Pair(start, start)); snake.addLast(Pair(start - 1, start)); snake.addLast(Pair(start - 2, start))
        prevSnake = snake.toList()
        direction = Direction.RIGHT; pendingDirection = Direction.RIGHT
        runScore = 0; runCoins = 0; paused = false
        spawnFood(); screen = Screen.PLAYING; lastTickTime = SystemClock.uptimeMillis()
    }
    private fun spawnFood() {
        var f: Pair<Int, Int>
        do { f = Pair(Random.nextInt(cellCount), Random.nextInt(cellCount)) }
        while (snake.any { wrap(it.first) == f.first && wrap(it.second) == f.second })
        food = f
    }
    private fun update() {
        direction = pendingDirection
        val head = snake.first()
        var hvx = head.first; var hvy = head.second
        when (direction) {
            Direction.UP -> hvy--; Direction.DOWN -> hvy++; Direction.LEFT -> hvx--; Direction.RIGHT -> hvx++
        }
        val whx = wrap(hvx); val why = wrap(hvy)
        if (snake.any { wrap(it.first) == whx && wrap(it.second) == why }) {
            if (runScore > bestScore) bestScore = runScore
            coins += runCoins
            saveProgress()
            if (runScore > 0) submitScoreOnline(playerName, runScore)
            screen = Screen.GAME_OVER
            return
        }
        snake.addFirst(Pair(hvx, hvy))
        if (whx == food.first && why == food.second) { runScore++; runCoins++; spawnFood() } else snake.removeLast()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        cellSize = w.toFloat() / cellCount
        renderGroundBitmap()
    }

    private fun renderGroundBitmap() {
        if (cellSize <= 0f) return
        val boardSize = (cellCount * cellSize).toInt()
        val bmp = android.graphics.Bitmap.createBitmap(width, boardSize, android.graphics.Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val g = curGround()
        val bg = Paint().apply { color = Color.parseColor(g.bg) }
        c.drawRect(0f, 0f, width.toFloat(), boardSize.toFloat(), bg)
        val linePaint = Paint().apply { color = Color.parseColor(g.line); alpha = 115; strokeWidth = 1f }
        for (i in 0..cellCount) {
            val pos = i * cellSize
            c.drawLine(pos, 0f, pos, boardSize.toFloat(), linePaint)
            c.drawLine(0f, pos, cellCount * cellSize, pos, linePaint)
        }
        val fillPaint = Paint().apply { color = Color.parseColor(g.line) }
        when (g.pattern) {
            "dots" -> for (i in 0 until 40) c.drawCircle((i * 37) % width.toFloat(), (i * 53) % boardSize.toFloat(), 2f, fillPaint)
            "crack" -> {
                val p = Paint().apply { color = Color.parseColor(g.line); style = Paint.Style.STROKE; strokeWidth = 2f }
                for (i in 0 until 5) {
                    val path = Path()
                    path.moveTo((i * 70) % width.toFloat(), 0f)
                    path.lineTo((i * 70 + 30) % width.toFloat(), boardSize * 0.5f)
                    path.lineTo((i * 70 - 10 + width) % width.toFloat(), boardSize.toFloat())
                    c.drawPath(path, p)
                }
            }
            "wave" -> {
                val p = Paint().apply { color = Color.parseColor(g.line); style = Paint.Style.STROKE; strokeWidth = 2f }
                for (i in 0 until 8) {
                    val path = Path()
                    path.moveTo(0f, i * boardSize / 8f)
                    path.quadTo(width / 2f, i * boardSize / 8f - 15f, width.toFloat(), i * boardSize / 8f)
                    c.drawPath(path, p)
                }
            }
        }
        groundBitmap = bmp
    }

    // ---------- Drawing ----------
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)
        activeButtons.clear()
        when (screen) {
            Screen.NAME -> {}
            Screen.MENU -> drawMenu(canvas)
            Screen.PLAYING -> { drawGame(canvas); if (paused) drawPauseOverlay(canvas) }
            Screen.SHOP -> drawShop(canvas)
            Screen.SETTINGS -> drawSettings(canvas)
            Screen.LEADERBOARD -> drawLeaderboard(canvas)
            Screen.GAME_OVER -> { drawGame(canvas); drawGameOver(canvas) }
        }
    }
    private fun addButton(rect: RectF, action: () -> Unit) { activeButtons.add(rect to action) }
    private fun drawButton(canvas: Canvas, rect: RectF, label: String, enabled: Boolean = true, action: () -> Unit) {
        canvas.drawRoundRect(rect, 20f, 20f, if (enabled) buttonPaint else buttonDisabledPaint)
        canvas.drawText(label, rect.centerX(), rect.centerY() + 14f, buttonTextPaint)
        if (enabled) addButton(rect, action)
    }

    private fun drawMenu(canvas: Canvas) {
        val cx = width / 2f
        canvas.drawText(tr("title"), cx, height * 0.16f, titlePaint)
        canvas.drawText("${tr("hello")} $playerName", cx, height * 0.22f, smallTextPaint)
        canvas.drawText("${tr("best")}: $bestScore", cx, height * 0.27f, smallTextPaint)
        canvas.drawText("🪙 $coins", cx, height * 0.33f, textPaint)
        val btnW = width * 0.62f; val btnH = 110f
        var y = height * 0.42f
        drawButton(canvas, RectF(cx - btnW/2, y, cx + btnW/2, y + btnH), tr("play")) { startGame() }; y += btnH + 18
        drawButton(canvas, RectF(cx - btnW/2, y, cx + btnW/2, y + btnH), tr("shopSnakes")) { screen = Screen.SHOP; shopTab = "snakes" }; y += btnH + 18
        drawButton(canvas, RectF(cx - btnW/2, y, cx + btnW/2, y + btnH), tr("shopGrounds")) { screen = Screen.SHOP; shopTab = "grounds" }; y += btnH + 18
        drawButton(canvas, RectF(cx - btnW/2, y, cx + btnW/2, y + btnH), tr("leaderboard")) { screen = Screen.LEADERBOARD; fetchLeaderboard() }; y += btnH + 18
        drawButton(canvas, RectF(cx - btnW/2, y, cx + btnW/2, y + btnH), tr("settings")) { screen = Screen.SETTINGS }
    }

    private fun drawSettings(canvas: Canvas) {
        val cx = width / 2f
        drawButton(canvas, RectF(30f, 40f, 180f, 110f), tr("back")) { screen = Screen.MENU }
        canvas.drawText(tr("settings"), cx, 190f, titlePaint.apply { textSize = 60f })
        canvas.drawText(tr("language"), cx, 260f, smallTextPaint)
        val bw = width * 0.35f
        val faRect = RectF(cx - bw - 10f, 300f, cx - 10f, 380f)
        val enRect = RectF(cx + 10f, 300f, cx + bw + 10f, 380f)
        drawButton(canvas, faRect, "فارسی", true) { lang = "fa"; saveProgress() }
        drawButton(canvas, enRect, "English", true) { lang = "en"; saveProgress() }
        val strokePaint = Paint().apply { color = Color.parseColor("#4CAF50"); style = Paint.Style.STROKE; strokeWidth = 5f }
        canvas.drawRoundRect(if (lang == "fa") faRect else enRect, 20f, 20f, strokePaint)
    }

    private fun drawLeaderboard(canvas: Canvas) {
        val cx = width / 2f
        drawButton(canvas, RectF(30f, 40f, 180f, 110f), tr("back")) { screen = Screen.MENU }
        canvas.drawText(tr("leaderboard"), cx, 190f, titlePaint.apply { textSize = 58f })
        if (onlineLeaderboard.isEmpty()) {
            canvas.drawText(tr("empty"), cx, 260f, textPaint)
        } else {
            var top = 240f
            onlineLeaderboard.forEachIndexed { i, (name, score) ->
                val rowRect = RectF(40f, top, width - 40f, top + 78f)
                canvas.drawRoundRect(rowRect, 16f, 16f, if (i < 3) cardPaint else Paint().apply { color = Color.parseColor("#15181c") })
                val rankColor = when (i) { 0 -> "#FFC107"; 1 -> "#B0BEC5"; 2 -> "#D2691E"; else -> "#FFFFFF" }
                val rankPaint = Paint(textPaint).apply { color = Color.parseColor(rankColor); textAlign = Paint.Align.RIGHT; textSize = 36f }
                canvas.drawText("${i + 1}.", rowRect.right - 26f, rowRect.centerY() + 12f, rankPaint)
                val namePaint = Paint(textPaint).apply { textAlign = Paint.Align.RIGHT; textSize = 34f }
                canvas.drawText(name, rowRect.right - 70f, rowRect.centerY() + 12f, namePaint)
                val scorePaint = Paint(textPaint).apply { color = Color.parseColor("#FFC107"); textAlign = Paint.Align.LEFT; textSize = 34f }
                canvas.drawText("$score", rowRect.left + 20f, rowRect.centerY() + 12f, scorePaint)
                top += 88f
            }
        }
    }

    private fun drawShop(canvas: Canvas) {
        drawButton(canvas, RectF(30f, 40f, 180f, 110f), tr("back")) { screen = Screen.MENU }
        canvas.drawText("🪙 $coins", width - 140f, 92f, textPaint)
        val tabW = width * 0.42f
        drawButton(canvas, RectF(40f, 130f, 40f + tabW, 190f), tr("snakesTab")) { shopTab = "snakes" }
        drawButton(canvas, RectF(width - 40f - tabW, 130f, width - 40f, 190f), tr("groundsTab")) { shopTab = "grounds" }
        val strokePaint = Paint().apply { color = Color.parseColor("#4CAF50"); style = Paint.Style.STROKE; strokeWidth = 5f }
        if (shopTab == "snakes") canvas.drawRoundRect(RectF(40f, 130f, 40f + tabW, 190f), 20f, 20f, strokePaint)
        else canvas.drawRoundRect(RectF(width - 40f - tabW, 130f, width - 40f, 190f), 20f, 20f, strokePaint)

        var top = 220f
        val rowH = 150f
        if (shopTab == "snakes") skins.forEach { s ->
            val rowRect = RectF(30f, top, width - 30f, top + rowH)
            canvas.drawRoundRect(rowRect, 20f, 20f, cardPaint)
            val grad = RadialGradient(rowRect.left + 80f - 10f, rowRect.centerY() - 10f, 60f, Color.parseColor(s.c3), Color.parseColor(s.c1), Shader.TileMode.CLAMP)
            canvas.drawCircle(rowRect.left + 80f, rowRect.centerY(), 46f, Paint().apply { shader = grad })
            val nameP = Paint(textPaint).apply { textAlign = Paint.Align.RIGHT; textSize = 34f }
            canvas.drawText(skinName(s), rowRect.right - 30f, rowRect.centerY() - 10f, nameP)
            val isU = unlockedSnakes.contains(s.name); val isS = selectedSnake == s.name
            val actionRect = RectF(rowRect.left + 150f, rowRect.top + 30f, rowRect.left + 320f, rowRect.bottom - 30f)
            when {
                isS -> { canvas.drawRoundRect(actionRect, 14f, 14f, buttonDisabledPaint); canvas.drawText(tr("selected"), actionRect.centerX(), actionRect.centerY() + 12f, buttonTextPaint) }
                isU -> drawButton(canvas, actionRect, tr("select")) { selectedSnake = s.name; saveProgress() }
                coins >= s.price -> drawButton(canvas, actionRect, "🪙${s.price}") { coins -= s.price; unlockedSnakes.add(s.name); selectedSnake = s.name; saveProgress() }
                else -> { canvas.drawRoundRect(actionRect, 14f, 14f, buttonDisabledPaint); canvas.drawText("🪙${s.price}", actionRect.centerX(), actionRect.centerY() + 12f, buttonTextPaint) }
            }
            top += rowH + 16f
        } else grounds.forEach { g ->
            val rowRect = RectF(30f, top, width - 30f, top + rowH)
            canvas.drawRoundRect(rowRect, 20f, 20f, cardPaint)
            canvas.drawRoundRect(RectF(rowRect.left + 30f, rowRect.top + 25f, rowRect.left + 130f, rowRect.bottom - 25f), 14f, 14f, Paint().apply { color = Color.parseColor(g.bg) })
            val nameP = Paint(textPaint).apply { textAlign = Paint.Align.RIGHT; textSize = 34f }
            canvas.drawText(groundName(g), rowRect.right - 30f, rowRect.centerY() - 10f, nameP)
            val isU = unlockedGrounds.contains(g.name); val isS = selectedGround == g.name
            val actionRect = RectF(rowRect.left + 150f, rowRect.top + 30f, rowRect.left + 320f, rowRect.bottom - 30f)
            when {
                isS -> { canvas.drawRoundRect(actionRect, 14f, 14f, buttonDisabledPaint); canvas.drawText(tr("selected"), actionRect.centerX(), actionRect.centerY() + 12f, buttonTextPaint) }
                isU -> drawButton(canvas, actionRect, tr("select")) { selectedGround = g.name; saveProgress(); renderGroundBitmap() }
                coins >= g.price -> drawButton(canvas, actionRect, "🪙${g.price}") { coins -= g.price; unlockedGrounds.add(g.name); selectedGround = g.name; saveProgress(); renderGroundBitmap() }
                else -> { canvas.drawRoundRect(actionRect, 14f, 14f, buttonDisabledPaint); canvas.drawText("🪙${g.price}", actionRect.centerX(), actionRect.centerY() + 12f, buttonTextPaint) }
            }
            top += rowH + 16f
        }
    }

    // ---------- Realistic snake rendering ----------
    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
    private fun angleFor(d: Direction) = when (d) { Direction.RIGHT -> 0f; Direction.DOWN -> 90f; Direction.LEFT -> 180f; Direction.UP -> -90f }

    private fun buildRuns(pts: List<PointF>): List<List<PointF>> {
        val runs = mutableListOf(mutableListOf(pts[0]))
        for (i in 1 until pts.size) {
            val dx = pts[i].x - pts[i - 1].x; val dy = pts[i].y - pts[i - 1].y
            if (hypot(dx, dy) > cellSize * 3) runs.add(mutableListOf(pts[i])) else runs.last().add(pts[i])
        }
        return runs
    }
    private fun drawBodyLayer(canvas: Canvas, pts: List<PointF>, paint: Paint, dx: Float, dy: Float) {
        canvas.save(); canvas.translate(dx, dy)
        for (run in buildRuns(pts)) {
            if (run.size == 1) {
                val dot = Paint(paint).apply { style = Paint.Style.FILL }
                canvas.drawCircle(run[0].x, run[0].y, paint.strokeWidth / 2, dot)
            } else {
                val path = Path(); path.moveTo(run[0].x, run[0].y)
                for (i in 1 until run.size - 1) {
                    val mx = (run[i].x + run[i + 1].x) / 2; val my = (run[i].y + run[i + 1].y) / 2
                    path.quadTo(run[i].x, run[i].y, mx, my)
                }
                path.lineTo(run.last().x, run.last().y)
                canvas.drawPath(path, paint)
            }
        }
        canvas.restore()
    }
    private fun drawHead(canvas: Canvas, cx: Float, cy: Float, r: Float, skin: Skin, now: Long) {
        val shadowP = Paint().apply { color = Color.argb(60, 0, 0, 0) }
        canvas.drawOval(RectF(cx - r * 1.2f, cy + r * 0.15f, cx + r * 1.2f, cy + r * 0.85f), shadowP)
        canvas.save(); canvas.translate(cx, cy); canvas.rotate(angleFor(direction))
        val path = Path()
        path.moveTo(-r * 0.9f, -r * 0.75f)
        path.cubicTo(r * 0.3f, -r * 0.85f, r * 1.35f, -r * 0.35f, r * 1.5f, 0f)
        path.cubicTo(r * 1.35f, r * 0.35f, r * 0.3f, r * 0.85f, -r * 0.9f, r * 0.75f)
        path.cubicTo(-r * 0.5f, 0f, -r * 0.5f, 0f, -r * 0.9f, -r * 0.75f)
        path.close()
        val grad = LinearGradient(-r, -r * 0.7f, r, r * 0.7f, intArrayOf(Color.parseColor(skin.c1), Color.parseColor(skin.c3), Color.parseColor(skin.c2)), null, Shader.TileMode.CLAMP)
        canvas.drawPath(path, Paint().apply { shader = grad })
        canvas.drawPath(path, Paint().apply { color = Color.parseColor(skin.c2); style = Paint.Style.STROKE; strokeWidth = 2f })
        val nostrilP = Paint().apply { color = Color.parseColor(skin.c2) }
        canvas.drawCircle(r * 1.2f, -r * 0.18f, r * 0.06f, nostrilP)
        canvas.drawCircle(r * 1.2f, r * 0.18f, r * 0.06f, nostrilP)
        val eyeWhite = Paint().apply { color = Color.WHITE }
        val eyePupil = Paint().apply { color = Color.parseColor("#111111") }
        canvas.drawCircle(r * 0.35f, -r * 0.55f, r * 0.22f, eyeWhite)
        canvas.drawCircle(r * 0.35f, r * 0.55f, r * 0.22f, eyeWhite)
        canvas.drawCircle(r * 0.4f, -r * 0.55f, r * 0.11f, eyePupil)
        canvas.drawCircle(r * 0.4f, r * 0.55f, r * 0.11f, eyePupil)
        if ((now / 200) % 3 == 0L) {
            val tongueP = Paint().apply { color = Color.parseColor("#E57373"); style = Paint.Style.STROKE; strokeWidth = 4f }
            canvas.drawLine(r * 1.45f, 0f, r * 2.1f, 0f, tongueP)
            canvas.drawLine(r * 2.1f, 0f, r * 2.35f, -r * 0.18f, tongueP)
            canvas.drawLine(r * 2.1f, 0f, r * 2.35f, r * 0.18f, tongueP)
        }
        canvas.restore()
    }

    private fun drawGame(canvas: Canvas) {
        val boardSize = cellCount * cellSize
        canvas.save(); canvas.clipRect(0f, 0f, width.toFloat(), boardSize)
        groundBitmap?.let { canvas.drawBitmap(it, 0f, 0f, null) }

        val now = SystemClock.uptimeMillis()
        val bob = kotlin.math.sin(now / 220.0).toFloat() * 2f
        val fx = food.first * cellSize + cellSize / 2
        val fy = food.second * cellSize + cellSize / 2 + bob
        canvas.drawOval(RectF(fx - 7f, food.second * cellSize + cellSize / 2 + 7f, fx + 7f, food.second * cellSize + cellSize / 2 + 15f), Paint().apply { color = Color.argb(60, 0, 0, 0) })
        canvas.drawRoundRect(RectF(fx - 3f, fy - cellSize / 2 + 2f, fx + 3f, fy - cellSize / 2 + 10f), 2f, 2f, Paint().apply { color = Color.parseColor("#4CAF50") })
        val ag = RadialGradient(fx - cellSize * 0.1f, fy - cellSize * 0.1f, cellSize * 0.4f, Color.parseColor("#FFCDD2"), Color.parseColor("#D32F2F"), Shader.TileMode.CLAMP)
        canvas.drawCircle(fx, fy, cellSize * 0.35f, Paint().apply { shader = ag })

        val sk = curSkin()
        val n = min(snake.size, prevSnake.size)
        val frac = if (paused) 1f else min(1f, (now - lastTickTime) / 150f)
        val pts = snake.mapIndexed { i, s ->
            var vx = s.first.toFloat(); var vy = s.second.toFloat()
            if (i < n) { vx = lerp(prevSnake[i].first.toFloat(), s.first.toFloat(), frac); vy = lerp(prevSnake[i].second.toFloat(), s.second.toFloat(), frac) }
            PointF(wrapF(vx) * cellSize + cellSize / 2, wrapF(vy) * cellSize + cellSize / 2)
        }

        drawBodyLayer(canvas, pts, Paint().apply { color = Color.argb(70, 0, 0, 0); style = Paint.Style.STROKE; strokeWidth = cellSize * 0.72f; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }, 0f, cellSize * 0.22f)
        drawBodyLayer(canvas, pts, Paint().apply { color = Color.parseColor(sk.c1); style = Paint.Style.STROKE; strokeWidth = cellSize * 0.72f; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }, 0f, 0f)
        drawBodyLayer(canvas, pts, Paint().apply { color = Color.parseColor(sk.c2); alpha = 140; style = Paint.Style.STROKE; strokeWidth = cellSize * 0.4f; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }, cellSize * 0.06f, cellSize * 0.12f)
        drawBodyLayer(canvas, pts, Paint().apply { color = Color.parseColor(sk.c3); alpha = 128; style = Paint.Style.STROKE; strokeWidth = cellSize * 0.26f; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }, -cellSize * 0.08f, -cellSize * 0.14f)

        var i = 2
        while (i < pts.size - 1) {
            val p = pts[i]
            canvas.save(); canvas.clipPath(Path().apply { addCircle(p.x, p.y, cellSize * 0.34f, Path.Direction.CW) })
            val markPaint = Paint().apply { color = Color.parseColor(sk.c2) }
            when (sk.pattern) {
                "band" -> if (i % 6 == 0) canvas.drawRect(p.x - cellSize * 0.4f, p.y - 4f, p.x + cellSize * 0.4f, p.y + 4f, markPaint)
                "ring" -> if (i % 4 == 0) canvas.drawCircle(p.x, p.y, cellSize * 0.2f, Paint().apply { color = Color.parseColor(sk.c2); style = Paint.Style.STROKE; strokeWidth = 5f })
                "diamond" -> if (i % 4 == 0) { canvas.save(); canvas.translate(p.x, p.y); canvas.rotate(45f); canvas.drawRect(-12f, -12f, 12f, 12f, markPaint); canvas.restore() }
                "blotch" -> if (i % 4 == 0) canvas.drawCircle(p.x, p.y, cellSize * 0.22f, markPaint)
                "speckle" -> canvas.drawCircle(p.x, p.y, 2.2f, markPaint)
                "stripe", "zigzag" -> canvas.drawRect(p.x - 3f, p.y - cellSize * 0.3f, p.x + 3f, p.y + cellSize * 0.3f, markPaint)
                "sparkle" -> if (i % 5 == 0) canvas.drawCircle(p.x, p.y, 2.4f, Paint().apply { color = Color.parseColor(sk.c3) })
            }
            canvas.restore()
            i += 2
        }
        drawHead(canvas, pts[0].x, pts[0].y, cellSize * 0.5f, sk, now)
        canvas.restore()

        canvas.drawText("${tr("score")}: $runScore   🪙 $runCoins   ${tr("best")}: $bestScore", width / 2f, boardSize + 70f, smallTextPaint.apply { textSize = 34f })

        val pr = RectF(width - 100f, 40f, width - 30f, 110f)
        pauseIconRect = pr
        canvas.drawRoundRect(pr, 20f, 20f, buttonDisabledPaint)
        canvas.drawText("⏸", pr.centerX(), pr.centerY() + 14f, buttonTextPaint)
        if (!paused) addButton(pr) { paused = true }
    }

    private fun drawPauseOverlay(canvas: Canvas) {
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), overlayPaint)
        val cx = width / 2f
        canvas.drawText(tr("paused"), cx, height * 0.35f, titlePaint.apply { textSize = 64f })
        val btnW = width * 0.6f; val btnH = 110f
        drawButton(canvas, RectF(cx - btnW/2, height * 0.48f, cx + btnW/2, height * 0.48f + btnH), tr("resume")) { paused = false }
        drawButton(canvas, RectF(cx - btnW/2, height * 0.60f, cx + btnW/2, height * 0.60f + btnH), tr("toMenu")) { screen = Screen.MENU; paused = false }
    }

    private fun drawGameOver(canvas: Canvas) {
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), overlayPaint)
        val cx = width / 2f
        canvas.drawText(tr("gameOver"), cx, height * 0.26f, titlePaint)
        canvas.drawText("${tr("score")}: $runScore", cx, height * 0.33f, textPaint)
        canvas.drawText("🪙 $runCoins", cx, height * 0.39f, textPaint)
        canvas.drawText("${tr("best")}: $bestScore", cx, height * 0.44f, smallTextPaint)
        val btnW = width * 0.62f; val btnH = 105f
        var y = height * 0.52f
        drawButton(canvas, RectF(cx - btnW/2, y, cx + btnW/2, y + btnH), tr("again")) { startGame() }; y += btnH + 16
        drawButton(canvas, RectF(cx - btnW/2, y, cx + btnW/2, y + btnH), tr("leaderboard")) { screen = Screen.LEADERBOARD; fetchLeaderboard() }; y += btnH + 16
        drawButton(canvas, RectF(cx - btnW/2, y, cx + btnW/2, y + btnH), tr("toMenu")) { screen = Screen.MENU }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> { touchStartX = event.x; touchStartY = event.y }
            MotionEvent.ACTION_UP -> {
                if (screen != Screen.PLAYING || paused) {
                    for ((rect, action) in activeButtons) if (rect.contains(event.x, event.y)) { action(); invalidate(); return true }
                    return true
                }
                pauseIconRect?.let { if (it.contains(event.x, event.y)) { paused = true; invalidate(); return true } }
                val dx = event.x - touchStartX; val dy = event.y - touchStartY
                if (abs(dx) > abs(dy)) {
                    if (dx > 40 && direction != Direction.LEFT) pendingDirection = Direction.RIGHT
                    else if (dx < -40 && direction != Direction.RIGHT) pendingDirection = Direction.LEFT
                } else {
                    if (dy > 40 && direction != Direction.UP) pendingDirection = Direction.DOWN
                    else if (dy < -40 && direction != Direction.DOWN) pendingDirection = Direction.UP
                }
            }
        }
        return true
    }
}
