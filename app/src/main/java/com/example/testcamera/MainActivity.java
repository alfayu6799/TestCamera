package com.example.testcamera;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.hardware.Camera;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.Toast;

import com.googlecode.tesseract.android.TessBaseAPI;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import static android.Manifest.permission.CAMERA;
import static android.Manifest.permission.WRITE_EXTERNAL_STORAGE;
import static android.os.Build.VERSION_CODES.M;


public class MainActivity extends AppCompatActivity implements SurfaceHolder.Callback, Camera.PictureCallback {

    private final String TAG = MainActivity.class.getSimpleName();  //debug

    private SurfaceView surfaceView;
    private SurfaceHolder surfaceHolder;
    private Camera camera;
    private Camera.Parameters parameters;

    private ProgressDialog mProgressDialog;

    //flag to detect flash is on or off
    private boolean isLighton = false;  //default is turn off
    private int mScreenWidth;
    private int mScreenHeight;
    private int defaultWidth = 1920;                 //預設預覽尺寸(default)
    private int defaultHeight = 1080;                //預設預覽尺寸(default)
    private float scaleX = (float) 2.26f;           //照片寬:2441 (800萬像素) (default)
    private float scaleY = (float) 1.7f;            //照片高:3264 (800萬像素) (default)

    public static final int REQUEST_CODE = 100;
    private String[] neededPermissions = new String[]{CAMERA, WRITE_EXTERNAL_STORAGE};

    private boolean isOcr = false;
    private String keyOcr;        //辨識用字元
    private int compression = 80; //中品質照片(default)
    private String pixel;         //相片大小
    private String fileNamepath;  //照片儲存路徑
    private String fileName;      //照片檔名

    //原圖的長寬
    private int height;
    private int width;
    private Bitmap bm;

    //Tess-two
    private TessBaseAPI mTess;
    private static final String DATAPATH = Environment.getExternalStorageDirectory().getAbsolutePath() + File.separator; //tessBaseAPI初始化第一個參數
    private static final String tessdata = DATAPATH + File.separator + "tessdata";
    private static final String DEFAULT_LANGUAGE = "eng";                                            //
    public static final  String DEFAULT_LANGUAGE_NAME = DEFAULT_LANGUAGE + ".traineddata";            //assets中的文件名稱
    public static final  String LANGUAGE_PATH = tessdata + File.separator + DEFAULT_LANGUAGE_NAME;    //保存到SD Card中的完整文件名稱

    //存檔路徑
    private String path = Environment.getExternalStorageDirectory().getPath() + "/DCIM/Camera/";  //default

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getSupportActionBar().hide(); //hide title
        setContentView(R.layout.activity_main);

        Bundle bundle = MainActivity.this.getIntent().getExtras();
        getData(bundle);//取得sudabcat參數

        //getScreenMetrix(MainActivity.this); //取得手機螢幕的長寬

        surfaceView = findViewById(R.id.surfaceView);
        if (surfaceView != null) {
            boolean result = checkPermission(); //權限check
            if (result) {
                copyToSD(LANGUAGE_PATH, DEFAULT_LANGUAGE_NAME); //將辨識用字庫copy入設備中
                setupSurfaceHolder();
            }
        }
    }

    //取得sudabcat參數
    private void getData(Bundle bundle) {
        if (bundle != null && bundle.containsKey("OCRKEYWORD")){
            pixel = bundle.getString("PIXEL");                           //照片大小
            keyOcr = bundle.getString("OCRKEYWORD");                     //辨識文字
            compression = Integer.parseInt(bundle.getString("QUALITY")); //照片品質
            fileNamepath = bundle.getString("PICPATH");                  //路徑

            //根據辨識字元有無啟動
            if (keyOcr.equals("")) {
                isOcr = false;
            }else {
                isOcr = true;
            }
            //照片大小
            if(pixel.equals("800")){ //800萬像素
                scaleX = (float) 2.26f;
                scaleY = (float) 1.7f;
            }else if (pixel.equals("500")){ //500萬像素
                scaleX = (float) 1.8f;
                scaleY = (float) 1.35f;
            }else if (pixel.equals("300")){ //300萬像素
                scaleX = (float) 1.42f;
                scaleY = (float) 1.06f;
            }else if (pixel.equals("200")){ //300萬像素
                scaleX = (float) 1.11f;
                scaleY = (float) 0.83f;
            }else if (pixel.equals("HD1080")){ //default
                scaleX = (float) 1.0f;
                scaleY = (float) 1.0f;
            }else if (pixel.equals("130")){ //130萬像素
                scaleX = (float) 0.88f;
                scaleY = (float) 0.66f;
            }

            //儲存照片路徑
            if (fileNamepath.equals("DCIM")){
                path = Environment.getExternalStorageDirectory().getPath() + "/DCIM/Camera/";
            }else if (fileNamepath.equals("AppName")){
                String temp = MainActivity.this.getFilesDir().getPath();
                Log.d(TAG, "getData AppName path : " + temp);
                File dir = new File(temp, "/sudabcat");
                if (!dir.exists()){
                    dir.mkdir();
                }
                path = temp + "/sudabcat/";
            }else if (fileNamepath.equals("Sudabcat")){
               String temp = Environment.getExternalStorageDirectory().getPath();
               File dir = new File(temp + "/sudabcat");
               if (!dir.exists()){
                   dir.mkdir();
               }
                path = temp + "/sudabcat/";
            }
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.d(TAG, "onStart: ");
    }

    //語言包copy到設備端 (assets的中文識別字庫檔創建到設備端,不然系統會crash)
    private void copyToSD(String language, String name) {
        Log.d(TAG, "copyToSD Fxn ");
        File f = new File(language);
        if (f.exists()){
            f.delete();
        }
        if (!f.exists()){
            File p = new File(f.getParent());
            if (!p.exists()){
                p.mkdirs();
            }
            try {
                f.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        InputStream is = null;
        OutputStream os = null;
        try {
            is = this.getAssets().open(name);
            File file = new File(language);
            os = new FileOutputStream(file);
            byte[] bytes = new byte[2048];
            int len = 0;
            while ((len = is.read(bytes)) != -1) {
                os.write(bytes, 0, len);
            }
            os.flush();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (is != null)
                    is.close();
                if (os != null)
                    os.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private boolean checkPermission() { //檢查權限
        int currentAPIVersion = Build.VERSION.SDK_INT;
        if (currentAPIVersion >= M) {
            ArrayList<String> permissionsNotGranted = new ArrayList<>();
            for (String permission : neededPermissions) {
                if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                    permissionsNotGranted.add(permission);
                }
            }
            if (permissionsNotGranted.size() > 0) {
                boolean shouldShowAlert = false;
                for (String permission : permissionsNotGranted) {
                    shouldShowAlert = ActivityCompat.shouldShowRequestPermissionRationale(this, permission);
                }
                if (shouldShowAlert) {
                    showPermissionAlert(permissionsNotGranted.toArray(new String[permissionsNotGranted.size()]));
                } else {
                    requestPermissions(permissionsNotGranted.toArray(new String[permissionsNotGranted.size()]));
                }
                return false;
            }
        }
        return true;
    }

    private void showPermissionAlert(final String[] permissions) {
        AlertDialog.Builder alertBuilder = new AlertDialog.Builder(this);
        alertBuilder.setCancelable(true);
        alertBuilder.setTitle(R.string.permission_required);
        alertBuilder.setMessage(R.string.permission_message);
        alertBuilder.setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                requestPermissions(permissions);
            }
        });
        AlertDialog alert = alertBuilder.create();
        alert.show();
    }

    private void requestPermissions(String[] permissions) {
        ActivityCompat.requestPermissions(MainActivity.this, permissions, REQUEST_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case REQUEST_CODE:
                for (int result : grantResults) {
                    if (result == PackageManager.PERMISSION_DENIED) {
                        Toast.makeText(MainActivity.this, R.string.permission_warning, Toast.LENGTH_LONG).show();
                        setViewVisibility(R.id.showPermissionMsg, View.VISIBLE);
                        return;
                    }
                }
                setupSurfaceHolder();
                copyToSD(LANGUAGE_PATH, DEFAULT_LANGUAGE_NAME); //將辨識用字庫copy入設備中
                break;
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    //自訂Visibility
    private void setViewVisibility(int id, int visibility) {
        View view = findViewById(id);
        if (view != null) {
            view.setVisibility(visibility);
        }
    }

    private void setupSurfaceHolder() {
        //顯示 : 拍照,預覽畫面,閃光,曝光 icon
        setViewVisibility(R.id.startBtn, View.VISIBLE);    //拍照
        setViewVisibility(R.id.surfaceView, View.VISIBLE); //預覽畫面
        setViewVisibility(R.id.flashBtn, View.VISIBLE);    //閃光
        setViewVisibility(R.id.exposueSkb, View.VISIBLE);  //曝光

        surfaceHolder = surfaceView.getHolder();
        surfaceHolder.addCallback(this);

        setBtnClick();       //拍照
        flashClick();        //閃光
        exposureClick();     //曝光
    }

    private void setBtnClick() { //拍照
        ImageView startBtn = findViewById(R.id.startBtn);
        if (startBtn != null) {
            startBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    captureImage();
                }
            });
        }
    }

    private void flashClick(){ //閃光
        final ImageView flashBtn = findViewById(R.id.flashBtn);
        flashBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(isLighton){
                    isLighton = false;
                    flashBtn.setImageResource(R.drawable.ic_flash_off_white_48dp);
                    offFlash();
                }else{
                    isLighton = true;
                    flashBtn.setImageResource(R.drawable.ic_flash_on_white_48dp);
                    openFlash();
                }
            }
        });
    }

    private void exposureClick() { //曝光
        SeekBar exposure = findViewById(R.id.exposueSkb);
        exposure.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int i = progress -2;
                exposure(i);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });
    }

    public void captureImage() {
        if (camera != null) {
            camera.autoFocus(new Camera.AutoFocusCallback() {
                @Override
                public void onAutoFocus(boolean success, Camera camera) {
                    if(success){ //對焦成功才可以拍照
                        camera.takePicture(shutterCallback, null, MainActivity.this);
                    }
                }
            });
//            camera.takePicture(null, null, this);
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder surfaceHolder) {
        Log.d(TAG, "surfaceCreated: ");
        startCamera(); //啟動相機
    }

    //啟動相機
    private void startCamera() {
        camera = Camera.open();
        parameters = camera.getParameters();  //得到相機的參數
        //6.0以上設備鏡頭的方判斷
        if (Build.VERSION.SDK_INT >= 23) {  //Android 6.0(API 23)
            setPar(); //設置照片參數
            camera.setDisplayOrientation(90);      //預覽鏡頭旋轉270度
        }else{
            camera.setDisplayOrientation(90);       //預覽鏡頭旋轉90度
        }
        try {
            camera.setPreviewDisplay(surfaceHolder);
            camera.startPreview();   //預覽
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    //設置照片參數
    private void setPar() {
        parameters.setPictureSize(defaultWidth, defaultHeight);  //照片分辨率
        camera.setParameters(parameters);                        //設置相機的參數
    }

    @Override
    public void surfaceChanged(SurfaceHolder surfaceHolder, int i, int i1, int i2) {
        Log.d(TAG, "surfaceChanged:");
        resetCamera();
    }

    public void resetCamera() {
        if (surfaceHolder.getSurface() == null) {
            // Return if preview surface does not exist
            return;
        }

        if (camera != null) {
            // Stop if preview surface is already running.
            camera.stopPreview();
            try {
                // Set preview display
                camera.setPreviewDisplay(surfaceHolder);
            } catch (IOException e) {
                e.printStackTrace();
            }
            // Start the camera preview...
            camera.startPreview();
        }
    }


    @Override
    public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
        releaseCamera();
    }

    private void releaseCamera() {
        if (camera != null) {
            camera.stopPreview();
            camera.release();
            camera = null;
        }
    }

    //創建jpeg圖片回調數據對象
    @Override
    public void onPictureTaken(byte[] bytes, Camera camera) {
        saveImage(bytes);  //照片存檔
        //resetCamera();
    }

    private void saveImage(byte[] bytes) {
        //存檔前先對照片做處理
        bm = BitmapFactory.decodeByteArray(bytes,0,bytes.length); //原圖
        Matrix matrix = new Matrix(); //矩陣(坐標映射)
        height = bm.getHeight();      //原圖的高
        width = bm.getWidth();       //原圖的寬

        //圖片儲存前旋轉
        if (Build.VERSION.SDK_INT >= 23) { //Android 6.0(API 23)
            matrix.setRotate(90);       //設定旋轉角度(少掉這個會無法辨識照片)
        }else {
            matrix.setRotate(90);       //設定旋轉角度(少掉這個會無法辨識照片)
        }

        //旋轉後的圖片重建
        Bitmap bitmap = Bitmap.createBitmap(bm, 0, 0, width, height, matrix, true);

        //時間戳
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        //照片檔名
        String sudabcatName = "SUD_" + timeStamp + ".jpg";

        fileName = path + sudabcatName; //照片檔名(含時間戳)

        try {
            FileOutputStream fout = new FileOutputStream(fileName);
            BufferedOutputStream bos = new BufferedOutputStream(fout);
            bitmap.compress(Bitmap.CompressFormat.JPEG, compression, bos);
            bos.flush();       //輸出
            bos.close();       //關閉
            fout.close();      //關閉
            bitmap.recycle(); // 回收bitmap
            Log.d(TAG, "onPictureTaken: saveClick : " + fileName);
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(MainActivity.this, "onPictureTaken: save photo is fail !!", Toast.LENGTH_SHORT).show();
        }
        setPic(fileName); //顯示拍照後的圖片
    }

    //顯示拍照後的圖片
    private void setPic(String fileName) {
        //顯示 : 拍照後的圖片,存檔,刪檔icon
        setViewVisibility(R.id.imageShow, View.VISIBLE);
        setViewVisibility(R.id.delBtn, View.VISIBLE);
        setViewVisibility(R.id.saveBtn, View.VISIBLE);
        //隱藏 : 拍照,閃光,曝光icon
        setViewVisibility(R.id.startBtn, View.GONE);
        setViewVisibility(R.id.flashBtn, View.GONE);
        setViewVisibility(R.id.exposueSkb, View.GONE);
        if(isOcr) {
            ocrClick(fileName);    //辨識
        }else{
            saveClick(fileName);   //存檔
        }
        delClick(fileName);    //刪檔
    }

    //存檔
    private void saveClick(final String fileName) {
        ImageView savePic = findViewById(R.id.saveBtn);
        savePic.setImageResource(R.drawable.ic_save_white_48dp);
        final Bitmap bitmap_after = setCustomBitmap(); //存檔前將照片放大or縮小
        savePic.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try { //回寫照片檔案
                    Log.d(TAG, "saveClick: " + fileName);
                    FileOutputStream fileOutputStream = new FileOutputStream(fileName);
                    BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(fileOutputStream);
                    bitmap_after.compress(Bitmap.CompressFormat.JPEG, compression, bufferedOutputStream); //壓縮圖片
                    bufferedOutputStream.flush();       //輸出
                    bufferedOutputStream.close();       //關閉
                    fileOutputStream.close();           //關閉
                    Toast.makeText(MainActivity.this, "Save pic is sucessed !!", Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    e.printStackTrace();
                }
                setupSurfaceHolder();
                setGone();
                resetCamera();
            }
        });
    }

    //存檔前將照片放大or縮小(取得sudabcat來的參數並設置)
    private Bitmap setCustomBitmap() {
        Matrix m = new Matrix();
        if (Build.VERSION.SDK_INT >= 23) { //Android 6.0(API 23)
            m.setRotate(90);       //設定旋轉角度(少掉這個會無法辨識照片)
        }else {
            m.setRotate(90);       //設定旋轉角度(少掉這個會無法辨識照片)
        }
        m.postScale(scaleX, scaleY); //長寬重製
        return Bitmap.createBitmap(bm, 0, 0, width, height, m, true);
    }

    //刪檔
    private void delClick(final String fileName) {
        ImageView delPic = findViewById(R.id.delBtn);
        delPic.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                delPic(fileName);    //刪檔(有bug)
                setupSurfaceHolder();
                setGone();
                resetCamera();
            }
        });
    }

    //辨識
    private void ocrClick(final String fileName) {
        ImageView savePic = findViewById(R.id.saveBtn);
        final Bitmap pic = BitmapFactory.decodeFile(fileName); //將手機中的圖片文件轉換成Bitmap
        bitmap2Gray(pic); //將圖片灰階化
        savePic.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d(TAG, "ocrClick: " + isOcr + " ocrkey : " + keyOcr);
                if(isOcr){
                    Toast.makeText(MainActivity.this, "Start OCR , Please Wait ....", Toast.LENGTH_SHORT).show();
                    isOcr = false;
                    doOcr(pic , fileName);
                }else {
                    Toast.makeText(MainActivity.this, "Save pic is sucessed !!", Toast.LENGTH_SHORT).show();
                    setupSurfaceHolder();
                    setGone();
                    resetCamera();
                }
            }
        });
    }

    //灰度化
    public Bitmap bitmap2Gray(Bitmap bmSrc){
        Log.d(TAG, "bitmap2Gray pic: " + bmSrc);
        //創建灰度圖片
        Bitmap bmpGray = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);
        //創建畫布
        Canvas c = new Canvas(bmpGray);
        Paint paint = new Paint();
        ColorMatrix cm = new ColorMatrix();
        cm.setSaturation(0); //設定顏色矩陣的飽和度 0:灰色 1:原圖
        ColorMatrixColorFilter f = new ColorMatrixColorFilter(cm); //顏色過濾器
        paint.setColorFilter(f);
        c.drawBitmap(bmSrc, 0, 0, paint); //畫圖

        //saveGray(bmpGray);
        return bmpGray;
    }

    private void saveGray(Bitmap bmpGray) {
        String path = Environment.getExternalStorageDirectory() + "/DCIM/Camera/"
                + System.currentTimeMillis() + ".jpg";
        try {
            FileOutputStream fout = new FileOutputStream(path);
            BufferedOutputStream bos = new BufferedOutputStream(fout);
            bmpGray.compress(Bitmap.CompressFormat.JPEG, 100, bos);
            bos.flush();
            bos.close();
            fout.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void doOcr(final Bitmap pic, final String fileName) {
        Log.d(TAG, "doOcr pic : " + pic);
        if (mProgressDialog == null) {
            mProgressDialog = ProgressDialog.show(this, "Processing",
                    "辨識文字: " + keyOcr , true);
        }
        else {
            mProgressDialog.show();
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                final String result = getOCRResult(pic);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Log.d(TAG, "keyword: " + keyOcr + " Result: " + result);
                        if(result.contains(keyOcr) || result.equalsIgnoreCase(keyOcr)){
                            Toast.makeText(MainActivity.this, getString(R.string.ocr_ok), Toast.LENGTH_SHORT).show();
                            setCustomOcrPic(fileName);  //存檔前將參數帶入
                            isOcr = true;
                            setupSurfaceHolder();
                            setGone();
                            resetCamera();
                        }else {
                            Toast.makeText(MainActivity.this, getString(R.string.ocr_fuzzy), Toast.LENGTH_SHORT).show();
                            isOcr = true;
                            delPic(fileName); //bug need to fix
                            setupSurfaceHolder();
                            setGone();
                            resetCamera();
                        }
                        mProgressDialog.dismiss();
                    }
                });
            }
        }).start();
    }

    //存檔前將參數帶入
    private void setCustomOcrPic(String fileName) {
        Matrix ma = new Matrix();
        if (Build.VERSION.SDK_INT >= 23) { //Android 6.0(API 23)
            ma.setRotate(270);       //設定旋轉角度(少掉這個會無法辨識照片)
        }else {
            ma.setRotate(90);       //設定旋轉角度(少掉這個會無法辨識照片)
        }
        ma.postScale(scaleX, scaleY); //來自sudabcat參數
        Bitmap bitOcr = Bitmap.createBitmap(bm, 0, 0, width, height, ma,true);
        try {
            FileOutputStream fileOutputStream = new FileOutputStream(fileName);
            BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(fileOutputStream);
            bitOcr.compress(Bitmap.CompressFormat.JPEG, compression, bufferedOutputStream); //壓縮圖片
            bufferedOutputStream.flush();       //輸出
            bufferedOutputStream.close();       //關閉
            fileOutputStream.close();           //關閉
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    //隱藏 : 拍照後的圖片顯示,存檔,刪檔icon
    private void setGone() {
        setViewVisibility(R.id.delBtn, View.GONE);
        setViewVisibility(R.id.saveBtn, View.GONE);
        setViewVisibility(R.id.imageShow, View.GONE);
    }

    //開啟閃光
    public  void openFlash(){
        parameters.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH); //turn on
        camera.setParameters(parameters);
    }

    //關閉閃光
    public void offFlash() {
        parameters.setFlashMode(Camera.Parameters.FLASH_MODE_OFF); //turn off
        camera.setParameters(parameters);
    }

    //曝光
    public int exposure(int exposure) {
        parameters.setExposureCompensation(exposure);
        camera.setParameters(parameters);
        return exposure;
    }

    //刪檔
    private void delPic(String fileName) {
        Log.d(TAG, "delPic: " + fileName);
        File file = new File(fileName);
        if (file.exists()){
            file.delete();
            Toast.makeText(MainActivity.this, "Delete Successed !!", Toast.LENGTH_SHORT).show();
        }
    }

    //辨識Fxn
    public String getOCRResult(Bitmap bitmap) {
        mTess = new TessBaseAPI();
        mTess.init(DATAPATH, DEFAULT_LANGUAGE);
        // 白名單
        mTess.setVariable(TessBaseAPI.VAR_CHAR_WHITELIST, "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-");
        // 黑名單
        mTess.setVariable(TessBaseAPI.VAR_CHAR_BLACKLIST, "!@#$%^&*()_+=[]}{;:'\"\\|~`,./<>?");
        mTess.setPageSegMode(TessBaseAPI.PageSegMode.PSM_AUTO_OSD);

        mTess.setImage(bitmap);                //需要辨識的圖片
        String result = mTess.getUTF8Text();  //根據Init的語言，獲得ocr後的字符串
        mTess.end();
        return result;
    }

    //取得手機螢幕大小
    private void getScreenMetrix(Context context) {
        WindowManager WM = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        DisplayMetrics outMetrics = new DisplayMetrics();
        WM.getDefaultDisplay().getMetrics(outMetrics);
        mScreenWidth = outMetrics.widthPixels;
        mScreenHeight = outMetrics.heightPixels;
        Log.d(TAG, "getScreen Screen.width=" + mScreenWidth + " Screen.height=" + mScreenHeight);
    }

    //拍照時會出現"喀擦"聲
    Camera.ShutterCallback shutterCallback = new Camera.ShutterCallback(){
        @Override
        public void onShutter() {
        }
    };

    @Override
    protected void onDestroy() {
        super.onDestroy();
        isOcr = false;
    }
}
