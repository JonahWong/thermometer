package com.mindtherobot.samples.thermometer;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LightingColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

import java.util.List;

public final class Thermometer extends View implements SensorEventListener {

	private static final String TAG = Thermometer.class.getSimpleName();
	
	private Handler handler;

	// drawing tools
	private RectF rimRect;
	private Paint rimPaint;
	private Paint rimCirclePaint;
	
	private RectF faceRect;
	private Bitmap faceTextureBitmap;
	private Paint facePaint;
	private Paint rimShadowPaint;
	
	private Paint scalePaint;
    /**
     * 刻度那个圈所在的区域
     */
	private RectF scaleRect;

	private Paint titlePaint;
	private Path titlePath;

	private Paint logoPaint;
	private Bitmap logoBitmap;
    /**
     * Logo scale = 0.3 in this example
     */
	private Matrix logoMatrix;
	private float logoScale;
	
	private Paint handPaint;
	private Path handPath;
	private Paint handScrewPaint;
	
	private Paint backgroundPaint; 
	// end drawing tools
	
	private Bitmap background; // holds the cached static part
	
	// scale configuration
	private static final int totalNicks = 100;
	private static final float degreesPerNick = 360.0f / totalNicks;
    /**
     * 12点方向上显示的温度数
     * 注意，不是12点方向就是90°，显示的是温度值，不是角度值
     */
	private static final int centerDegree = 40; // the one in the top center (12 o'clock)
    /**
     * 左侧显示的最小温度数
     * 刻度上显示的是温度值，不是角度值
     */
    private static final int minDegrees = -30;
    /**
     * 右侧显示的最大温度数
     * 刻度上显示的是温度值，不是角度值
     */
	private static final int maxDegrees = 110;
	
	// hand dynamics -- all are angular expressed in F degrees
	private boolean handInitialized = false;
	private float handPosition = minDegrees;
	private float handTarget = centerDegree;
	private float handVelocity = 0.0f;
	private float handAcceleration = 0.0f;
	private long lastHandMoveTime = -1L;
	
	
	public Thermometer(Context context) {
		super(context);
		init();
	}

	public Thermometer(Context context, AttributeSet attrs) {
		super(context, attrs);
		init();
	}

	public Thermometer(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		init();
	}
	
	@Override
	protected void onAttachedToWindow() {
		super.onAttachedToWindow();
		attachToSensor();
	}

	@Override
	protected void onDetachedFromWindow() {
		detachFromSensor();
		super.onDetachedFromWindow();
	}
	
	@Override
	protected void onRestoreInstanceState(Parcelable state) {
		Bundle bundle = (Bundle) state;
		Parcelable superState = bundle.getParcelable("superState");
		super.onRestoreInstanceState(superState);
		
		handInitialized = bundle.getBoolean("handInitialized");
		handPosition = bundle.getFloat("handPosition");
		handTarget = bundle.getFloat("handTarget");
		handVelocity = bundle.getFloat("handVelocity");
		handAcceleration = bundle.getFloat("handAcceleration");
		lastHandMoveTime = bundle.getLong("lastHandMoveTime");
	}

	@Override
	protected Parcelable onSaveInstanceState() {
		Parcelable superState = super.onSaveInstanceState();
		
		Bundle state = new Bundle();
		state.putParcelable("superState", superState);
		state.putBoolean("handInitialized", handInitialized);
		state.putFloat("handPosition", handPosition);
		state.putFloat("handTarget", handTarget);
		state.putFloat("handVelocity", handVelocity);
		state.putFloat("handAcceleration", handAcceleration);
		state.putLong("lastHandMoveTime", lastHandMoveTime);
		return state;
	}

	private void init() {
		handler = new Handler();
		
		initDrawingTools();
	}

	private String getTitle() {
		return "mindtherobot.com";
	}

	private SensorManager getSensorManager() {
		return (SensorManager) getContext().getSystemService(Context.SENSOR_SERVICE);		
	}
	
	private void attachToSensor() {
		SensorManager sensorManager = getSensorManager();
		
		List<Sensor> sensors = sensorManager.getSensorList(Sensor.TYPE_TEMPERATURE);
		if (sensors.size() > 0) {
			Sensor sensor = sensors.get(0);
			sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_FASTEST, handler);
		} else {
			Log.e(TAG, "No temperature sensor found");
		}		
	}
	
	private void detachFromSensor() {
		SensorManager sensorManager = getSensorManager();
		sensorManager.unregisterListener(this);
	}

	private void initDrawingTools() {

        /** 控件绘制包含两个步骤
         * 指定画笔 - 绘制效果 Paint
         * 指定区间（位置） - 绘制位置 RectF
         * 也就是“在什么位置绘制什么效果”的问题
         *
         * */

		rimRect = new RectF(0.1f, 0.1f, 0.9f, 0.9f);

		/** the linear gradient is a bit skewed for realism
         * the bezel paint
         * rimPaint 画最外层的bezel，颜色渐变是从左上（偏上）到右下（偏下）渐变（0.4，0） - （0.6， 1）
         *
         * */
		rimPaint = new Paint();
		rimPaint.setFlags(Paint.ANTI_ALIAS_FLAG);

		rimPaint.setShader(new LinearGradient(0.40f, 0.0f, 0.60f, 1.0f,
										   Color.rgb(0xf0, 0xf5, 0xf0),
										   Color.rgb(0x30, 0x31, 0x30),
										   Shader.TileMode.CLAMP));
        /** rimCirclePaint 为了适应不同的页面背景，需要在bezel外面画一圈轮廓暗圈
         *
         * */
		rimCirclePaint = new Paint();
		rimCirclePaint.setAntiAlias(true);
		rimCirclePaint.setStyle(Paint.Style.STROKE);
		rimCirclePaint.setColor(Color.argb(0x4f, 0x33, 0x36, 0x33));
		rimCirclePaint.setStrokeWidth(0.005f);

		float rimSize = 0.02f;//bezel width
		faceRect = new RectF();//faceRect is what resides within the bezel.
        /**
         * faceRect 范围是在rimRect范围的基础上四个边均向内缩进rimSize大小的区域
         * Actually，正是因为这个缩进才让rim区域有了rim环的样式（rim本身是一个有填充的圆形区域）
         */
		faceRect.set(rimRect.left + rimSize, rimRect.top + rimSize,
			     rimRect.right - rimSize, rimRect.bottom - rimSize);		
        //
		faceTextureBitmap = BitmapFactory.decodeResource(getContext().getResources(),
				   R.drawable.plastic);
		BitmapShader paperBitmapShader = new BitmapShader(faceTextureBitmap,
												    Shader.TileMode.MIRROR, 
												    Shader.TileMode.MIRROR);
		facePaint = new Paint();  //used to paint the white area within the bezel.
        /** 下面这个是画笔优化策略*/
		facePaint.setFilterBitmap(true);

        /** 类似设置画笔笔触大小*/
        Matrix paperMatrix = new Matrix();
		paperMatrix.setScale(1.0f / faceTextureBitmap.getWidth(), 1.0f / faceTextureBitmap.getHeight());
		paperBitmapShader.setLocalMatrix(paperMatrix);

        /** 设置画图方式为填充内容，此处也就是Bitmap图片内容作为填充内容，而不是作为边线的背景*/
        facePaint.setStyle(Paint.Style.FILL);
        /** 设置画笔的着色器，也就是画笔画一下其实就是输入Bitmap到画布上，
         * 用Shader有点批处理的意味，一次不是画一个点、一条线，而是一次整幅画都出来了
         *
         * */
		facePaint.setShader(paperBitmapShader);

        /** bezel 以内的那一圈半透明效果的阴影*/
		rimShadowPaint = new Paint();
        /** 阴影效果的具体设置
         *  使用了一个辐射渐变的着色器，设置如下：
         *  辐射渐变的中心辐射点，即圆点。辐射半径，圆点和半径指定了辐射渐变的作用区域
         *  然后接下来的两个参数int[] float[]相互对应，表示以上区域内在float[]表示的区间内响应的应用int[]里的颜色
         *  它们是一一对应的
         *  当然float[]也可以为null，此时int[]里颜色会在圆心与区域边界之间均匀分布
         *  最后一个参数是着色模式
         *  TODO the parameters of RadialGradient
         * */
		rimShadowPaint.setShader(new RadialGradient(0.5f, 0.5f, faceRect.width() / 2.0f,
				   new int[] { 0x00000000, 0x00000500, 0x50000500 },
				   new float[] { 0.96f, 0.96f, 0.99f },
				   Shader.TileMode.MIRROR));
		rimShadowPaint.setStyle(Paint.Style.FILL);

        /**
         * 画表盘刻度的画笔
         * 特别注意下它使用了setTextScaleX方法来表示文字有收紧效果，就是变得纤细了。高度方面无影响
         * 刻度粗细与rimCirclePaint画笔一样
         * */
		scalePaint = new Paint();
		scalePaint.setStyle(Paint.Style.STROKE);
		scalePaint.setColor(0x9f004d0f);
		scalePaint.setStrokeWidth(0.005f);
		scalePaint.setAntiAlias(true);
		
		scalePaint.setTextSize(0.045f);
		scalePaint.setTypeface(Typeface.SANS_SERIF);
		scalePaint.setTextScaleX(0.8f);
		scalePaint.setTextAlign(Paint.Align.CENTER);		

        /**
         * 设置刻度占用的空间为0.1f的高度的圆环
         * 也就是说刻度之内的圆环半径仅剩0.3了
         * 指定刻度大小是为了指定刻度的区域RectF
         * 此处的区域其实是覆盖了刻度以内区域的
         * 只是说刻度以内的其他元素比如logo等绘制后会在刻度区域内进行
         *
         * 其实就是外层的区间包含了内层的区间
         * 内层的区间是在相邻外层的范围内绘制的
         * */
		float scalePosition = 0.10f;
		scaleRect = new RectF();
		scaleRect.set(faceRect.left + scalePosition, faceRect.top + scalePosition,
					  faceRect.right - scalePosition, faceRect.bottom - scalePosition);

		titlePaint = new Paint();
		titlePaint.setColor(0xaf946109);
		titlePaint.setAntiAlias(true);
		titlePaint.setTypeface(Typeface.DEFAULT_BOLD);
		titlePaint.setTextAlign(Paint.Align.CENTER);
		titlePaint.setTextSize(0.05f);
		titlePaint.setTextScaleX(0.8f);

		titlePath = new Path();
        /**
         * 分析一下下面的RectF的四个值是如何得到的
         * rimRect是（0.1， 0.1， 0.9， 0.9）分别对应左上右下
         * rimSize是0.02
         * 关于下面的数字是如何得出的可以看下R.drawable.gauge_view_implementation_process_size_included效果图
         * 这个坐标其实就是蓝色矩形的坐标。
         * 弧线路径就是基于该蓝色矩形的
         */
		titlePath.addArc(new RectF(0.24f, 0.24f, 0.76f, 0.76f), -180.0f, -180.0f);

		logoPaint = new Paint();
		logoPaint.setFilterBitmap(true);
		logoBitmap = BitmapFactory.decodeResource(getContext().getResources(), R.drawable.logo);
		logoMatrix = new Matrix();
		logoScale = (1.0f / logoBitmap.getWidth()) * 0.3f;
		logoMatrix.setScale(logoScale, logoScale);

		handPaint = new Paint();
		handPaint.setAntiAlias(true);
		handPaint.setColor(0xff392f2c);
        /**
         * 第一个参数模糊半径，第二三个参数是shadow与其附着物的相对偏移距离，可正可负
         * 最后一个参数是模糊颜色
         * Shadow附着在主体之上才有意义，所以中间两个参数就是描述这种相对关系
         */
		handPaint.setShadowLayer(0.01f, -0.005f, -0.005f, 0x7f000000);
		handPaint.setStyle(Paint.Style.FILL);	

        /** 注意下面的路径的最终效果是指针垂直向下，类似时钟六点的效果。细指针在上，粗指针在下
         *  效果图 {@value com.mindtherobot.samples.thermometer.R.drawable.needle_path_effect}
         * */
		handPath = new Path();
        /** P-0 - 指针粗头的顶点，其中0.5f表示中间位置，0.2f表示中点一下的offset，其中表盘face刻度以内的圆的半径大约0.3*/
		handPath.moveTo(0.5f, 0.5f + 0.2f);
        /** P-1  - 稍微向左上偏移一点，形成粗头尖角效果的左侧部分*/
        handPath.lineTo(0.5f - 0.010f, 0.5f + 0.2f - 0.007f);
        /** P-2  - 路径从下半部分画到指针细头左侧部分*/
        handPath.lineTo(0.5f - 0.002f, 0.5f - 0.32f);
        /** P+2  - 指针细头左侧部分到细头右侧部分，很短的offset-x，否则细头就变成粗头了哦。注意此处y值保持不变。与p-2过程相对应*/
        handPath.lineTo(0.5f + 0.002f, 0.5f - 0.32f);
        /** P+1  - 指针再次从细头画到粗头，形成粗头尖角的右侧部分。与p-1过程相对应*/
		handPath.lineTo(0.5f + 0.010f, 0.5f + 0.2f - 0.007f);
        /** p+0  - 指针路径重新回到指针最初开始的地方，到此已经形成闭合路径。与p-0过程相对应*/
		handPath.lineTo(0.5f, 0.5f + 0.2f);
        /** 上面就已经形成闭合路径了，下面是为了在表盘face中间位置画一个同颜色的圆环，包括上面的闭合路径+下面的圆环都是实色填充*/
		handPath.addCircle(0.5f, 0.5f, 0.025f, Path.Direction.CW);

        /** 下面的画笔工具是为了在表盘face中间的圆环的中间部分画不同颜色的圆环，配合上一步画的圆环，以达到3D的screw效果*/
		handScrewPaint = new Paint();
		handScrewPaint.setAntiAlias(true);
		handScrewPaint.setColor(0xff493f3c);
		handScrewPaint.setStyle(Paint.Style.FILL);
		
		backgroundPaint = new Paint();
		backgroundPaint.setFilterBitmap(true);
	}
	
	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		Log.d(TAG, "Width spec: " + MeasureSpec.toString(widthMeasureSpec));
		Log.d(TAG, "Height spec: " + MeasureSpec.toString(heightMeasureSpec));
		
		int widthMode = MeasureSpec.getMode(widthMeasureSpec);
		int widthSize = MeasureSpec.getSize(widthMeasureSpec);
		
		int heightMode = MeasureSpec.getMode(heightMeasureSpec);
		int heightSize = MeasureSpec.getSize(heightMeasureSpec);
		
		int chosenWidth = chooseDimension(widthMode, widthSize);
		int chosenHeight = chooseDimension(heightMode, heightSize);
		
		int chosenDimension = Math.min(chosenWidth, chosenHeight);
		
		setMeasuredDimension(chosenDimension, chosenDimension);
	}
	
	private int chooseDimension(int mode, int size) {
		if (mode == MeasureSpec.AT_MOST || mode == MeasureSpec.EXACTLY) {
			return size;
		} else { // (mode == MeasureSpec.UNSPECIFIED)
			return getPreferredSize();
		} 
	}
	
	// in case there is no size specified
	private int getPreferredSize() {
		return 300;
	}

    /** 在canvas上画出Bezel
     * 包括灰色边，还有在黑色主题下不易看到的暗色细边——通过rimCirclePaint来画
     *
     * */
	private void drawRim(Canvas canvas) {
		// first, draw the metallic body
        /**
         * 注意虽说是画rim，但是实际上画的是包括其内部填充的
         * 是通过线性着色器画的
         * 只是说因为在其范围内继续画其他的组件，所以就产生了rim的效果了
         * */
		canvas.drawOval(rimRect, rimPaint);
		// now the outer rim circle
        /**
         * 画rim的外层暗圈
         * 注意：首先应该知道画的什么是由画笔决定的
         * 包括是画的填充界面还是还是画的只是一个stroke线条。
         * 比如下面的rimCirclePaint就是画的stroke线条
         * 线条位置是根据rimRect来决定的
         * 线条形状是根据drawOval方法决定了是椭圆线条。
         *
         * 所以，画笔最终要画的就是矩形范围内的椭圆曲线，此处矩形为正方形。
         * 所以，就是画一个圆圈啦。
         *
         * */
		canvas.drawOval(rimRect, rimCirclePaint);
	}

    /**
     * 画表盘：纹理
     *
     * */
	private void drawFace(Canvas canvas) {
        /**
         * faceRect 范围是在rimRect范围的基础上四个边均向内缩进rimSize大小的区域
         * Actually，正是因为这个缩进才让rim区域有了rim环的样式（rim本身是一个有填充的圆形区域）
         */
		canvas.drawOval(faceRect, facePaint);
		// draw the inner rim circle
        /**
         * rim之内的暗圈是画在faceRect范围之内的，而不是rimRect之内。
         * 不过按理说该画到rimRect范围内
         * update:考虑到一个矩形区域内，如果不指定其他区域的话就只能画一个对应的圆圈。
         * 所以，在之前drawRim方法中已经画了一个外圈了，内圈就只能通过rim矩形范围内的其他矩形范围来画了。
         * 就是下面的drawOval方法啦
         * rimCirclePaint 画笔的笔触大小0.005（其中表盘环形标题离表盘的距离0.02，so 你可以知道0.005大概的大小）
         */
		canvas.drawOval(faceRect, rimCirclePaint);

		/** draw the rim shadow inside the face
         *  rimShadowPaint画笔中用的RadialGradient的作用范围是faceRect区域中的 new float[] { 0.96f, 0.96f, 0.99f }
         * */
		canvas.drawOval(faceRect, rimShadowPaint);
	}

    /**
     * 画刻度
     * @param canvas
     */
	private void drawScale(Canvas canvas) {
		canvas.drawOval(scaleRect, scalePaint);

        /**
         * restore the current matrix when restore() is called
         */
		canvas.save(Canvas.MATRIX_SAVE_FLAG);
        /**
         * for循环画出所有100个刻度
         */
		for (int i = 0; i < totalNicks; ++i) {
            /**
             * 刻度所在圈的上位置
             */
			float y1 = scaleRect.top;
            /**
             * 刻度的上位置
             * 0.020f是刻度上位置与刻度圆圈的距离
             */
			float y2 = y1 - 0.020f;
            /**
             * 画最上面（北极星方向）的竖直的那条刻度短线
             *
             * startX The x-coordinate of the start point of the line
             * startY The y-coordinate of the start point of the line
             * paint  The paint used to draw the line
             */
			canvas.drawLine(0.5f, y1, 0.5f, y2, scalePaint);

            /**
             * i 表示第i个刻痕口
             * 一共有100个刻痕口，也就是整个表盘刻度被分成了100份
             * 下面的%5中的5指的是5个格显示一次温度（刻度值）
             */
			if (i % 5 == 0) {
				int value = nickToDegree(i);
				
				if (value >= minDegrees && value <= maxDegrees) {
					String valueString = Integer.toString(value);
                    /**
                     * 0.015f是刻度与刻度值的距离
                     */
					canvas.drawText(valueString, 0.5f, y2 - 0.015f, scalePaint);
				}
			}
			
			canvas.rotate(degreesPerNick, 0.5f, 0.5f);
		}
		canvas.restore();		
	}

    /**
     *
     * @param nick 刻度格子数 0 - 100 个格子
     * @return 格子对应的刻度数 刻度上显示的温度的度数 也就是返回的某个位置对应的温度数
     */
	private int nickToDegree(int nick) {
        /**
         * 从12点方向开始算，nick=0
         * 6点方向，nick = totalNicks/2 = 50
         * 设置12点到6点之间的是正值，即0°到49°
         * 而从6点到12点之间的是负值，即零下的度数。零下50°到零下1°
         * 但考虑到40°（华氏）算是比较中间的温度值，所以中间的度数设置为40°
         * 所以在上面两个温度值范围基础之上再加40，即中间值 centerDegree
         * 同时要考虑实际情况，设置一个最小温度值零下30°，一个最大温度值110°
         */
		int rawDegree = ((nick < totalNicks / 2) ? nick : (nick - totalNicks)) * 2;
		int shiftedDegree = rawDegree + centerDegree;
		return shiftedDegree;
	}

    /**
     * 温度值转角度值（几何概念）
     *
     * @param degree
     * @return
     */
	private float degreeToAngle(float degree) {
        /**
         * 温度值-中间温度值，然后除以2，表示他们之间有几个刻度格子
         * 然后与每个格子代表的角度值（几何概念）相乘得到真实的角度值，即
         * 12点方向角度值是0.0f，6点方向角度值是90.0f 因为整个圆形表盘的角度值一共是360.0f
         */
		return (degree - centerDegree) / 2.0f * degreesPerNick;
	}
	
	private void drawTitle(Canvas canvas) {
		String title = getTitle();
		canvas.drawTextOnPath(title, titlePath, 0.0f,0.0f, titlePaint);				
	}
	
	private void drawLogo(Canvas canvas) {
		canvas.save(Canvas.MATRIX_SAVE_FLAG);
		canvas.translate(0.5f - logoBitmap.getWidth() * logoScale / 2.0f,
						 0.5f - logoBitmap.getHeight() * logoScale / 2.0f);

		int color = 0x00000000;
        /**
         * position range is 0 to 1(rightside to the center) and -1 to 0(leftside to the center)
         */
		float position = getRelativeTemperaturePosition();
		if (position < 0) {
			color |= (int) ((0xf0) * -position); // blue
		} else {
			color |= ((int) ((0xf0) * position)) << 16; // red			
		}
		//Log.d(TAG, "*** " + Integer.toHexString(color));
        /**
         * color gradient filter to set to Paint object.
         * Changing color of the logo according to temprature.
         */
		LightingColorFilter logoFilter = new LightingColorFilter(0xff338822, color);
		logoPaint.setColorFilter(logoFilter);

        /**
         * logoMatrix specify the bitmap is scaled to 0.3 of its initial self.
         * Specify the size(second parameter) and color vary(third parameter)
         */
		canvas.drawBitmap(logoBitmap, logoMatrix, logoPaint);
		canvas.restore();		
	}

    /**
     * By default,there's no hand showing to the user.Only if the hand is initialized,it shows.
     * Draw hand according to handPosition(initial position),not the targetPosition,which is used in the moveHand method.
     * @param canvas
     */
	private void drawHand(Canvas canvas) {
		if (handInitialized) {
            /**
             * handAngle is relative to the 12 o'clock.
             * handAngle is 0 when it's 12 o'clock.
             * handPosition is the value of temprature.
             * That is to say,how we can get angle from temprature value,we can do this through degreeToAngle() method.
             */
			float handAngle = degreeToAngle(handPosition);
			canvas.save(Canvas.MATRIX_SAVE_FLAG);
			canvas.rotate(handAngle, 0.5f, 0.5f);
			canvas.drawPath(handPath, handPaint);
			canvas.restore();
            /**
             * Draw the grey hand screw dot in the middle.
             */
			canvas.drawCircle(0.5f, 0.5f, 0.01f, handScrewPaint);
		}
	}

	private void drawBackground(Canvas canvas) {
		if (background == null) {
			Log.w(TAG, "Background not created");
		} else {
			canvas.drawBitmap(background, 0, 0, backgroundPaint);
		}
	}
	
	@Override
	protected void onDraw(Canvas canvas) {
		drawBackground(canvas);

		float scale = (float) getWidth();		
		canvas.save(Canvas.MATRIX_SAVE_FLAG);
		canvas.scale(scale, scale);

		drawLogo(canvas);
		drawHand(canvas);
		
		canvas.restore();
	
		if (handNeedsToMove()) {
			moveHand();
		}
	}

	@Override
	protected void onSizeChanged(int w, int h, int oldw, int oldh) {
		Log.d(TAG, "Size changed to " + w + "x" + h);
		
		regenerateBackground();
	}

    /** 将不变的界面元素都画到一个Bitmap背景中，以内存换性能*/
	private void regenerateBackground() {
		// free the old bitmap
		if (background != null) {
			background.recycle();
		}
		
		background = Bitmap.createBitmap(getWidth(), getHeight(), Bitmap.Config.ARGB_8888);
        /**
         * construct a canvas with the specified bitmap to draw into. The bitmap must be mutable.
         */
		Canvas backgroundCanvas = new Canvas(background);
		float scale = (float) getWidth();		
		backgroundCanvas.scale(scale, scale);
		
		drawRim(backgroundCanvas);
		drawFace(backgroundCanvas);
		drawScale(backgroundCanvas);
		drawTitle(backgroundCanvas);		
	}

	private boolean handNeedsToMove() {
		return Math.abs(handPosition - handTarget) > 0.01f;
	}

    /**
     * called in the onDraw method
     */
	private void moveHand() {
        /**
         * temprature changes so little that we can just ignore it.
         */
		if (! handNeedsToMove()) {
			return;
		}
		
		if (lastHandMoveTime != -1L) {
			long currentTime = System.currentTimeMillis();
            /**
             * delta is second Type.
             */
			float delta = (currentTime - lastHandMoveTime) / 1000.0f;

            /**
             * often used in Comparable interface,if > 0 return 1.0 if < 0 ,return -1.0;if 0 return 0;
             */
			float direction = Math.signum(handVelocity);
			if (Math.abs(handVelocity) < 90.0f) {
				handAcceleration = 5.0f * (handTarget - handPosition);
			} else {
				handAcceleration = 0.0f;
			}
			handPosition += handVelocity * delta;
			handVelocity += handAcceleration * delta;
            /**
             * the handPosition will more and more approach to the handTarget,and when their distance is within 0.01f,then we should directly set
             * the handPosition to handTarget. and set related params to their initial state and stop moving.
             */
			if ((handTarget - handPosition) * direction < 0.01f * direction) {
				handPosition = handTarget;
				handVelocity = 0.0f;
				handAcceleration = 0.0f;
				lastHandMoveTime = -1L;
			} else {
				lastHandMoveTime = System.currentTimeMillis();				
			}
			invalidate();
		} else {
			lastHandMoveTime = System.currentTimeMillis();
			moveHand();
		}
	}
	
	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy) {
		
	}

	@Override
	public void onSensorChanged(SensorEvent sensorEvent) {
		if (sensorEvent.values.length > 0) {
			float temperatureC = sensorEvent.values[0];
			//Log.i(TAG, "*** Temperature: " + temperatureC);
			
			float temperatureF = (9.0f / 5.0f) * temperatureC + 32.0f;
			setHandTarget(temperatureF);
		} else {
			Log.w(TAG, "Empty sensor event received");
		}
	}

    /**
     * get the value to change the color of the logo.
     * This is responding to temperature changing
     * @return
     */
	private float getRelativeTemperaturePosition() {
		if (handPosition < centerDegree) {
			return - (centerDegree - handPosition) / (float) (centerDegree - minDegrees);
		} else {
			return (handPosition - centerDegree) / (float) (maxDegrees - centerDegree);
		}
	}
	
	private void setHandTarget(float temperature) {
		if (temperature < minDegrees) {
			temperature = minDegrees;
		} else if (temperature > maxDegrees) {
			temperature = maxDegrees;
		}
		handTarget = temperature;
		handInitialized = true;
        /**
         * Internally,it will call onDraw method
         */
		invalidate();
	}
}
