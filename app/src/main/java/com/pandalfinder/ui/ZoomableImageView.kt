package com.pandalfinder.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.animation.DecelerateInterpolator
import androidx.appcompat.widget.AppCompatImageView

/**
 * High-performance, gesture-driven Zoomable ImageView.
 * Features:
 * - Smooth Pinch-to-zoom (1.0x to 4.0x)
 * - Double-tap zoom-in / zoom-out with smooth animated transitions
 * - Boundary-clamped Drag & Pan when zoomed
 * - Touch event isolation (disallowing parent ViewPager2 intercept only when zoomed)
 * - Automatic Fit-Center alignment preserving exact aspect ratio
 */
class ZoomableImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatImageView(context, attrs, defStyleAttr) {

    private val currentMatrix = Matrix()
    private val matrixValues = FloatArray(9)

    private var minScale = 1.0f
    private var maxScale = 4.0f
    private var doubleTapScale = 2.5f

    private var scaleDetector: ScaleGestureDetector
    private var gestureDetector: GestureDetector

    private var isZoomed = false
    private val lastTouchPoint = PointF()
    private var activePointerId = MotionEvent.INVALID_POINTER_ID
    private var isDragging = false

    private var scaleAnimator: ValueAnimator? = null
    var onSingleTapListener: (() -> Unit)? = null

    init {
        scaleType = ScaleType.MATRIX

        scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val scaleFactor = detector.scaleFactor
                val currentScale = getScale()
                var targetScale = currentScale * scaleFactor

                if (targetScale < minScale) {
                    targetScale = minScale
                } else if (targetScale > maxScale) {
                    targetScale = maxScale
                }

                val effectiveFactor = targetScale / currentScale
                currentMatrix.postScale(effectiveFactor, effectiveFactor, detector.focusX, detector.focusY)
                checkBoundsAndApply()
                return true
            }

            override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }

            override fun onScaleEnd(detector: ScaleGestureDetector) {
                checkBoundsAndApply()
            }
        })

        gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                onSingleTapListener?.invoke()
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                val currentScale = getScale()
                val targetScale = if (currentScale > 1.2f) minScale else doubleTapScale
                animateScale(targetScale, e.x, e.y)
                return true
            }
        })
    }

    override fun setImageDrawable(drawable: Drawable?) {
        super.setImageDrawable(drawable)
        resetZoom()
    }

    override fun setImageBitmap(bm: Bitmap?) {
        super.setImageBitmap(bm)
        resetZoom()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        fitCenterImage()
    }

    fun resetZoom(animate: Boolean = false) {
        scaleAnimator?.cancel()
        if (animate && getScale() > 1.0f) {
            animateScale(minScale, width / 2f, height / 2f)
        } else {
            fitCenterImage()
        }
    }

    private fun fitCenterImage() {
        val d = drawable ?: return
        val viewWidth = width.toFloat()
        val viewHeight = height.toFloat()
        if (viewWidth <= 0 || viewHeight <= 0) return

        val drawableWidth = d.intrinsicWidth.toFloat()
        val drawableHeight = d.intrinsicHeight.toFloat()
        if (drawableWidth <= 0 || drawableHeight <= 0) return

        currentMatrix.reset()
        val scale = Math.min(viewWidth / drawableWidth, viewHeight / drawableHeight)
        val dx = (viewWidth - drawableWidth * scale) / 2f
        val dy = (viewHeight - drawableHeight * scale) / 2f

        currentMatrix.postScale(scale, scale)
        currentMatrix.postTranslate(dx, dy)
        imageMatrix = currentMatrix
        isZoomed = false
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                scaleAnimator?.cancel()
                lastTouchPoint.set(event.x, event.y)
                activePointerId = event.getPointerId(0)
                isDragging = false
                if (getScale() > 1.05f) {
                    parent?.requestDisallowInterceptTouchEvent(true)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                val pointerIndex = event.findPointerIndex(activePointerId)
                if (pointerIndex != -1) {
                    val x = event.getX(pointerIndex)
                    val y = event.getY(pointerIndex)
                    val dx = x - lastTouchPoint.x
                    val dy = y - lastTouchPoint.y

                    if (getScale() > 1.05f) {
                        if (!scaleDetector.isInProgress) {
                            currentMatrix.postTranslate(dx, dy)
                            checkBoundsAndApply()
                            parent?.requestDisallowInterceptTouchEvent(true)
                        }
                    } else {
                        // At 1.0x scale, allow ViewPager2 to handle horizontal swipe gestures
                        parent?.requestDisallowInterceptTouchEvent(false)
                    }

                    lastTouchPoint.set(x, y)
                }
            }
            MotionEvent.ACTION_POINTER_UP -> {
                val pointerIndex = event.actionIndex
                val pointerId = event.getPointerId(pointerIndex)
                if (pointerId == activePointerId) {
                    val newPointerIndex = if (pointerIndex == 0) 1 else 0
                    lastTouchPoint.set(event.getX(newPointerIndex), event.getY(newPointerIndex))
                    activePointerId = event.getPointerId(newPointerIndex)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                activePointerId = MotionEvent.INVALID_POINTER_ID
                isDragging = false
                if (getScale() <= 1.05f) {
                    fitCenterImage()
                    parent?.requestDisallowInterceptTouchEvent(false)
                }
            }
        }
        return true
    }

    private fun getScale(): Float {
        currentMatrix.getValues(matrixValues)
        return matrixValues[Matrix.MSCALE_X]
    }

    private fun checkBoundsAndApply() {
        val d = drawable ?: return
        val viewWidth = width.toFloat()
        val viewHeight = height.toFloat()
        if (viewWidth <= 0 || viewHeight <= 0) return

        val rect = RectF(0f, 0f, d.intrinsicWidth.toFloat(), d.intrinsicHeight.toFloat())
        currentMatrix.mapRect(rect)

        var deltaX = 0f
        var deltaY = 0f

        // Center horizontally if smaller than view, otherwise clamp edges
        if (rect.width() <= viewWidth) {
            deltaX = (viewWidth - rect.width()) / 2f - rect.left
        } else {
            if (rect.left > 0) deltaX = -rect.left
            if (rect.right < viewWidth) deltaX = viewWidth - rect.right
        }

        // Center vertically if smaller than view, otherwise clamp edges
        if (rect.height() <= viewHeight) {
            deltaY = (viewHeight - rect.height()) / 2f - rect.top
        } else {
            if (rect.top > 0) deltaY = -rect.top
            if (rect.bottom < viewHeight) deltaY = viewHeight - rect.bottom
        }

        currentMatrix.postTranslate(deltaX, deltaY)
        imageMatrix = currentMatrix
        isZoomed = getScale() > 1.05f
    }

    private fun animateScale(targetScale: Float, focusX: Float, focusY: Float) {
        scaleAnimator?.cancel()
        val startScale = getScale()

        scaleAnimator = ValueAnimator.ofFloat(startScale, targetScale).apply {
            duration = 240
            interpolator = DecelerateInterpolator()
            addUpdateListener { anim ->
                val animatedValue = anim.animatedValue as Float
                val current = getScale()
                if (current > 0f) {
                    val factor = animatedValue / current
                    currentMatrix.postScale(factor, factor, focusX, focusY)
                    checkBoundsAndApply()
                }
            }
            start()
        }
    }
}
