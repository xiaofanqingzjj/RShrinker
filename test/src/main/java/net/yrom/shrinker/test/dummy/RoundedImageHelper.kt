package net.yrom.shrinker.test.dummy


import android.graphics.Canvas
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.util.Log
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.ImageView
import net.yrom.shrinker.test.R


/**
 * 一个简单的支持圆角的图片组件
 *
 * @author fortune
 */
class RoundedImageHelper( var imageView: ImageView, attrs: AttributeSet? = null)  {

    companion object {
        const val TAG = "RoundedImageHelper"
    }

    private val cornerRadiuses = FloatArray(4)

    private var hasSingleCorner = false
    private var mClipPath: Path? = null

    private var borderColor: Int = 0
    private var borderWidth: Int = 0

    private var cornerRadius: Int = 0

    private var borderPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private var isCircle = false

    init {
        attrs?.run {
            val a = imageView.context.obtainStyledAttributes(attrs, R.styleable.SuperImageView)

            isCircle = a.getBoolean(R.styleable.SuperImageView_siv_circle, false)


            cornerRadius = a.getDimensionPixelSize(R.styleable.SuperImageView_siv_corner_radius, 0)

            val radius = FloatArray(4)
            radius[0] = a.getDimensionPixelSize(R.styleable.SuperImageView_siv_corner_radius_top_left, 0).toFloat()
            radius[1] = a.getDimensionPixelSize(R.styleable.SuperImageView_siv_corner_radius_top_right, 0).toFloat()
            radius[2] = a.getDimensionPixelSize(R.styleable.SuperImageView_siv_corner_radius_bottom_right, 0).toFloat()
            radius[3] = a.getDimensionPixelSize(R.styleable.SuperImageView_siv_corner_radius_bottom_left, 0).toFloat()

            borderWidth = a.getDimensionPixelSize(R.styleable.SuperImageView_siv_border_width, 0)
            borderColor = a.getColor(R.styleable.SuperImageView_siv_border_color, 0)

            a.recycle()
            radius.forEach {
                if (it != 0f) {
                    hasSingleCorner = true
                }
            }

            if (hasSingleCorner) {
                for (i in 0..3) {
                    cornerRadiuses[i] = radius[i]
                }
            }


            applyCorner()
        }
    }

    private fun applyCorner() {
        if (cornerRadius != 0 || hasSingleCorner || isCircle) {
            imageView.outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View?, outline: Outline?) {
                    view?.run {
                        Log.d(TAG, "applyCorner:$cornerRadius, hasSingle:$hasSingleCorner")
                        mClipPath = Path()

                        if (isCircle) { // 圆形
                            val r = if (width > height) height / 2f else width / 2f
                            mClipPath?.addCircle(width / 2f, height / 2f, r, Path.Direction.CCW)
                        } else if (cornerRadius > 0) {
                            mClipPath?.addRoundRect(0f, 0f, width.toFloat(), height.toFloat(), cornerRadius.toFloat(), cornerRadius.toFloat(), Path.Direction.CCW)
                        } else if (hasSingleCorner) {
                            mClipPath?.addRoundRect(0f, 0f, width.toFloat(), height.toFloat(), floatArrayOf(
                                    cornerRadiuses[0], cornerRadiuses[0], cornerRadiuses[1], cornerRadiuses[1], cornerRadiuses[2], cornerRadiuses[2], cornerRadiuses[3], cornerRadiuses[3]
                            ), Path.Direction.CCW)
                        } else {}

//
//                        if (cornerRadius > 0) {
//                            mClipPath?.addRoundRect(0f, 0f, width.toFloat(), height.toFloat(), cornerRadius.toFloat(), cornerRadius.toFloat(), Path.Direction.CCW)
//                        } else if (hasSingleCorner){
//                            mClipPath?.addRoundRect(0f, 0f, width.toFloat(), height.toFloat(), floatArrayOf(
//                                    cornerRadiuses[0], cornerRadiuses[0], cornerRadiuses[1], cornerRadiuses[1], cornerRadiuses[2], cornerRadiuses[2], cornerRadiuses[3], cornerRadiuses[3]
//                            ), Path.Direction.CCW)
//                        } else {
//
//                        }
                    }
                }
            }

        }
    }

    fun draw(canvas: Canvas?, orgDraw:()->Unit) {
        canvas?.run {
            save()

            if (mClipPath != null ) {
                clipPath(mClipPath!!)
            }

            orgDraw?.invoke()
//            imageView.draw(canvas)

            if (borderWidth != 0 && mClipPath != null) {
                borderPaint.color = borderColor
                borderPaint.style = Paint.Style.STROKE
                borderPaint.strokeWidth = borderWidth.toFloat()
                canvas.drawPath(mClipPath!!, borderPaint)
            }
            restore()
        }
    }


}

