package com.kloss.pixelcat

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Point
import android.graphics.Rect
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import kotlin.math.min
import kotlin.random.Random

/**
 * Pixel Cat — เกมแมวพิกเซลวิ่งกระโดดข้ามกล่อง
 * วาดลงบิตแมป 96x96 แล้วขยายแบบไม่เบลอ (Galaxy Watch Ultra 480x480 = ขยาย 5 เท่าพอดี)
 * แตะ = กระโดด, แตะกลางอากาศ = กระโดด 2 ชั้น, เก็บปลาได้ +10
 */
class GameView(context: Context) : View(context) {

    private companion object {
        const val W = 96
        const val H = 96
        const val GROUND = 66
        const val CAT_X = 22
        const val GRAVITY = 420f
        const val JUMP_V = -160f
        const val DOUBLE_JUMP_V = -130f
        const val START_SPEED = 42f
        const val MAX_SPEED = 95f

        val CAT_A = arrayOf(
            "......o...o",
            "......oo.oo",
            "t.....ooooo",
            ".t....odood",
            "..oooooooop",
            "..ooooooooo",
            "..o.o...o.o",
            "..d.d...d.d",
        )
        val CAT_B = arrayOf(
            "......o...o",
            "......oo.oo",
            "t.....ooooo",
            ".t....odood",
            "..oooooooop",
            "..ooooooooo",
            "...o.o.o.o.",
            "...d.d.d.d.",
        )
        val CAT_HIT = arrayOf(
            "......o...o",
            "......oo.oo",
            ".t....ooooo",
            "t.....owoow",
            "..oooooooop",
            "..ooooooooo",
            "..o.o...o.o",
            "..d.d...d.d",
        )
        val FISH = arrayOf(
            ".bbb.B",
            "bdbbBB",
            ".bbb.B",
        )

        val FONT: Map<Char, String> = mapOf(
            '0' to "111101101101111", '1' to "010110010010111",
            '2' to "111001111100111", '3' to "111001111001111",
            '4' to "101101111001001", '5' to "111100111001111",
            '6' to "111100111101111", '7' to "111001001001001",
            '8' to "111101111101111", '9' to "111101111001111",
            'A' to "010101111101101", 'B' to "110101110101110",
            'C' to "111100100100111", 'E' to "111100110100111",
            'G' to "111100101101111", 'H' to "101101111101101",
            'I' to "111010010010111", 'L' to "100100100100111",
            'M' to "101111111101101", 'N' to "110101101101101",
            'O' to "111101101101111", 'P' to "111101111100100",
            'R' to "110101110101101", 'S' to "111100111001111",
            'T' to "111010010010010", 'V' to "101101101101010",
            'W' to "101101111111101", 'X' to "101101010101101",
        )
    }

    private enum class State { READY, PLAYING, OVER }

    private class Obstacle(var x: Float, val w: Int, val h: Int)
    private class Fish(var x: Float, val y: Float, var taken: Boolean = false)

    // ---- palette ----
    private val sky = intArrayOf(
        0xFF0B1030.toInt(), 0xFF121A45.toInt(), 0xFF1B2660.toInt(), 0xFF26357A.toInt()
    )
    private val cStar = 0xFFE8E8FF.toInt()
    private val cMoon = 0xFFFFF4C2.toInt()
    private val cMoonShade = 0xFFE6D58F.toInt()
    private val cCity = 0xFF0A0E26.toInt()
    private val cWindow = 0xFFFFD866.toInt()
    private val cGround = 0xFF3B2A1E.toInt()
    private val cGroundTop = 0xFF6BBF59.toInt()
    private val cGroundDot = 0xFF5A4030.toInt()
    private val cBox = 0xFFB0703A.toInt()
    private val cBoxDark = 0xFF5C3418.toInt()
    private val cBoxLight = 0xFFD9955A.toInt()
    private val cWhite = Color.WHITE
    private val cYellow = 0xFFFFD866.toInt()
    private val cRed = 0xFFFF5A5A.toInt()
    private val cGray = 0xFFB8B8C8.toInt()
    private val cShadow = 0xFF000000.toInt()

    private val spriteColors: Map<Char, Int> = mapOf(
        'o' to 0xFFF59E3B.toInt(),
        't' to 0xFFC86A1E.toInt(),
        'd' to 0xFF2A1A10.toInt(),
        'w' to Color.WHITE,
        'p' to 0xFFFF8FA3.toInt(),
        'b' to 0xFF5EC8F2.toInt(),
        'B' to 0xFF2C7FB8.toInt(),
    )

    // ---- rendering ----
    private val buffer = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
    private val bc = Canvas(buffer)
    private val paint = Paint().apply { isAntiAlias = false }
    private val blitPaint = Paint().apply {
        isFilterBitmap = false; isAntiAlias = false; isDither = false
    }
    private val dst = Rect()

    private val prefs = context.getSharedPreferences("pixelcat", Context.MODE_PRIVATE)

    // ---- game state ----
    private var state = State.READY
    private var catY = GROUND.toFloat()   // ตำแหน่งเท้าแมว
    private var vy = 0f
    private var jumpsUsed = 0
    private var speed = START_SPEED
    private var distance = 0f
    private var fishCount = 0
    private var score = 0
    private var best = prefs.getInt("best", 0)
    private var newBest = false
    private var spawnIn = 50f
    private var time = 0f
    private var overTime = 0f
    private var groundScroll = 0f
    private var cityScroll = 0f

    private var running = false
    private var lastNanos = 0L

    private val obstacles = ArrayList<Obstacle>()
    private val fishes = ArrayList<Fish>()
    private val stars = List(18) { Point(Random.nextInt(W), Random.nextInt(8, 44)) }
    private val city = IntArray(48) { Random.nextInt(4, 15) }

    init {
        isHapticFeedbackEnabled = true
        reset()
    }

    fun resume() {
        running = true
        lastNanos = 0L
        postInvalidateOnAnimation()
    }

    fun pause() {
        running = false
    }

    private fun reset() {
        state = State.READY
        catY = GROUND.toFloat()
        vy = 0f
        jumpsUsed = 0
        speed = START_SPEED
        distance = 0f
        fishCount = 0
        score = 0
        newBest = false
        spawnIn = 50f
        obstacles.clear()
        fishes.clear()
    }

    private fun jump() {
        if (catY >= GROUND) {
            vy = JUMP_V
            jumpsUsed = 1
        } else if (jumpsUsed < 2) {
            vy = DOUBLE_JUMP_V
            jumpsUsed = 2
        }
    }

    private fun gameOver() {
        state = State.OVER
        overTime = 0f
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        if (score > best) {
            best = score
            newBest = true
            prefs.edit().putInt("best", best).apply()
        }
    }

    private fun spawn() {
        val w = Random.nextInt(4, 8)
        val h = Random.nextInt(5, 11)
        val x = W + 4f
        obstacles.add(Obstacle(x, w, h))
        if (Random.nextFloat() < 0.2f && speed > 55f) {
            obstacles.add(Obstacle(x + w + 1, Random.nextInt(4, 6), Random.nextInt(5, 8)))
        }
        if (Random.nextFloat() < 0.5f) {
            fishes.add(Fish(x + w / 2f - 3f, GROUND - h - 14f - Random.nextInt(0, 6)))
        }
        spawnIn = Random.nextFloat() * 45f + 40f + speed * 0.35f
    }

    private fun update(dt: Float) {
        time += dt
        when (state) {
            State.READY -> cityScroll += dt * 6f
            State.OVER -> overTime += dt
            State.PLAYING -> {
                speed = min(MAX_SPEED, speed + dt * 1.6f)
                val dx = speed * dt
                distance += dx
                groundScroll += dx
                cityScroll += dx * 0.3f

                vy += GRAVITY * dt
                catY += vy * dt
                if (catY >= GROUND) {
                    catY = GROUND.toFloat(); vy = 0f; jumpsUsed = 0
                }

                for (o in obstacles) o.x -= dx
                obstacles.removeAll { it.x + it.w < -4 }
                for (f in fishes) f.x -= dx
                fishes.removeAll { it.x < -8 || it.taken }

                spawnIn -= dx
                if (spawnIn <= 0f) spawn()

                // hitbox แมว (หลวมนิดๆ ให้เล่นง่าย)
                val cl = CAT_X + 2f
                val cr = CAT_X + 10f
                val ct = catY - 6f
                val cb = catY
                for (o in obstacles) {
                    val ol = o.x + 1f
                    val orr = o.x + o.w - 1f
                    val ot = GROUND - o.h + 1f
                    if (cr > ol && cl < orr && cb > ot) {
                        gameOver(); return
                    }
                }
                for (f in fishes) {
                    if (!f.taken && cr > f.x && cl < f.x + 6f && cb > f.y && ct < f.y + 3f) {
                        f.taken = true
                        fishCount++
                        performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    }
                }
                score = (distance / 8f).toInt() + fishCount * 10
            }
        }
    }

    // ---- drawing helpers ----
    private fun px(x: Int, y: Int, w: Int, h: Int, c: Int) {
        paint.color = c
        bc.drawRect(x.toFloat(), y.toFloat(), (x + w).toFloat(), (y + h).toFloat(), paint)
    }

    private fun drawSprite(rows: Array<String>, x: Int, y: Int) {
        for (r in rows.indices) {
            val row = rows[r]
            for (c in row.indices) {
                val col = spriteColors[row[c]] ?: continue
                px(x + c, y + r, 1, 1, col)
            }
        }
    }

    private fun drawText(s: String, x0: Int, y0: Int, c: Int) {
        var x = x0
        for (ch in s) {
            val g = FONT[ch]
            if (g != null) {
                for (i in 0 until 15) if (g[i] == '1') px(x + i % 3, y0 + i / 3, 1, 1, c)
            }
            x += 4
        }
    }

    private fun drawTextCentered(s: String, y: Int, c: Int) {
        val x = (W - (s.length * 4 - 1)) / 2
        drawText(s, x + 1, y + 1, cShadow)
        drawText(s, x, y, c)
    }

    private fun render() {
        // ท้องฟ้า
        for (i in 0 until 4) px(0, i * 17, W, 17, sky[i])
        stars.forEachIndexed { i, s ->
            if (((time * 1.5f).toInt() + i) % 7 != 0) px(s.x, s.y, 1, 1, cStar)
        }
        // พระจันทร์
        px(67, 18, 5, 7, cMoon)
        px(66, 19, 7, 5, cMoon)
        px(69, 20, 2, 2, cMoonShade)

        // ตึกเมือง (parallax)
        val off = cityScroll.toInt()
        for (i in 0 until 26) {
            val bx = i * 4 - (off % 4)
            val idx = ((off / 4) + i) % city.size
            val h = city[idx]
            px(bx, GROUND - h, 4, h, cCity)
            if (idx % 3 == 0) px(bx + 1, GROUND - h + 2, 1, 1, cWindow)
            if (idx % 5 == 0 && h > 8) px(bx + 2, GROUND - h + 5, 1, 1, cWindow)
        }

        // พื้น
        px(0, GROUND, W, H - GROUND, cGround)
        px(0, GROUND, W, 1, cGroundTop)
        val g = groundScroll.toInt() % 8
        var gx = 0
        while (gx < W + 8) {
            px(gx - g, GROUND + 3, 2, 1, cGroundDot)
            px(gx - g + 4, GROUND + 6, 1, 1, cGroundDot)
            gx += 8
        }

        // กล่อง
        for (o in obstacles) {
            val x = o.x.toInt()
            val top = GROUND - o.h
            px(x, top, o.w, o.h, cBoxDark)
            px(x + 1, top + 1, o.w - 2, o.h - 2, cBox)
            px(x + 1, top + 1, o.w - 2, 1, cBoxLight)
        }

        // ปลา
        for (f in fishes) drawSprite(FISH, f.x.toInt(), f.y.toInt())

        // แมว
        val sprite = when {
            state == State.OVER -> CAT_HIT
            state == State.PLAYING && catY >= GROUND ->
                if ((distance / 6f).toInt() % 2 == 0) CAT_A else CAT_B
            else -> CAT_A
        }
        drawSprite(sprite, CAT_X, catY.toInt() - 8)

        // HUD
        when (state) {
            State.READY -> {
                drawTextCentered("PIXEL CAT", 26, cWhite)
                if ((time * 2f).toInt() % 2 == 0) drawTextCentered("TAP", 74, cYellow)
                drawTextCentered("BEST $best", 82, cGray)
            }
            State.PLAYING -> drawTextCentered(score.toString(), 14, cWhite)
            State.OVER -> {
                drawTextCentered("GAME OVER", 24, cRed)
                drawTextCentered(score.toString(), 32, cWhite)
                drawTextCentered(if (newBest) "NEW BEST" else "BEST $best", 74, cYellow)
                if (overTime > 0.5f && (time * 2f).toInt() % 2 == 0) drawTextCentered("TAP", 82, cGray)
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        val now = System.nanoTime()
        val dt = if (lastNanos == 0L) 0f else min((now - lastNanos) / 1_000_000_000f, 0.05f)
        lastNanos = now

        update(dt)
        render()

        val size = min(width, height)
        val left = (width - size) / 2
        val top = (height - size) / 2
        dst.set(left, top, left + size, top + size)
        canvas.drawColor(Color.BLACK)
        canvas.drawBitmap(buffer, null, dst, blitPaint)

        if (running) postInvalidateOnAnimation()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> when (state) {
                State.READY -> { state = State.PLAYING; jump() }
                State.PLAYING -> jump()
                State.OVER -> if (overTime > 0.5f) reset()
            }
            MotionEvent.ACTION_UP -> performClick()
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
