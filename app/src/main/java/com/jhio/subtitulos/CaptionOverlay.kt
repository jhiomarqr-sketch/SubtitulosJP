package com.jhio.subtitulos

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView

/** Burbuja flotante con los subtítulos, encima de cualquier app. Se puede arrastrar. */
class CaptionOverlay(private val ctx: Context) {

    private val wm = ctx.getSystemService(WindowManager::class.java)
    private val density = ctx.resources.displayMetrics.density
    private var attached = false

    private val jaView = TextView(ctx).apply {
        setTextColor(Color.parseColor("#BBBBBB"))
        textSize = 13f
        visibility = View.GONE
    }

    private val esView = TextView(ctx).apply {
        setTextColor(Color.WHITE)
        textSize = 19f
        setTypeface(null, Typeface.BOLD)
        setShadowLayer(4f, 0f, 0f, Color.BLACK)
    }

    private val container = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        val p = (12 * density).toInt()
        setPadding(p, p / 2, p, p / 2)
        background = GradientDrawable().apply {
            setColor(Color.parseColor("#B3000000"))
            cornerRadius = 14 * density
        }
        addView(jaView)
        addView(esView)
    }

    private val params = WindowManager.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.BOTTOM
        y = (120 * density).toInt()
        horizontalMargin = 0.03f
    }

    @SuppressLint("ClickableViewAccessibility")
    fun show() {
        if (attached) return
        var startY = 0
        var startRawY = 0f
        container.setOnTouchListener { _, ev ->
            when (ev.action) {
                MotionEvent.ACTION_DOWN -> { startY = params.y; startRawY = ev.rawY; true }
                MotionEvent.ACTION_MOVE -> {
                    // gravity BOTTOM: y crece hacia arriba
                    params.y = (startY - (ev.rawY - startRawY)).toInt().coerceAtLeast(0)
                    wm.updateViewLayout(container, params)
                    true
                }
                else -> false
            }
        }
        esView.text = "Cargando modelo de voz…"
        wm.addView(container, params)
        attached = true
    }

    /** ja = texto original (puede ir vacío), es = traducción. Llamar en el hilo principal. */
    fun setText(ja: String, es: String) {
        if (!attached) return
        jaView.text = ja
        jaView.visibility = if (ja.isBlank()) View.GONE else View.VISIBLE
        esView.text = es
        container.visibility = if (ja.isBlank() && es.isBlank()) View.INVISIBLE else View.VISIBLE
    }

    fun hide() {
        if (attached) {
            wm.removeView(container)
            attached = false
        }
    }
}
