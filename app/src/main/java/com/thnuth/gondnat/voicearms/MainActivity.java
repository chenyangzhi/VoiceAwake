package com.thnuth.gondnat.voicearms;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.Toast;

import com.baidu.speech.asr.SpeechConstant;
import com.thnuth.gondnat.voicearms.control.MyRecognizer;
import com.thnuth.gondnat.voicearms.control.MyWakeup;
import com.thnuth.gondnat.voicearms.recognization.CommonRecogParams;
import com.thnuth.gondnat.voicearms.recognization.MessageStatusRecogListener;
import com.thnuth.gondnat.voicearms.recognization.PidBuilder;
import com.thnuth.gondnat.voicearms.recognization.StatusRecogListener;
import com.thnuth.gondnat.voicearms.recognization.online.OnlineRecogParams;
import com.thnuth.gondnat.voicearms.wakeup.IWakeupListener;
import com.thnuth.gondnat.voicearms.wakeup.RecogWakeupListener;
import com.thnuth.gondnat.voicearms.wakeup.WakeupParams;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

import static com.thnuth.gondnat.voicearms.recognization.IStatus.STATUS_FINISHED;
import static com.thnuth.gondnat.voicearms.recognization.IStatus.STATUS_SPEAKING;
import static com.thnuth.gondnat.voicearms.recognization.IStatus.STATUS_WAKEUP_SUCCESS;

public class MainActivity extends AppCompatActivity {

    static String TAG = MainActivity.class.getName();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        handler = new Handler() {
            /*
             * @param msg
             */
            @Override
            public void handleMessage(Message msg) {
                super.handleMessage(msg);
                handleMsg(msg);
            }

        };
        aSwitch = findViewById(R.id.switchStart);
        aSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                if (b) {
                    start();
                } else {
                    stop();
                }
            }
        });
        initPermission();
        initRecog();
    }
    protected MyRecognizer myRecognizer;
    protected MyWakeup myWakeup;

    protected Handler handler;

    protected Switch aSwitch;
    /**
     *  0: 方案1， 唤醒词说完后，直接接句子，中间没有停顿。
     * >0 : 方案2： 唤醒词说完后，中间有停顿，然后接句子。推荐4个字 1500ms
     *
     *  backTrackInMs 最大 15000，即15s
     */
    private int backTrackInMs = 1500;

    /*
 * Api的参数类，仅仅用于生成调用START的json字符串，本身与SDK的调用无关
 */
    protected CommonRecogParams apiParams;

    protected void initRecog() {
        // 初始化识别引擎

        StatusRecogListener recogListener = new MessageStatusRecogListener(handler);
        myRecognizer = new MyRecognizer(this, recogListener);

        IWakeupListener listener = new RecogWakeupListener(handler);
        myWakeup = new MyWakeup(this,listener);
    }

    protected  void handleMsg(Message msg) {
        if (msg.what == STATUS_WAKEUP_SUCCESS){
            // 此处 开始正常识别流程
            Map<String, Object> params = new LinkedHashMap<String, Object>();
            params.put(SpeechConstant.ACCEPT_AUDIO_VOLUME, false);
            params.put(SpeechConstant.VAD,SpeechConstant.VAD_DNN);
            int pid = PidBuilder.create().model(PidBuilder.INPUT).toPId(); //如识别短句，不需要需要逗号，将PidBuilder.INPUT改为搜索模型PidBuilder.SEARCH
            params.put(SpeechConstant.PID,pid);
            if (backTrackInMs > 0) { // 方案1， 唤醒词说完后，直接接句子，中间没有停顿。
                params.put(SpeechConstant.AUDIO_MILLS, System.currentTimeMillis() - backTrackInMs);

            }
            myRecognizer.cancel();
            myRecognizer.start(params);
        } else if (STATUS_SPEAKING == msg.what) {
            Log.d(TAG, msg.obj.toString());
        } else if (STATUS_FINISHED == msg.what) {
            if (msg.arg2 == 1) {
                Log.i(TAG, msg.obj.toString());
                Toast toast = Toast.makeText(MainActivity.this
                        , msg.obj.toString()
                        // 设置该Toast提示信息的持续时间
                        , Toast.LENGTH_SHORT);
                toast.show();
            }
        }
    }

    protected  void start() {
        WakeupParams wakeupParams = new WakeupParams(this);
        Map<String,Object> params = wakeupParams.fetch();
        myWakeup.start(params);

    }
    /**
     * 开始录音后，手动停止录音。SDK会识别在此过程中的录音。点击“停止”按钮后调用。
     */
    private void stop() {
        myWakeup.stop();
        myRecognizer.stop();
    }

    /**
     * android 6.0 以上需要动态申请权限
     */
    private void initPermission() {
        String permissions[] = {Manifest.permission.RECORD_AUDIO,
                Manifest.permission.ACCESS_NETWORK_STATE,
                Manifest.permission.INTERNET,
                Manifest.permission.READ_PHONE_STATE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
        };
        ArrayList<String> toApplyList = new ArrayList<String>();
        for (String perm :permissions){
            if (PackageManager.PERMISSION_GRANTED != ContextCompat.checkSelfPermission(this, perm)) {
                toApplyList.add(perm);
                //进入到这里代表没有权限.
            }
        }
        String tmpList[] = new String[toApplyList.size()];
        if (!toApplyList.isEmpty()){
            ActivityCompat.requestPermissions(this, toApplyList.toArray(tmpList), 123);
        }
    }
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        // 此处为android 6.0以上动态授权的回调，用户自行实现。
    }

    protected CommonRecogParams getApiParams() {
        return new OnlineRecogParams(this);
    }

    @Override
    protected void onDestroy() {
        myRecognizer.release();
        myWakeup.release();
        super.onDestroy();
    }
}
