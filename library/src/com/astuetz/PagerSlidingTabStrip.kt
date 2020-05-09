/*
 * Copyright (C) 2013 Andreas Stuetz <andreas.stuetz@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.astuetz

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import android.os.Parcel
import android.os.Parcelable
import android.util.AttributeSet
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewTreeObserver.OnGlobalLayoutListener
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.viewpager.widget.ViewPager
import androidx.viewpager.widget.ViewPager.OnPageChangeListener
import com.astuetz.pagerslidingtabstrip.R
import java.util.*

@Suppress("unused")
class PagerSlidingTabStrip @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyle: Int = 0) :
        HorizontalScrollView(context, attrs, defStyle) {

    interface IconTabProvider {
        fun getPageIconResId(position: Int): Int
    }

    private val defaultTabLayoutParams: LinearLayout.LayoutParams
    private val expandedTabLayoutParams: LinearLayout.LayoutParams
    private val pageListener = PageListener()
    var delegatePageListener: OnPageChangeListener? = null
    private val tabsContainer: LinearLayout?
    private var pager: ViewPager? = null
    private var tabCount = 0
    private var currentPosition = 0
    private var currentPositionOffset = 0f
    private val rectPaint: Paint
    private val dividerPaint: Paint
    private var indicatorColor = -0x99999a
    private var underlineColor = 0x1A000000
    private var dividerColor = 0x1A000000
    private var shouldExpand = false
    private var isTextAllCaps = true
    private var scrollOffset = 52
    private var indicatorHeight = 8
    private var underlineHeight = 2
    private var dividerPadding = 12
    private var tabPadding = 24
    private var dividerWidth = 1
    private var tabTextSize = 12
    private var tabTextColor = -0x99999a
    private var tabTypeface: Typeface? = null
    private var tabTypefaceStyle = Typeface.BOLD
    private var lastScrollX = 0
    private var tabBackground = R.drawable.background_tab
    private var locale: Locale? = null

    init {
        isFillViewport = true
        setWillNotDraw(false)
        tabsContainer = LinearLayout(context)
        tabsContainer.orientation = LinearLayout.HORIZONTAL
        tabsContainer.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        addView(tabsContainer)
        val dm = resources.displayMetrics
        scrollOffset = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, scrollOffset.toFloat(), dm).toInt()
        indicatorHeight = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, indicatorHeight.toFloat(), dm).toInt()
        underlineHeight = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, underlineHeight.toFloat(), dm).toInt()
        dividerPadding = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dividerPadding.toFloat(), dm).toInt()
        tabPadding = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, tabPadding.toFloat(), dm).toInt()
        dividerWidth = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dividerWidth.toFloat(), dm).toInt()
        tabTextSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, tabTextSize.toFloat(), dm).toInt()

        val styleAttrs = context.obtainStyledAttributes(attrs, R.styleable.PagerSlidingTabStrip)
        // get system attrs (android:textSize and android:textColor)
        tabTextSize = styleAttrs.getDimensionPixelSize(R.styleable.PagerSlidingTabStrip_pstsTextSize, tabTextSize)
        tabTextColor = styleAttrs.getColor(R.styleable.PagerSlidingTabStrip_pstsTextColor, tabTextColor)
        // get custom attrs
        indicatorColor = styleAttrs.getColor(R.styleable.PagerSlidingTabStrip_pstsIndicatorColor, indicatorColor)
        underlineColor = styleAttrs.getColor(R.styleable.PagerSlidingTabStrip_pstsUnderlineColor, underlineColor)
        dividerColor = styleAttrs.getColor(R.styleable.PagerSlidingTabStrip_pstsDividerColor, dividerColor)
        indicatorHeight = styleAttrs.getDimensionPixelSize(R.styleable.PagerSlidingTabStrip_pstsIndicatorHeight, indicatorHeight)
        underlineHeight = styleAttrs.getDimensionPixelSize(R.styleable.PagerSlidingTabStrip_pstsUnderlineHeight, underlineHeight)
        dividerPadding = styleAttrs.getDimensionPixelSize(R.styleable.PagerSlidingTabStrip_pstsDividerPadding, dividerPadding)
        tabPadding = styleAttrs.getDimensionPixelSize(R.styleable.PagerSlidingTabStrip_pstsTabPaddingLeftRight, tabPadding)
        tabBackground = styleAttrs.getResourceId(R.styleable.PagerSlidingTabStrip_pstsTabBackground, tabBackground)
        shouldExpand = styleAttrs.getBoolean(R.styleable.PagerSlidingTabStrip_pstsShouldExpand, shouldExpand)
        scrollOffset = styleAttrs.getDimensionPixelSize(R.styleable.PagerSlidingTabStrip_pstsScrollOffset, scrollOffset)
        isTextAllCaps = styleAttrs.getBoolean(R.styleable.PagerSlidingTabStrip_pstsTextAllCaps, isTextAllCaps)
        styleAttrs.recycle()

        rectPaint = Paint()
        rectPaint.isAntiAlias = true
        rectPaint.style = Paint.Style.FILL
        dividerPaint = Paint()
        dividerPaint.isAntiAlias = true
        dividerPaint.strokeWidth = dividerWidth.toFloat()
        defaultTabLayoutParams = LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT)
        expandedTabLayoutParams = LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1.0f)
        if (locale == null) {
            locale = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                resources.configuration.locales.get(0)
            } else {
                @Suppress("DEPRECATION")
                resources.configuration.locale
            }
        }
    }

    fun setViewPager(pager: ViewPager) {
        this.pager = pager
        checkNotNull(pager.adapter) { "ViewPager does not have adapter instance." }

        pager.addOnPageChangeListener(pageListener)
        notifyDataSetChanged()
    }

    fun setOnPageChangeListener(listener: OnPageChangeListener?) {
        delegatePageListener = listener
    }

    fun notifyDataSetChanged() {
        tabsContainer?.removeAllViews()
        tabCount = 0
        pager?.adapter?.apply {
            tabCount = count
            for (i in 0 until tabCount) {
                if (this is IconTabProvider) {
                    addIconTab(i, getPageIconResId(i))
                } else {
                    getPageTitle(i)?.let { title ->
                        if (title.isNotEmpty()) {
                            addTextTab(i, title.toString())
                        }
                    }
                }
            }
        }
        updateTabStyles()
        viewTreeObserver.addOnGlobalLayoutListener(object : OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
                    @Suppress("DEPRECATION")
                    viewTreeObserver.removeGlobalOnLayoutListener(this)
                } else {
                    viewTreeObserver.removeOnGlobalLayoutListener(this)
                }
                pager?.apply {
                    currentPosition = currentItem
                    scrollToChild(currentPosition, 0)
                }
            }
        })
    }

    private fun addTextTab(position: Int, title: String) {
        context?.apply {
            try {
                val tab = TextView(this)
                tab.text = title
                tab.gravity = Gravity.CENTER
                tab.setSingleLine()
                addTab(position, tab)
            } catch (e: Exception) {
                Log.e(PagerSlidingTabStrip::class.java.simpleName, e.toString())
            }
        }
    }

    private fun addIconTab(position: Int, resId: Int) {
        context?.apply {
            val tab = ImageButton(this)
            tab.setImageResource(resId)
            addTab(position, tab)
        }
    }

    private fun addTab(position: Int, tab: View) {
        tab.isFocusable = true
        tab.setOnClickListener { pager?.currentItem = position }
        tab.setPadding(tabPadding, 0, tabPadding, 0)
        tabsContainer?.addView(tab, position, if (shouldExpand) expandedTabLayoutParams else defaultTabLayoutParams)
    }

    private fun updateTabStyles() {
        for (i in 0 until tabCount) {
            tabsContainer?.getChildAt(i)?.let { view ->
                view.setBackgroundResource(tabBackground)
                if (view is TextView) {
                    view.setTextSize(TypedValue.COMPLEX_UNIT_PX, tabTextSize.toFloat())
                    view.setTypeface(tabTypeface, tabTypefaceStyle)
                    view.setTextColor(tabTextColor)

                    // setAllCaps() is only available from API 14, so the upper case is made manually if we are on a
                    // pre-ICS-build
                    if (isTextAllCaps) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
                            view.isAllCaps = true
                        } else {
                            if (locale != null) {
                                view.text = view.toString().toUpperCase(locale!!)
                            } else {
                                view.text = view.toString().toUpperCase(Locale.getDefault())
                            }
                        }
                    }
                }
            }
        }
    }

    private fun scrollToChild(position: Int, offset: Int) {
        if (tabCount == 0 || tabsContainer == null || tabsContainer.getChildAt(position) == null) {
            return
        }
        var newScrollX = tabsContainer.getChildAt(position).left + offset
        if (position > 0 || offset > 0) {
            newScrollX -= scrollOffset
        }
        if (newScrollX != lastScrollX) {
            lastScrollX = newScrollX
            scrollTo(newScrollX, 0)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (isInEditMode || tabCount == 0 || tabsContainer == null) {
            return
        }
        val height = height

        // draw indicator line
        rectPaint.color = indicatorColor

        // default: line below current tab
        val currentTab = tabsContainer.getChildAt(currentPosition) ?: return
        var lineLeft = currentTab.left.toFloat()
        var lineRight = currentTab.right.toFloat()

        // if there is an offset, start interpolating left and right coordinates between current and next tab
        if (currentPositionOffset > 0f && currentPosition < tabCount - 1) {
            tabsContainer.getChildAt(currentPosition + 1).let { nextTab ->
                val nextTabLeft = nextTab.left.toFloat()
                val nextTabRight = nextTab.right.toFloat()
                lineLeft = currentPositionOffset * nextTabLeft + (1f - currentPositionOffset) * lineLeft
                lineRight = currentPositionOffset * nextTabRight + (1f - currentPositionOffset) * lineRight
            }
        }
        canvas.drawRect(lineLeft, height - indicatorHeight.toFloat(), lineRight, height.toFloat(), rectPaint)

        // draw underline
        rectPaint.color = underlineColor
        canvas.drawRect(0f, height - underlineHeight.toFloat(), tabsContainer.width.toFloat(), height.toFloat(), rectPaint)

        // draw divider
        dividerPaint.color = dividerColor
        for (i in 0 until tabCount - 1) {
            tabsContainer.getChildAt(i)?.let { tab ->
                canvas.drawLine(tab.right.toFloat(), dividerPadding.toFloat(), tab.right.toFloat(), height - dividerPadding.toFloat(), dividerPaint)
            }
        }
    }

    private inner class PageListener : OnPageChangeListener {
        override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {
            tabsContainer?.getChildAt(position) ?: return
            currentPosition = position
            currentPositionOffset = positionOffset
            scrollToChild(position, (positionOffset * tabsContainer.getChildAt(position).width).toInt())
            invalidate()
            delegatePageListener?.onPageScrolled(position, positionOffset, positionOffsetPixels)
        }

        override fun onPageScrollStateChanged(state: Int) {
            pager ?: return
            if (state == ViewPager.SCROLL_STATE_IDLE) {
                scrollToChild(pager!!.currentItem, 0)
            }
            delegatePageListener?.onPageScrollStateChanged(state)
        }

        override fun onPageSelected(position: Int) {
            delegatePageListener?.onPageSelected(position)
        }
    }

    fun setIndicatorColor(indicatorColor: Int) {
        this.indicatorColor = indicatorColor
        invalidate()
    }

    fun setIndicatorColorResource(resId: Int) {
        indicatorColor = ContextCompat.getColor(context, resId)
        invalidate()
    }

    fun getIndicatorColor(): Int {
        return indicatorColor
    }

    fun setIndicatorHeight(indicatorLineHeightPx: Int) {
        indicatorHeight = indicatorLineHeightPx
        invalidate()
    }

    fun getIndicatorHeight(): Int {
        return indicatorHeight
    }

    fun setUnderlineColor(underlineColor: Int) {
        this.underlineColor = underlineColor
        invalidate()
    }

    fun setUnderlineColorResource(resId: Int) {
        underlineColor = ContextCompat.getColor(context, resId)
        invalidate()
    }

    fun getUnderlineColor(): Int {
        return underlineColor
    }

    fun setDividerColor(dividerColor: Int) {
        this.dividerColor = dividerColor
        invalidate()
    }

    fun setDividerColorResource(resId: Int) {
        dividerColor = ContextCompat.getColor(context, resId)
        invalidate()
    }

    fun getDividerColor(): Int {
        return dividerColor
    }

    fun setUnderlineHeight(underlineHeightPx: Int) {
        underlineHeight = underlineHeightPx
        invalidate()
    }

    fun getUnderlineHeight(): Int {
        return underlineHeight
    }

    fun setDividerPadding(dividerPaddingPx: Int) {
        dividerPadding = dividerPaddingPx
        invalidate()
    }

    fun getDividerPadding(): Int {
        return dividerPadding
    }

    fun setScrollOffset(scrollOffsetPx: Int) {
        scrollOffset = scrollOffsetPx
        invalidate()
    }

    fun getScrollOffset(): Int {
        return scrollOffset
    }

    fun setShouldExpand(shouldExpand: Boolean) {
        this.shouldExpand = shouldExpand
        requestLayout()
    }

    fun getShouldExpand(): Boolean {
        return shouldExpand
    }

    fun setAllCaps(textAllCaps: Boolean) {
        isTextAllCaps = textAllCaps
    }

    var textSize: Int
        get() = tabTextSize
        set(textSizePx) {
            tabTextSize = textSizePx
            updateTabStyles()
        }

    fun setTextColorResource(resId: Int) {
        tabTextColor = ContextCompat.getColor(context, resId)
        updateTabStyles()
    }

    var textColor: Int
        get() = tabTextColor
        set(textColor) {
            tabTextColor = textColor
            updateTabStyles()
        }

    fun setTypeface(typeface: Typeface?, style: Int) {
        tabTypeface = typeface
        tabTypefaceStyle = style
        updateTabStyles()
    }

    var tabPaddingLeftRight: Int
        get() = tabPadding
        set(paddingPx) {
            tabPadding = paddingPx
            updateTabStyles()
        }

    public override fun onRestoreInstanceState(state: Parcelable) {
        val savedState = state as SavedState
        super.onRestoreInstanceState(savedState.superState)
        currentPosition = savedState.currentPosition
        requestLayout()
    }

    public override fun onSaveInstanceState(): Parcelable {
        val superState = super.onSaveInstanceState()
        val savedState = SavedState(superState)
        savedState.currentPosition = currentPosition
        return savedState
    }

    internal class SavedState(superState: Parcelable?) : BaseSavedState(superState) {
        var currentPosition = 0

        override fun writeToParcel(dest: Parcel, flags: Int) {
            super.writeToParcel(dest, flags)
            dest.writeInt(currentPosition)
        }
    }
}