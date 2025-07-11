package org.libreoffice.ui;

import org.libreoffice.R;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;

public class PageView extends View{
    private Bitmap bmp;
    private Paint mPaintBlack;

    public PageView(Context context ) {
        super(context);
        bmp = BitmapFactory.decodeResource(getResources(), R.drawable.dummy_page);
        initialise();
    }
    public PageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        bmp = BitmapFactory.decodeResource(getResources(), R.drawable.dummy_page);
        initialise();
    }
    public PageView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        bmp = BitmapFactory.decodeResource(getResources(), R.drawable.dummy_page);//load a "page"
        initialise();
    }

    private void initialise(){
        mPaintBlack = new Paint();
        mPaintBlack.setARGB(255, 0, 0, 0);
    }

    public void setBitmap(Bitmap bmp){
        this.bmp = bmp;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if( bmp != null ){
            int horizontalMargin = (int) (getWidth()*0.1);
            canvas.drawBitmap(bmp,
                    new Rect(0, 0, bmp.getWidth(), bmp.getHeight()),
                    new Rect(horizontalMargin, horizontalMargin,canvas.getWidth()-horizontalMargin, canvas.getHeight()- horizontalMargin), mPaintBlack);
        } else {
            canvas.drawText(getContext().getString(R.string.bmp_null), 100, 100, new Paint());
        }
    }

}


