
## 前言
`CollapsingToolbarLayout` 是 android material deign 里一个十分优秀的组件，它可以用非常少的代码实现很漂亮的滑动效果。关于这个组件的用法，网上已经很多了，这里不再赘述。今天项目中有一个需求，大概是这个样子的：
![out.gif](https://upload-images.jianshu.io/upload_images/12199876-22195985e54caef1.gif?imageMogr2/auto-orient/strip)

而 ` CollapsingToolbarLayout ` 只支持 `Title` 的折叠和滚动，不支持其他组件一起滚动，所以就有必要修改一下。

## 源码解析
`CollapsingToolbarLayout` 源码在 `android.support.design.widget` 包下，AndroidStudio里按住 Ctrl 键鼠标左键点进去就能看到，或者在线查看（比如 [androidos.net.cn](https://androidos.net.cn)）。
那么首先是构造函数
```
    public CollapsingToolbarLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        ThemeUtils.checkAppCompatTheme(context);

        mCollapsingTextHelper = new CollapsingTextHelper(this);
       ...省略部分代码...
        TypedArray a = context.obtainStyledAttributes(attrs,
                R.styleable.CollapsingToolbarLayout, defStyleAttr,
                R.style.Widget_Design_CollapsingToolbar);

       ... 省略部分代码 ...

        a.recycle();

        setWillNotDraw(false);

       ViewCompat.setOnApplyWindowInsetsListener(this,
                new android.support.v4.view.OnApplyWindowInsetsListener() {
                    @Override
                    public WindowInsetsCompat onApplyWindowInsets(View v,
                            WindowInsetsCompat insets) {
                        return onWindowInsetChanged(insets);
                    }
                });
    }
```
这部分比较简单，就是解析 xml 中设置的属性，比如 `expandedTitleMargin`，`expandedTitleTextAppearance` 等等。
然后在 `onAttachedToWindow`方法中，发现注册了 `AppbarLayout`的监听器
```
    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        // 添加 AppbarLayout 监听器
        final ViewParent parent = getParent();
        if (parent instanceof AppBarLayout) {
            // 从父 AppbarLayout 中获得 fitSystemStatus 属性
            ViewCompat.setFitsSystemWindows(this, ViewCompat.getFitsSystemWindows((View) parent));

            if (mOnOffsetChangedListener == null) {
                mOnOffsetChangedListener = new OffsetUpdateListener();
            }
            ((AppBarLayout) parent).addOnOffsetChangedListener(mOnOffsetChangedListener);

            // 请求适配到状态栏
            ViewCompat.requestApplyInsets(this);
        }
    }
```
相应地，`onDetachedFromWindow` 方法中解除了注册
```
    @Override
    protected void onDetachedFromWindow() {
        // 移除 AppbarLayout 监听器
        final ViewParent parent = getParent();
        if (mOnOffsetChangedListener != null && parent instanceof AppBarLayout) {
            AppBarLayout appBar = (AppBarLayout) parent;
            appBar.removeOnOffsetChangedListener(mOnOffsetChangedListener);
        }
       super.onDetachedFromWindow();
    }
```
接下来分析下，随着 `AppbarLayout` 的滚动，`CollapsingToolbarLayout` 做了些什么
```

        @Override
        public void onOffsetChanged(AppBarLayout layout, int verticalOffset) {
            mCurrentOffset = verticalOffset;

            final int insetTop = mLastInsets != null ? mLastInsets.getSystemWindowInsetTop() : 0;

            for (int i = 0, z = getChildCount(); i < z; i++) {
                final View child = getChildAt(i);
                final LayoutParams lp = (LayoutParams) child.getLayoutParams();
                final ViewOffsetHelper offsetHelper = getViewOffsetHelper(child);

                // 根据子 view 的折叠模式（collapseMode）分别处理
                switch (lp.mCollapseMode) {
                    case LayoutParams.COLLAPSE_MODE_PIN:
                        // 别针模式（pin）：会同时下移相同的偏移量，故在 y 方向上不动
                        offsetHelper.setTopAndBottomOffset(MathUtils.clamp(
                                -verticalOffset, 0, getMaxOffsetForPinChild(child)));
                        break;
                    case LayoutParams.COLLAPSE_MODE_PARALLAX:
                        // 视差模式（parallax）：向下移一段小于 verticalOffset 的距离，
                        // 这个值取决于 mParallaxMulti 这个小数
                        offsetHelper.setTopAndBottomOffset(
                                Math.round(-verticalOffset * lp.mParallaxMult));
                        break;
                }
            }

            // 更新背景
            updateScrimVisibility();

            if (mStatusBarScrim != null && insetTop > 0) {
                ViewCompat.postInvalidateOnAnimation(CollapsingToolbarLayout.this);
            }

            // 更新 title 的位置和大小等
            final int expandRange = getHeight() - ViewCompat.getMinimumHeight(
                    CollapsingToolbarLayout.this) - insetTop;
            mCollapsingTextHelper.setExpansionFraction(
                    Math.abs(verticalOffset) / (float) expandRange);
        }
```
如果要引入新的属性的话，需要修改的部分就是 `LayoutParams` 和 `OffsetUpdateListener` 这两个类。这里本来打算继承自 `CollapsingToolbarLayout` 类，结果发现很多方法和域是`private`的，而且 要增加`declare-styleable` 的属性也很麻烦，没办法使用下下策，即把源码复制过来，在此基础上修改。当然了一些支持类也要拷贝过来，比如 `ThemeUtils`，`ViewGroupUtils`等。

## 思路整理
到这里源码的大致思路分析地差不多了，接下来就是怎么增加和处理我们自己的属性。对于一个子 `view`，如果要跟着 `AppbarLayout` 一起滚动的话，首先要得到 `AppBarLayout` 展开时的位置，然后是折叠时的位置，这两个位置就像一次函数的两个端点。这样以 `AppbarLayout` 偏移量作为x轴，就能计算得到不同位置这个 `view` 的具体位置。举个例子，某子`view` 在拓展时的坐标是 `(100, 280)`，折叠时的坐标是`(150, 110)`，而 `AppbarLayout` 的折叠范围是`300`，当前折叠量为`x`，那么当前`view`的坐标就是`((x/300 * (150-100)+100), (x/300 * (110-280) + 280))`

在一般的情况下，当前折叠量可以从 `onOffsetChanged()` 的参数中获得，折叠范围可以按照官方已经算出来了，即 
```
final int expandRange = getHeight() - ViewCompat.getMinimumHeight(
                    CollapsingToolbarLayout.this) - insetTop;
```
那么只剩下折叠时和展开时的两个坐标了。这两个坐标可以在 `onLayout()` 函数中计算出来。

## 定义 declared-style 属性
`CollapsingToolbarLayout` 给了我们很多属性可以自定义，如`expandedTitleGravity`，`expandedTitleMarginStart` 等等，用起来十分方便，然而只限于`title`。不如我们也可以仿照一下，增加几个常用的属性
```
<declare-styleable name="ScrollCollapsingLayout_Layout">
        <attr name="collapseMode">
            <enum name="none" value="0"/>
            <enum name="pin" value="1"/>
            <enum name="parallax" value="2"/>
            <enum name="scroll" value="3" />
        </attr>

        <attr name="collapseParallaxMultiplier" />

        <attr name="collapsedGravity">
            <flag name="top" value="0x30"/>
            <flag name="bottom" value="0x50"/>
            <flag name="left" value="0x03"/>
            <flag name="right" value="0x05"/>
            <flag name="center_vertical" value="0x10"/>
            <flag name="fill_vertical" value="0x70"/>
            <flag name="center_horizontal" value="0x01"/>
            <flag name="center" value="0x11"/>
            <flag name="start" value="0x00800003"/>
            <flag name="end" value="0x00800005"/>
        </attr>

        <attr name="collapsedMargin" format="dimension"/>
        <attr name="collapsedMarginStart" format="dimension"/>
        <attr name="collapsedMarginTop" format="dimension"/>
        <attr name="collapsedMarginEnd" format="dimension"/>
        <attr name="collapsedMarginBottom" format="dimension"/>
    </declare-styleable>
```
需要注意的是，`collapseMode` 是仿照 `layout_collapseMode` 来的，因为后者已经被定义了，没办法重新定义。之所以给了 `collapsedGravity` 接口而没有 `expandedGravity`，是考虑到 `view` 已经有了 `layout_gravity`，`layout_margin`等属性，干脆就直接拿来用了。

然后就是在 LayoutParams 的 `LayoutParams(Context c, AttributeSet attrs)` 中初始化变量。
```

        private int mCollapsedGravity;

        private int mCollapsedMarginStart;
        private int mCollapsedMarginTop;
        private int mCollapsedMarginEnd;
        private int mCollapsedMarginBottom;

        public LayoutParams(Context c, AttributeSet attrs) {
            super(c, attrs);

            TypedArray a = c.obtainStyledAttributes(attrs,
                    R.styleable.ScrollCollapsingLayout_Layout);

            mCollapseMode = a.getInt(R.styleable.
                    ScrollCollapsingLayout_Layout_collapseMode,
                    COLLAPSE_MODE_OFF);

            mParallaxMult = a.getFloat(R.styleable.
                    ScrollCollapsingLayout_Layout_collapseParallaxMultiplier,
                    DEFAULT_PARALLAX_MULTIPLIER);

            mCollapsedGravity = a.getInt(R.styleable.
                    ScrollCollapsingLayout_Layout_collapsedGravity,
                    Gravity.START|Gravity.CENTER_VERTICAL);

            int margin = a.getDimensionPixelSize(R.styleable.
                    ScrollCollapsingLayout_Layout_collapsedMargin, 0);
            mCollapsedMarginStart = a.getDimensionPixelSize(R.styleable.
                    ScrollCollapsingLayout_Layout_collapsedMarginStart, margin);
            mCollapsedMarginTop = a.getDimensionPixelSize(R.styleable.
                    ScrollCollapsingLayout_Layout_collapsedMarginTop, margin);
            mCollapsedMarginEnd = a.getDimensionPixelSize(R.styleable.
                    ScrollCollapsingLayout_Layout_collapsedMarginEnd, margin);
            mCollapsedMarginBottom = a.getDimensionPixelSize(R.styleable.
                    ScrollCollapsingLayout_Layout_collapsedMarginBottom, margin);

            a.recycle();
        }
```
接下来就是计算上面说的两个端点的位置。我们是放在 `ScrollCollapsingLayout` 的 `onLayout()` 方法中，因为此时所有的子`view` 已经测量完毕了。
```

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        ... 省略部分代码 ...
        // 通知子 view 测量布局区域
        updateScrollChild(left, top, right, bottom);
    }

    private void updateScrollChild(int left, int top, int right, int bottom) {
        int collapsedLeft = left;
        int collapsedTop = top;
        int collapsedRight = right;
        int collapsedBottom = bottom;

        if (mToolbar != null) {
            collapsedLeft = mTmpRect.left;
            collapsedTop = mTmpRect.top;
            collapsedRight = mTmpRect.right;
            collapsedBottom = mTmpRect.bottom;
        }

        for (int i = 0, z = getChildCount(); i < z; i++) {
            View v = getChildAt(i);
            LayoutParams lp = (LayoutParams) v.getLayoutParams();
            if (lp.mCollapseMode == LayoutParams.COLLAPSE_MODE_SCROLL) {
                lp.setCollapsedBounds(collapsedLeft, collapsedTop,
                        collapsedRight, collapsedBottom);
                lp.setExpandedBounds(v.getLeft(), v.getTop(),
                        v.getRight(), v.getBottom());
                lp.recalculate();
            }
        }
    }
```
接下来就是在 `LayoutParams` 中具体计算
```

        void setCollapsedBounds(int l, int t, int r, int b) {
            collapsedRect.left = l;
            collapsedRect.top = t;
            collapsedRect.right = r;
            collapsedRect.bottom = b;
        }

        void setExpandedBounds(int l, int t, int r, int b) {
            expandedRect.left = l;
            expandedRect.top = t;
            expandedRect.right = r;
            expandedRect.bottom = b;
        }

        void recalculate() {

            boolean isRtl = getLayoutDirection() == LAYOUT_DIRECTION_RTL;

            final int collapsedAbsGravity = Gravity.getAbsoluteGravity(
                    mCollapsedGravity, getLayoutDirection());

            switch (collapsedAbsGravity & Gravity.VERTICAL_GRAVITY_MASK) {
                case Gravity.BOTTOM:
                    collapsedRect.bottom -= mCollapsedMarginBottom;
                    collapsedRect.top = collapsedRect.bottom - height;
                    break;
                case Gravity.TOP:
                    collapsedRect.top += mCollapsedMarginTop;
                    collapsedRect.bottom = collapsedRect.top + height;
                    break;
                case Gravity.CENTER_VERTICAL:
                default:
                    collapsedRect.top = (collapsedRect.top + collapsedRect.bottom) /2
                            - height /2;
                    collapsedRect.bottom = collapsedRect.top + height;
                    break;
            }

            switch (collapsedAbsGravity & Gravity.RELATIVE_HORIZONTAL_GRAVITY_MASK) {
                case Gravity.RIGHT:
                    collapsedRect.right -= isRtl ? mCollapsedMarginStart : mCollapsedMarginEnd;
                    collapsedRect.left -= collapsedRect.right - width;
                    break;
                case Gravity.CENTER_HORIZONTAL:
                    collapsedRect.left = (collapsedRect.left + collapsedRect.right) /2
                            - width /2;
                    collapsedRect.right = collapsedRect.left + width;
                    break;
                case Gravity.LEFT:
                default:
                    collapsedRect.left += isRtl ? mCollapsedMarginEnd : mCollapsedMarginStart;
                    collapsedRect.right = collapsedRect.left + width;
                    break;
            }
        }
```
计算完成之后，接下来就是在 `OffsetUpdateListener` 中处理偏移量
```

        @Override
        public void onOffsetChanged(AppBarLayout layout, int verticalOffset) {
            ... 省略部分代码 ...
            final int expandRange = getHeight() - getMinimumHeight() - insetTop;
            final float percent = -1.0f * verticalOffset / expandRange;

                    ... 省略部分代码 ...
                    case LayoutParams.COLLAPSE_MODE_SCROLL:
                        offsetHelper.setLeftAndRightOffset((int)
                                (percent * (lp.collapsedRect.left - lp.expandedRect.left)));
                        // 这里要多加一个偏移量，把竖直方向纠正过来
                        offsetHelper.setTopAndBottomOffset(-verticalOffset + (int)
                                (percent * (lp.collapsedRect.top - lp.expandedRect.top)));
                        break;
                ... 省略部分代码 ...
            }
        }
```
## 效果检验
```
<android.support.design.widget.CoordinatorLayout>
    <android.support.design.widget.AppBarLayout>
        <cn.nlifew.scrollcollapsinglayout.widget.ScrollCollapsingLayout>

            <android.support.v7.widget.Toolbar
                ... />

            <ImageView
                android:id="@+id/activity_main_image"
                android:src="@drawable/ic_account_circle"
                android:layout_width="50dp"
                android:layout_height="50dp"
                android:layout_gravity="bottom|start"
                android:layout_marginStart="25dp"
                android:layout_marginBottom="15dp"
                app:collapseMode="scroll" />
        </cn.nlifew.scrollcollapsinglayout.widget.ScrollCollapsingLayout>
    </android.support.design.widget.AppBarLayout>
</android.support.design.widget.CoordinatorLayout>
```
效果的话大概就是如图上所示了
##说明
* 因为要用到`CollapsingToolbar` 的一些私有变量和方法，这里不是用的继承的方式，而是直接拷贝源码的方式，不建议这么做
* 移动 `view` 的方式有很多种，这里有的是`ViewOffsetHelper`这个类，本质上还是 `view.offsetTopAndBottom` 和 `view.offsetLeftAndRight`这两个方法，读者可自行修改。
* 因为 AndroidStudio 本身的 bug，在xml中使用自定义属性时可能会提示错误`Unexpected namespace prefix 'app' ...`，但其实编译运行都没问题的，可以在 `File-Settings-Editor-Inspections-Android-Lint-Correctness-Missing Android XML namespace`去掉勾选
## 下载
项目已开源至 [github](https://github.com/nlifew/ScrollCollapsingLayout)

