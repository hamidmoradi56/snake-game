package ir.example.snakegame

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import kotlin.random.Random

class SnakeView(context: Context) : View(context) {

    private val cellCount = 20
    private var cellSize = 0f

    private val snake = ArrayDeque<Pair<Int, Int>>()
    private var direction = Direction.RIGHT
    private var pendingDirection = Direction.RIGHT
    private var food = Pair(5, 5)
    private var score = 0
    private var bestScore = 0
    private var isGameOver = false

    private var touchStartX = 0f
    private var touchStartY = 0f

    private val bgPaint = Paint().apply { color = Color.parseColor("#101418") }
    private val gridPaint = Paint().apply { color = Color.parseColor("#1c2128") }
    private val snakePaint = Paint().apply { color = Color.parseColor("#4CAF50") }
    private val headPaint = Paint().apply { color = Color.parseColor("#81C784") }
    private val foodPaint = Paint().apply { color = Color.parseColor("#FF5252") }
    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 56f
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
    }
    private val bigTextPaint = Paint().apply {
        color = Color.WHITE
        textSize = 64f
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
    }

    enum class Direction { UP, DOWN, LEFT, RIGHT }

    private val gameLoop = object : Runnable {
        override fun run() {
            if (!isGameOver) {
                update()
                invalidate()
            }
            postDelayed(this, 150L)
        }
    }

    init {
        resetGame()
        post(gameLoop)
    }

    private fun resetGame() {
        snake.clear()
        val start = cellCount / 2
        snake.addLast(Pair(start, start))
        snake.addLast(Pair(start - 1, start))
        snake.addLast(Pair(start - 2, start))
        direction = Direction.RIGHT
        pendingDirection = Direction.RIGHT
        score = 0
        isGameOver = false
        spawnFood()
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

        // wrap around the edges instead of dying at the wall
        if (nx < 0) nx = cellCount - 1
        if (nx >= cellCount) nx = 0
        if (ny < 0) ny = cellCount - 1
        if (ny >= cellCount) ny = 0
        val newHead = Pair(nx, ny)

        if (snake.contains(newHead)) {
            isGameOver = true
            if (score > bestScore) bestScore = score
            return
        }

        snake.addFirst(newHead)
        if (newHead == food) {
            score++
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

        val boardSize = cellCount * cellSize

        for (i in 0..cellCount) {
            val pos = i * cellSize
            canvas.drawLine(pos, 0f, pos, boardSize, gridPaint)
            canvas.drawLine(0f, pos, boardSize, pos, gridPaint)
        }

        val foodRect = RectF(
            food.first * cellSize + 4,
            food.second * cellSize + 4,
            (food.first + 1) * cellSize - 4,
            (food.second + 1) * cellSize - 4
        )
        canvas.drawRoundRect(foodRect, 12f, 12f, foodPaint)

        snake.forEachIndexed { index, (x, y) ->
            val rect = RectF(
                x * cellSize + 2,
                y * cellSize + 2,
                (x + 1) * cellSize - 2,
                (y + 1) * cellSize - 2
            )
            canvas.drawRoundRect(rect, 8f, 8f, if (index == 0) headPaint else snakePaint)
        }

        canvas.drawText("امتیاز: $score   |   رکورد: $bestScore", width / 2f, boardSize + 90f, textPaint)

        if (isGameOver) {
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), Paint().apply {
                color = Color.parseColor("#AA000000")
            })
            canvas.drawText("باختی!", width / 2f, height / 2f - 60f, bigTextPaint)
            canvas.drawText("برای شروع دوباره ضربه بزن", width / 2f, height / 2f + 20f, textPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                touchStartX = event.x
                touchStartY = event.y
            }
            MotionEvent.ACTION_UP -> {
                if (isGameOver) {
                    resetGame()
                    invalidate()
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
