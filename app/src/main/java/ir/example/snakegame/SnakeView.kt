package ir.example.snakegame

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import kotlin.random.Random

class SnakeView(context: Context) : View(context) {

    data class Skin(val name: String, val body: String, val head: String, val belly: String, val price: Int)

    private val skins = listOf(
        Skin("سبز کلاسیک", "#4CAF50", "#388E3C", "#A5D6A7", 0),
        Skin("آبی یخی", "#29B6F6", "#0288D1", "#B3E5FC", 50),
        Skin("بنفش نئون", "#AB47BC", "#7B1FA2", "#E1BEE7", 100),
        Skin("قرمز آتشین", "#FF5252", "#D32F2F", "#FFCDD2", 150),
        Skin("طلایی", "#FFC107", "#FF8F00", "#FFECB3", 250)
    )

    private val prefs: SharedPreferences =
        context.getSharedPreferences("snake_prefs", Context.MODE_PRIVATE)

    private var coins = prefs.getInt("coins", 0)
    private var bestScore = prefs.getInt("best_score", 0)
    private var unlocked = (prefs.getStringSet("unlocked", setOf(skins[0].name)) ?: setOf(skins[0].name)).toMutableSet()
    private var selectedSkin = prefs.getString("selected_skin", skins[0].name) ?: skins[0].name
    private var tick = 0

    private fun saveProgress() {
        prefs.edit()
            .putInt("coins", coins)
            .putInt("best_score", bestScore)
            .putStringSet("unlocked", unlocked)
            .putString("selected_skin", selectedSkin)
            .apply()
    }

    private fun currentSkin(): Skin = skins.first { it.name == selectedSkin }

    enum class Screen { MENU, PLAYING, SHOP, GAME_OVER }
    private var screen = Screen.MENU

    private val cellCount = 20
    private var cellSize = 0f

    private val snake = ArrayDeque<Pair<Int, Int>>()
    private var direction = Direction.RIGHT
    private var pendingDirection = Direction.RIGHT
    private var food = Pair(5, 5)
    private var runScore = 0
    private var runCoins = 0

    private var touchStartX = 0f
    private var touchStartY = 0f

    enum class Direction { UP, DOWN, LEFT, RIGHT }

    private val bgPaint = Paint().apply { color = Color.parseColor("#101418") }
    private val gridPaint = Paint().apply { color = Color.parseColor("#1c2128") }
    private val overlayPaint = Paint().apply { color = Color.parseColor("#CC000000") }
    private val cardPaint = Paint().apply { color = Color.parseColor("#1c2128") }
    private val cardBorderPaint = Paint().apply {
        color = Color.parseColor("#333c46")
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val buttonPaint = Paint().apply { color = Color.parseColor("#4CAF50") }
    private val buttonDisabledPaint = Paint().apply { color = Color.parseColor("#33404a") }
    private val eyeWhitePaint = Paint().apply { color = Color.WHITE }
    private val eyePupilPaint = Paint().apply { color = Color.parseColor("#111111") }
    private val tonguePaint = Paint().apply {
        color = Color.parseColor("#E57373")
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    private val appleStemPaint = Paint().apply { color = Color.parseColor("#4CAF50") }
    private val appleShinePaint = Paint().apply { color = Color.parseColor("#FFCDD2") }

    private val titlePaint = Paint().apply {
        color = Color.WHITE
        textSize = 90f
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 52f
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
    }
    private val smallTextPaint = Paint().apply {
        color = Color.parseColor("#B0BEC5")
        textSize = 38f
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
    }
    private val buttonTextPaint = Paint().apply {
        color = Color.WHITE
        textSize = 48f
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    private val activeButtons = mutableListOf<Pair<RectF, () -> Unit>>()

    private val gameLoop = object : Runnable {
        override fun run() {
            tick++
            if (screen == Screen.PLAYING) {
                update()
                invalidate()
            } else {
                invalidate()
            }
            postDelayed(this, 150L)
        }
    }

    init {
        post(gameLoop)
    }

    private fun startGame() {
        snake.clear()
        val start = cellCount / 2
        snake.addLast(Pair(start, start))
        snake.addLast(Pair(start - 1, start))
        snake.addLast(Pair(start - 2, start))
        direction = Direction.RIGHT
        pendingDirection = Direction.RIGHT
        runScore = 0
        runCoins = 0
        spawnFood()
        screen = Screen.PLAYING
    }

    private fun spawnFood() {
        var newFood: Pair<Int, Int>
        do {
            newFood = Pair(Random.nextInt(cellCount), Random.nextInt(cellCount))
        } while (snake.contains(newFood))
        food = newFood
    }

    private fun update() {
        direction = pendingDirection
        val head = snake.first()
        var (nx, ny) = when (direction) {
            Direction.UP -> Pair(head.first, head.second - 1)
            Direction.DOWN -> Pair(head.first, head.second + 1)
            Direction.LEFT -> Pair(head.first - 1, head.second)
            Direction.RIGHT -> Pair(head.first + 1, head.second)
        }
        if (nx < 0) nx = cellCount - 1
        if (nx >= cellCount) nx = 0
        if (ny < 0) ny = cellCount - 1
        if (ny >= cellCount) ny = 0
        val newHead = Pair(nx, ny)

        if (snake.contains(newHead)) {
            if (runScore > bestScore) bestScore = runScore
            coins += runCoins
            saveProgress()
            screen = Screen.GAME_OVER
            return
        }

        snake.addFirst(newHead)
        if (newHead == food) {
            runScore++
            runCoins++
            spawnFood()
        } else {
            snake.removeLast()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        cellSize = w.toFloat() / cellCount
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)
        activeButtons.clear()

        when (screen) {
            Screen.MENU -> drawMenu(canvas)
            Screen.PLAYING -> drawGame(canvas)
            Screen.SHOP -> drawShop(canvas)
            Screen.GAME_OVER -> {
                drawGame(canvas)
                drawGameOver(canvas)
            }
        }
    }

    private fun addButton(rect: RectF, action: () -> Unit) {
        activeButtons.add(rect to action)
    }

    private fun drawButton(canvas: Canvas, rect: RectF, label: String, enabled: Boolean = true, action: () -> Unit) {
        canvas.drawRoundRect(rect, 20f, 20f, if (enabled) buttonPaint else buttonDisabledPaint)
        canvas.drawText(label, rect.centerX(), rect.centerY() + 16f, buttonTextPaint)
        if (enabled) addButton(rect, action)
    }

    private fun drawMenu(canvas: Canvas) {
        val cx = width / 2f
        canvas.drawText("بازی مار", cx, height * 0.22f, titlePaint)
        canvas.drawText("رکورد: $bestScore", cx, height * 0.30f, smallTextPaint)
        canvas.drawText("🪙 $coins", cx, height * 0.37f, textPaint)

        val btnW = width * 0.6f
        val btnH = 130f
        val playRect = RectF(cx - btnW / 2, height * 0.48f, cx + btnW / 2, height * 0.48f + btnH)
        drawButton(canvas, playRect, "شروع بازی") { startGame() }

        val shopRect = RectF(cx - btnW / 2, height * 0.60f, cx + btnW / 2, height * 0.60f + btnH)
        drawButton(canvas, shopRect, "فروشگاه مارها") { screen = Screen.SHOP }
    }

    private fun drawShop(canvas: Canvas) {
        val cx = width / 2f

        val backRect = RectF(30f, 40f, 180f, 110f)
        drawButton(canvas, backRect, "بازگشت") { screen = Screen.MENU }

        canvas.drawText("🪙 $coins", width - 150f, 92f, textPaint)
        canvas.drawText("فروشگاه مارها", cx, 190f, titlePaint.apply { textSize = 64f })

        val rowH = 160f
        val startY = 260f
        skins.forEachIndexed { index, skin ->
            val top = startY + index * (rowH + 20f)
            val rowRect = RectF(40f, top, width - 40f, top + rowH)
            canvas.drawRoundRect(rowRect, 24f, 24f, cardPaint)
            canvas.drawRoundRect(rowRect, 24f, 24f, cardBorderPaint)

            val bodySwatch = Paint().apply { color = Color.parseColor(skin.body) }
            canvas.drawCircle(rowRect.left + 90f, rowRect.centerY(), 50f, bodySwatch)
            val shinePaint = Paint().apply { color = Color.parseColor(skin.belly) }
            canvas.drawCircle(rowRect.left + 75f, rowRect.centerY() - 15f, 16f, shinePaint)
            val headSwatch = Paint().apply { color = Color.parseColor(skin.head) }
            canvas.drawCircle(rowRect.left + 90f, rowRect.centerY() - 55f, 20f, headSwatch)

            val nameTextPaint = Paint(textPaint).apply { textAlign = Paint.Align.LEFT; textSize = 44f }
            canvas.drawText(skin.name, rowRect.left + 170f, rowRect.centerY() - 10f, nameTextPaint)

            val isUnlocked = unlocked.contains(skin.name)
            val isSelected = selectedSkin == skin.name

            val actionRect = RectF(rowRect.right - 260f, rowRect.top + 30f, rowRect.right - 30f, rowRect.bottom - 30f)
            when {
                isSelected -> {
                    canvas.drawRoundRect(actionRect, 16f, 16f, buttonDisabledPaint)
                    canvas.drawText("انتخاب‌شده", actionRect.centerX(), actionRect.centerY() + 14f, buttonTextPaint)
                }
                isUnlocked -> {
                    drawButton(canvas, actionRect, "انتخاب") {
                        selectedSkin = skin.name
                        saveProgress()
                    }
                }
                coins >= skin.price -> {
                    drawButton(canvas, actionRect, "خرید 🪙${skin.price}") {
                        coins -= skin.price
                        unlocked.add(skin.name)
                        selectedSkin = skin.name
                        saveProgress()
                    }
                }
                else -> {
                    canvas.drawRoundRect(actionRect, 16f, 16f, buttonDisabledPaint)
                    canvas.drawText("🪙${skin.price}", actionRect.centerX(), actionRect.centerY() + 14f, buttonTextPaint)
                }
            }
        }
    }

    private fun drawSnakeSegment(canvas: Canvas, x: Int, y: Int, isHead: Boolean, skin: Skin, idx: Int) {
        val px = x * cellSize
        val py = y * cellSize
        val cx = px + cellSize / 2
        val cy = py + cellSize / 2

        if (isHead) {
            val headPaint = Paint().apply { color = Color.parseColor(skin.head) }
            canvas.drawRoundRect(RectF(px, py, px + cellSize, py + cellSize), cellSize * 0.35f, cellSize * 0.35f, headPaint)

            var ex1 = cx - cellSize * 0.2f; var ey1 = cy - cellSize * 0.2f
            var ex2 = cx + cellSize * 0.2f; var ey2 = cy - cellSize * 0.2f
            when (direction) {
                Direction.LEFT -> { ex1 = cx - cellSize * 0.15f; ey1 = cy - cellSize * 0.2f; ex2 = cx - cellSize * 0.15f; ey2 = cy + cellSize * 0.2f }
                Direction.RIGHT -> { ex1 = cx + cellSize * 0.15f; ey1 = cy - cellSize * 0.2f; ex2 = cx + cellSize * 0.15f; ey2 = cy + cellSize * 0.2f }
                Direction.DOWN -> { ex1 = cx - cellSize * 0.2f; ey1 = cy + cellSize * 0.15f; ex2 = cx + cellSize * 0.2f; ey2 = cy + cellSize * 0.15f }
                Direction.UP -> { ex1 = cx - cellSize * 0.2f; ey1 = cy - cellSize * 0.15f; ex2 = cx + cellSize * 0.2f; ey2 = cy - cellSize * 0.15f }
            }
            val eyeR = cellSize * 0.15f
            canvas.drawCircle(ex1, ey1, eyeR, eyeWhitePaint)
            canvas.drawCircle(ex2, ey2, eyeR, eyeWhitePaint)
            canvas.drawCircle(ex1, ey1, eyeR * 0.4f, eyePupilPaint)
            canvas.drawCircle(ex2, ey2, eyeR * 0.4f, eyePupilPaint)

            if ((tick / 6) % 3 == 0) {
                var tx = cx; var ty = cy
                when (direction) {
                    Direction.RIGHT -> tx = cx + cellSize * 0.65f
                    Direction.LEFT -> tx = cx - cellSize * 0.65f
                    Direction.UP -> ty = cy - cellSize * 0.65f
                    Direction.DOWN -> ty = cy + cellSize * 0.65f
                }
                canvas.drawLine(cx, cy, tx, ty, tonguePaint)
            }
        } else {
            val bodyPaint = Paint().apply { color = Color.parseColor(skin.body) }
            val bellyPaint = Paint().apply { color = Color.parseColor(skin.belly) }
            canvas.drawRoundRect(
                RectF(px + 2, py + 2, px + cellSize - 2, py + cellSize - 2),
                cellSize * 0.3f, cellSize * 0.3f, bodyPaint
            )
            if (idx % 2 == 0) {
                canvas.drawCircle(cx, cy, cellSize * 0.16f, bellyPaint)
            } else {
                canvas.drawRoundRect(
                    RectF(cx - cellSize * 0.12f, cy - cellSize * 0.12f, cx + cellSize * 0.12f, cy + cellSize * 0.12f),
                    4f, 4f, bellyPaint
                )
            }
        }
    }

    private fun drawGame(canvas: Canvas) {
        val boardSize = cellCount * cellSize
        for (i in 0..cellCount) {
            val pos = i * cellSize
            canvas.drawLine(pos, 0f, pos, boardSize, gridPaint)
            canvas.drawLine(0f, pos, boardSize, pos, gridPaint)
        }

        val fx = food.first * cellSize + cellSize / 2
        val fy = food.second * cellSize + cellSize / 2
        canvas.drawRoundRect(RectF(fx - 3f, fy - cellSize / 2 + 2f, fx + 3f, fy - cellSize / 2 + 10f), 2f, 2f, appleStemPaint)
        val applePaint = Paint().apply { color = Color.parseColor("#FF5252") }
        canvas.drawCircle(fx, fy, cellSize * 0.35f, applePaint)
        canvas.drawCircle(fx - cellSize * 0.12f, fy - cellSize * 0.12f, cellSize * 0.1f, appleShinePaint)

        val skin = currentSkin()
        for (i in snake.size - 1 downTo 0) {
            val (x, y) = snake[i]
            drawSnakeSegment(canvas, x, y, i == 0, skin, i)
        }

        canvas.drawText(
            "امتیاز: $runScore   |   🪙 $runCoins   |   رکورد: $bestScore",
            width / 2f, boardSize + 90f, textPaint
        )
    }

    private fun drawGameOver(canvas: Canvas) {
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), overlayPaint)
        val cx = width / 2f
        canvas.drawText("باختی!", cx, height * 0.32f, titlePaint)
        canvas.drawText("امتیاز: $runScore", cx, height * 0.40f, textPaint)
        canvas.drawText("سکه‌ی به‌دست‌آمده: 🪙 $runCoins", cx, height * 0.46f, textPaint)
        canvas.drawText("رکورد: $bestScore", cx, height * 0.52f, smallTextPaint)

        val btnW = width * 0.6f
        val btnH = 130f
        val againRect = RectF(cx - btnW / 2, height * 0.60f, cx + btnW / 2, height * 0.60f + btnH)
        drawButton(canvas, againRect, "دوباره بازی کن") { startGame() }

        val menuRect = RectF(cx - btnW / 2, height * 0.72f, cx + btnW / 2, height * 0.72f + btnH)
        drawButton(canvas, menuRect, "منوی اصلی") { screen = Screen.MENU }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                touchStartX = event.x
                touchStartY = event.y
            }
            MotionEvent.ACTION_UP -> {
                if (screen != Screen.PLAYING) {
                    for ((rect, action) in activeButtons) {
                        if (rect.contains(event.x, event.y)) {
                            action()
                            invalidate()
                            return true
                        }
                    }
                    return true
                }

                val dx = event.x - touchStartX
                val dy = event.y - touchStartY
                if (kotlin.math.abs(dx) > kotlin.math.abs(dy)) {
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
