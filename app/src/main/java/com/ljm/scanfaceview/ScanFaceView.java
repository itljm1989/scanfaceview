package com.ljm.scanfaceview;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

import java.util.List;


/**
 * @author: ljm
 * 创建日期:2022/7/28
 */
public class ScanFaceView extends View {
    private static final String TAG = "ScanFaceView";
    // 默认扫描圆圈的半径
    private static final int DEFAULT_CIRCLE_RADIUS = 270;
    private Context mContext;
    /**
     * 自身的宽高
     */
    private int mWidth, mHeight;

    private Paint mBgPaint;

    private Paint mTextPaint;

    private Paint mCirclePaint;

    private float circleCenterX;

    private float circleCenterY;

    private Paint mRectPaint;
    private ValueAnimator mValueAnimator;
    private Bitmap scanBitmap;

    private Matrix bitmapMatrix = new Matrix();
    private boolean mIsHorizontal;
    private boolean mIsVertical;
    private float mCircle_radius;
    private int mInner_circle_color;
    private float mCircle_marginTop;
    private float mCircle_marginLeft;
    private float mCircle_marginRight;
    private float mCircle_marginBottom;
    private int mAnim_duration;
    private int mScan_img_resource_id;
    private FaceBox mFaceBox;
    private Paint mTransparentPaint;

    public ScanFaceView(Context context) {
        this(context, null);
    }

    public ScanFaceView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ScanFaceView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        TypedArray typedArray = context.obtainStyledAttributes(attrs, R.styleable.ScanFaceView);
        mIsHorizontal = typedArray.getBoolean(R.styleable.ScanFaceView_circle_centerHorizontal, false);
        mIsVertical = typedArray.getBoolean(R.styleable.ScanFaceView_circle_centerVertical, false);
        mCircle_radius = typedArray.getDimension(R.styleable.ScanFaceView_circle_radius, DEFAULT_CIRCLE_RADIUS);
        mInner_circle_color = typedArray.getColor(R.styleable.ScanFaceView_inner_circle_color, Color.parseColor("#008ED6"));
        mCircle_marginTop = typedArray.getDimension(R.styleable.ScanFaceView_circle_marginTop, 0);
        mCircle_marginLeft = typedArray.getDimension(R.styleable.ScanFaceView_circle_marginLeft, 0);
        mCircle_marginRight = typedArray.getDimension(R.styleable.ScanFaceView_circle_marginRight, 0);
        mCircle_marginBottom = typedArray.getDimension(R.styleable.ScanFaceView_circle_marginBottom, 0);
        mAnim_duration = typedArray.getInteger(R.styleable.ScanFaceView_anim_duration, 1200);
        mScan_img_resource_id = typedArray.getResourceId(R.styleable.ScanFaceView_unit_scan_img, R.mipmap.icon_scan_line);
        Log.i(TAG, "ScanFaceView: isHorizontal=" + mIsHorizontal
                + "//isVertical=" + mIsVertical
                + "//circle_radius=" + mCircle_radius
                + "//inner_circle_color=" + mInner_circle_color
                + "//circle_marginTop=" + mCircle_marginTop
                + "//circle_marginLeft=" + mCircle_marginLeft
                + "//circle_marginRight=" + mCircle_marginRight
                + "//circle_marginBottom=" + mCircle_marginBottom
                + "//anim_duration=" + mAnim_duration
                + "//scan_img_resource_id=" + mScan_img_resource_id);
        init(context);
    }

    private void init(Context context) {
        // 禁用硬件加速
        setWillNotDraw(false);
        setLayerType(LAYER_TYPE_HARDWARE, null);
        this.mContext = context;
        Log.i(TAG, "init: ");
        mFaceBox = new FaceBox();
        initBgPaint();
        initTextPaint();
        initCirclePaint();
        initRectPaint();
        initTransparentPaint();
    }

    private ValueAnimator.AnimatorUpdateListener getUpdateListener() {
        return animation -> {
            float value = (float) animation.getAnimatedValue();
            bitmapMatrix.setTranslate(circleCenterX - mCircle_radius, value);
            bitmapMatrix.preScale(1.0f, -1.0f);
            invalidate();
        };
    }

    private void initRectPaint() {
        mRectPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mRectPaint.setStyle(Paint.Style.FILL);
        mRectPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OVER));
    }

    private void initCirclePaint() {
        mCirclePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mCirclePaint.setStyle(Paint.Style.STROKE);
        mCirclePaint.setColor(mInner_circle_color);
        mCirclePaint.setStrokeWidth(6);
    }

    private void initTextPaint() {
        mTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mTextPaint.setTextSize(24);
        mTextPaint.setColor(Color.parseColor("#0000ff"));
        mTextPaint.setTextAlign(Paint.Align.CENTER);
        mTextPaint.setLetterSpacing(0.1f);
    }

    private void initBgPaint() {
        mBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mBgPaint.setStyle(Paint.Style.FILL);
        mBgPaint.setColor(Color.WHITE);
    }

    private void initTransparentPaint() {
        mTransparentPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mTransparentPaint.setStyle(Paint.Style.FILL);
        mTransparentPaint.setColor(Color.TRANSPARENT);
        mTransparentPaint.setAlpha(0);
        mTransparentPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_OUT));
    }

    private void drawCircleMask(Canvas canvas) {
        Log.i(TAG, "drawCircleMask");
        canvas.save();
        canvas.drawRect(new Rect(0, 0, mWidth, mHeight), mBgPaint);
        //设置混合模式
        mBgPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        //源图Src，重叠区域右下角部分
        canvas.drawCircle(circleCenterX, circleCenterY, mCircle_radius, mBgPaint);
        canvas.drawCircle(circleCenterX, circleCenterY, mCircle_radius + 10, mCirclePaint);
        //清除混合模式
        mBgPaint.setXfermode(null);
        canvas.restore();
    }


    public void setFaces(List<RectF> faces) {
        mFaceBox.setFaces(faces);
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        Log.i(TAG, "onSizeChanged: ");
        mWidth = w;
        mHeight = h;
        if (mIsHorizontal) {
            circleCenterX = mWidth / 2.0f;
        } else {
            circleCenterX = mCircle_marginLeft + mCircle_radius;
        }
        if (mIsVertical) {
            circleCenterY = mHeight / 2.0f;
        } else {
            circleCenterY = mCircle_marginTop + mCircle_radius;
        }
        setScanBitmap();
        startAnimator();
    }

    public void startAnimator() {
        mValueAnimator = ValueAnimator.ofFloat(circleCenterY - mCircle_radius, circleCenterY + mCircle_radius);
        mValueAnimator.setDuration(mAnim_duration);
        mValueAnimator.setRepeatCount(-1);
        mValueAnimator.setRepeatMode(ValueAnimator.REVERSE);
        mValueAnimator.addUpdateListener(getUpdateListener());
        mValueAnimator.start();
    }

    private void stopAnimator() {
        if (mValueAnimator != null && mValueAnimator.isRunning()) {
            mValueAnimator.cancel();
            reset(true);
        }
    }

    private void setScanBitmap() {
        Bitmap scanLineBitmap = BitmapFactory.decodeResource(getResources(), mScan_img_resource_id);
        scanBitmap = Bitmap.createScaledBitmap(scanLineBitmap, (int) mCircle_radius * 2, (int) mCircle_radius * 2, true);
        reset(false);
    }

    private void reset(boolean isInvalidate) {
        bitmapMatrix.reset();
        bitmapMatrix.setTranslate(circleCenterX, circleCenterY - mCircle_radius);
        bitmapMatrix.preScale(1.0f, -1.0f);
        if (isInvalidate) {
            invalidate();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        // 画白色背景背景和圆形遮罩
        drawCircleMask(canvas);
        // 画人脸附近的方框
        PointF pointF = mFaceBox.drawFaceBox(canvas);
        if (pointF != null) {
            // 获取人脸矩形中心点和扫描框中心点直线距离，小于或等于扫描框半径则证明最少有一半人脸在扫描框内，可以提示保持不动
            float distance = getDistance(circleCenterX, circleCenterY, pointF.x, pointF.y);
            if (distance <= mCircle_radius) {
                drawInnerHint(canvas);
            }
        }
        drawCenterText(canvas);
        if (scanBitmap != null) {
            canvas.drawBitmap(scanBitmap, bitmapMatrix, mRectPaint);
        }
    }

    /**
     * @param circleCenterX 扫描框中心点x轴座标
     * @param circleCenterY 扫描框中心点y轴座标
     * @param faceCenterX   人脸框中心点x轴座标
     * @param faceCenterY   人脸框中心点y轴座标
     * @return 两个中心点座标的直线距离
     */
    private float getDistance(float circleCenterX, float circleCenterY, float faceCenterX, float faceCenterY) {
        double dx = Math.pow(faceCenterX - circleCenterX, 2);
        double dy = Math.pow(faceCenterY - circleCenterY, 2);
        return (float) Math.sqrt(dx + dy);
    }

    private void drawCenterText(Canvas canvas) {
        mTextPaint.setColor(Color.parseColor("#000000"));
        mTextPaint.setTextSize(60);
        mTextPaint.setFakeBoldText(true);
        canvas.drawText("正在进行人脸识别", circleCenterX, circleCenterY + mCircle_radius + 150, mTextPaint);
    }

    private void drawInnerHint(Canvas canvas) {
        mTextPaint.setColor(Color.parseColor("#ffffff"));
        mTextPaint.setTextSize(50);
        canvas.drawText("请保持不动", circleCenterX, circleCenterY - (mCircle_radius / 2), mTextPaint);
    }


    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        Log.i(TAG, "onAttachedToWindow: ");
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        Log.i(TAG, "onDetachedFromWindow: ");
        stopAnimator();
    }
}
