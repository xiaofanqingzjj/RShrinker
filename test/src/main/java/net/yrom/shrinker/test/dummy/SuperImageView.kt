package net.yrom.shrinker.test.dummy

import android.content.Context
import android.graphics.Canvas
import android.support.v7.widget.AppCompatImageView
import android.util.AttributeSet
import android.util.Log
import android.widget.ImageView
import net.yrom.shrinker.test.R
import java.util.*

/**
 *
 * 一个功能扩展功能比较多的ImageView
 *
 * 1.系统的ImageView只支持CenterCrop, 这个ImageView支持各个方向的Crop
 * 2.支持各个方向的fit
 * 3.支持圆角，包括各个方法的圆角
 * 4.支持边框
 *
 * @author fortunexiao
 */
class SuperImageView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : AppCompatImageView(context, attrs) {


    companion object {

        const val TAG = "SuperImageView"


        private fun getYTranslation(cropType: ScaleType?, viewHeight: Int, postDrawabeHeigth: Float, verticalImageMode: Boolean): Float { //        if (verticalImageMode) {
            when (cropType) {
                ScaleType.CROP_CENTER_BOTTOM,
                ScaleType.CROP_LEFT_BOTTOM,
                ScaleType.CROP_RIGHT_BOTTOM,
                ScaleType.FIT_BOTTOM_LEFT,
                ScaleType.FIT_BOTTOM_CENTER,
                ScaleType.FIT_BOTTOM_RIGHT -> return viewHeight - postDrawabeHeigth

                ScaleType.CROP_LEFT_CENTER,
                ScaleType.CROP_RIGHT_CENTER ->  // View in the middle of the screen
                    return (viewHeight - postDrawabeHeigth) / 2f
            }

            // All other cases we don't need to translate
            return 0f
        }

        private fun getXTranslation(cropType: ScaleType?, viewWidth: Int, postDrawableWidth: Float, verticalImageMode: Boolean): Float {
            if (!verticalImageMode) {
                when (cropType) {
                    ScaleType.CROP_RIGHT_TOP,
                    ScaleType.CROP_RIGHT_CENTER,
                    ScaleType.CROP_RIGHT_BOTTOM,
                    ScaleType.FIT_TOP_RIGHT,
                    ScaleType.FIT_BOTTOM_RIGHT -> return viewWidth - postDrawableWidth

                    ScaleType.CROP_CENTER_TOP,
                    ScaleType.CROP_CENTER_BOTTOM,
                    ScaleType.FIT_TOP_CENTER,
                    ScaleType.FIT_BOTTOM_CENTER ->  // View in the middle of the screen
                        return (viewWidth - postDrawableWidth) / 2f
                }
            }

            // All other cases we don't need to translate
            return 0f
        }
    }


    private var mScaleType: ScaleType? = ScaleType.NONE

    private var roundedImageHelper: RoundedImageHelper? = null

    init {
        initImageView(attrs)
    }


    /**
     * Set crop type for the [ImageView]
     *
     * @param mScaleType A [ScaleType] desired scaling mode.
     */
    fun setScaleType(mScaleType: ScaleType?) {
        if (mScaleType == null) {
            throw NullPointerException()
        }
        if (this.mScaleType != mScaleType) {
            this.mScaleType = mScaleType
            setWillNotCacheDrawing(false)
            requestLayout()
            invalidate()
        }
    }

    private fun initImageView(attrs: AttributeSet?) {
        val a = context.obtainStyledAttributes(attrs, R.styleable.SuperImageView)
        val crop = a.getInt(R.styleable.SuperImageView_siv_scaleType, ScaleType.NONE.crop)
        if (crop >= 0) { // scaleType为matrix
            scaleType = ImageView.ScaleType.MATRIX
            mScaleType = ScaleType[crop]
        }
        a.recycle()

        roundedImageHelper = RoundedImageHelper(this, attrs)
    }


    override fun draw(canvas: Canvas) {
        if (roundedImageHelper != null) {
            roundedImageHelper!!.draw(canvas) {
                super@SuperImageView.draw(canvas)
                null
            }
        } else {
            super.draw(canvas)
        }
    }

    override fun setFrame(l: Int, t: Int, r: Int, b: Int): Boolean {
        val changed = super.setFrame(l, t, r, b)
        if (!isInEditMode && drawable != null) {
            computeImageMatrix()
        }
        return changed
    }

    private fun computeImageMatrix() { // view的大小
        val viewWidth = width - paddingLeft - paddingRight
        val viewHeight = height - paddingTop - paddingBottom

        if (mScaleType != ScaleType.NONE && viewHeight > 0 && viewWidth > 0) {

            val matrix = imageMatrix //imageMaths.getMatrix();

            // 图片大小
            val drawableWidth = drawable.intrinsicWidth
            val drawableHeight = drawable.intrinsicHeight
            Log.d(TAG, "computeImageMatrix imageSize:" + drawableWidth + "x" + drawableHeight + ", viewSize:" + viewWidth + "x" + viewHeight)

            if (drawableHeight == -1 || drawableWidth == -1) {
                postInvalidate()
                return
            }

            val scaleY = viewHeight.toFloat() / drawableHeight.toFloat()
            val scaleX = viewWidth.toFloat() / drawableWidth.toFloat()

            // 设置缩放，匹配短的边，长的边超过的裁剪

            var scale = -1f
            scale = if (mScaleType!!.crop <= 7) { // crop
                if (scaleX > scaleY) scaleX else scaleY
            } else { // fit
                if (scaleX < scaleY) scaleX else scaleY
            }

            //                    scaleX > scaleY ? scaleX : scaleY;
            matrix.setScale(scale, scale) // Same as doing matrix.reset() and matrix.preScale(...)

            // 竖图
            val verticalImageMode = scaleX > scaleY

            // 计算缩放之后的目标宽度
            val postDrawableWidth = drawableWidth * scale

            // 设置X方向的平移
            val xTranslation = getXTranslation(mScaleType, viewWidth, postDrawableWidth, verticalImageMode)

            // 缩放之后的高度
            val postDrawableHeight = drawableHeight * scale

            // Y方向的平移
            val yTranslation = getYTranslation(mScaleType, viewHeight, postDrawableHeight, verticalImageMode)

            Log.d(TAG, "xTranslation:$xTranslation, yTranslation:$yTranslation, scale:$scale")
            //            matrix.setTranslate();
            matrix.postTranslate(xTranslation, yTranslation)
            imageMatrix = matrix
        }
    }

    /**
     * Options for cropping the bounds of an image to the bounds of this view.
     */
    enum class ScaleType(val crop: Int) {
        NONE(-1),

        // crop
        CROP_LEFT_TOP(0),
        CROP_LEFT_CENTER(1),
        CROP_LEFT_BOTTOM(2),
        CROP_RIGHT_TOP(3),
        CROP_RIGHT_CENTER(4),
        CROP_RIGHT_BOTTOM(5),
        CROP_CENTER_TOP(6),
        CROP_CENTER_BOTTOM(7),

        // fit
        FIT_TOP_LEFT(8),
        FIT_TOP_CENTER(9),
        FIT_TOP_RIGHT(10),
        FIT_BOTTOM_LEFT(11),
        FIT_BOTTOM_CENTER(12),
        FIT_BOTTOM_RIGHT(13);

        companion object {
            // Reverse-lookup map for getting a day from an abbreviation
            private val codeLookup: MutableMap<Int, ScaleType> = HashMap()

            operator fun get(number: Int): ScaleType? {
                return codeLookup[number]
            }

            init {
                for (ft in values()) {
                    codeLookup[ft.crop] = ft
                }
            }
        }

    }


}