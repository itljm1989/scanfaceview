package com.ljm.scanfaceview;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;

import java.util.ArrayList;
import java.util.List;

/**
 * @author: ljm
 * 创建日期:2022/8/9
 */
public class FaceBox {
    private static final String TAG = "FaceBox";
    /**
     * 直角边长度
     */
    public static final int CORNERS_LENGTH = 20;

    private Path leftTopPath;
    private Path rightTopPath;
    private Path leftBottomPath;
    private Path rightBottomPath;
    private Paint mLinePaint;
    private List<RectF> mFaces;

    public FaceBox() {
        initPath();
        initLinePaint();
    }

    private void initPath() {
        leftTopPath = new Path();
        rightTopPath = new Path();
        leftBottomPath = new Path();
        rightBottomPath = new Path();
    }

    private void initLinePaint() {
        mLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mLinePaint.setDither(true);
        mLinePaint.setColor(Color.parseColor("#fa922c"));
        mLinePaint.setStrokeWidth(5);
        mLinePaint.setStyle(Paint.Style.STROKE);
        mLinePaint.setStrokeJoin(Paint.Join.ROUND);
        mLinePaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OVER));
    }

    public void setFaces(List<RectF> faces) {
        this.mFaces = faces;
    }


    public PointF drawFaceBox(Canvas canvas) {
        PointF point = null;
        if (mFaces != null && mFaces.size() > 0) {
            int length = mFaces.size();
            for (int i = 0; i < length; i++) {
                RectF rectF = mFaces.get(i);
                float realLeft = rectF.left;
                float realTop = rectF.top;
                float realRight = rectF.right;
                float realBottom = rectF.bottom;

                leftTopPath.moveTo(realLeft + CORNERS_LENGTH, realTop);
                leftTopPath.lineTo(realLeft, realTop);
                leftTopPath.lineTo(realLeft, realTop + CORNERS_LENGTH);
                canvas.drawPath(leftTopPath, mLinePaint);
                leftTopPath.reset();


                rightTopPath.moveTo(realRight - CORNERS_LENGTH, realTop);
                rightTopPath.lineTo(realRight, realTop);
                rightTopPath.lineTo(realRight, realTop + CORNERS_LENGTH);
                canvas.drawPath(rightTopPath, mLinePaint);
                rightTopPath.reset();

                leftBottomPath.moveTo(realLeft + CORNERS_LENGTH, realBottom);
                leftBottomPath.lineTo(realLeft, realBottom);
                leftBottomPath.lineTo(realLeft, realBottom - CORNERS_LENGTH);
                canvas.drawPath(leftBottomPath, mLinePaint);
                leftBottomPath.reset();


                rightBottomPath.moveTo(realRight - CORNERS_LENGTH, realBottom);
                rightBottomPath.lineTo(realRight, realBottom);
                rightBottomPath.lineTo(realRight, realBottom - CORNERS_LENGTH);
                canvas.drawPath(rightBottomPath, mLinePaint);
                rightBottomPath.reset();
                point = new PointF(realLeft + ((realRight - realLeft) / 2.0f), realTop + ((realBottom - realTop) / 2.0f));
            }
        }
        return point;
    }
}
