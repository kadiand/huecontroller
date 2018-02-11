package com.boesk.huecontroler;

/**
 * Created by owboateng on 18-1-2016.
 */
import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import com.philips.lighting.hue.sdk.PHAccessPoint;
import com.philips.lighting.hue.sdk.PHHueSDK;
import com.philips.lighting.hue.sdk.PHSDKListener;
import com.philips.lighting.model.PHBridge;
import com.philips.lighting.model.PHGroup;
import com.philips.lighting.model.PHHueParsingError;
import com.philips.lighting.model.PHLight;
import com.philips.lighting.model.PHLightState;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class ActivityReceiver implements PHSDKListener {
    Socket socket = null;
    BufferedReader in = null;
    PrintWriter out = null;

    private boolean startRecord;
    Context context;
    private String activity = null;

    private PHHueSDK phHueSDK;
    PHBridge bridge= null;
    PHAccessPoint ap = null;
    PHLightState state = null;
    PHGroup group = null;

    private boolean stop_bg_threads = false;
    private String partner_name;
    private String hue_ip;
    private String[] light_ids;
    private String server_ip;

    private String exp_type, username;

    float [] active_xy = null;
    float [] passive_xy = null;
    float [] resting_xy = null;
    float [] neutral_xy = null;

    Map<String, PHLight> map = null;
    List  <String> lightIdentifiers = null;

    public ActivityReceiver(Context context, String exp_type, String username, String connect_to, String hue_ip1, String hue_ip2, String hue_ip3, String hue_ip4, String [] light_ids){
        this.context = context;
        this.exp_type = exp_type;
        this.username = username;
        this.partner_name = connect_to.toLowerCase();
        String[] hue_ip_parts = {
                hue_ip1, hue_ip2, hue_ip3, hue_ip4
        };
        this.hue_ip = TextUtils.join(".", hue_ip_parts);
        this.light_ids = light_ids;
        Log.d("check1", this.light_ids[0]);
        Log.d("check1", this.light_ids[1]);

        this.server_ip = "131.155.175.79";
        lightIdentifiers = new ArrayList<String>();

        phHueSDK = PHHueSDK.getInstance();
        phHueSDK.getNotificationManager().registerSDKListener(this);

        ap = new PHAccessPoint();
        ap.setIpAddress(this.hue_ip);
        //ap.setUsername(username);
        state = new PHLightState();

        active_xy = new float[2];
        active_xy[0] = (float)0.6679;
        active_xy[1] = (float)0.3181;

        passive_xy = new float[2];
        passive_xy[0] = (float)0.409;
        passive_xy[1] = (float)0.518;

        resting_xy = new float[2];
        resting_xy[0] = (float)0.1691;
        resting_xy[1] = (float)0.0441;

        neutral_xy = new float[2];
        neutral_xy[0] = (float) 0;
        neutral_xy[1] = (float) 0;

        Log.d("check", "phHueSDK instantiated");


        startRecord = false;
    }

    public void setActivity(String activity) {
        this.activity = activity;
    }

    private void setRunGBThreads(boolean bol){
        this.stop_bg_threads = bol;
    }

    public void socket_connect(){
        try {
            startRecord = true;
            new Thread(new SocketThread()).start();

        }catch(Exception e){
            e.printStackTrace();
        }
    }

    public void socket_close(){
        try {
            if (socket != null) {
                if (out != null) {
                    out.println("exit");
                    out.flush();
                }
                startRecord = false;

                socket.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void startHue(){
        if (!phHueSDK.isAccessPointConnected(ap))
            phHueSDK.connect(ap);
        if (bridge != null) {
            Log.d("check", "Hue bridge connected");
        } else {
            Log.d("check", "Hue bridge is null");
        }

    }


    public void stopHue(){
        startRecord = false;
        if (bridge != null) {
            phHueSDK.disconnect(bridge);
            Log.d("check", "Bridge disconnected");
        }
    }

    public void setExp_type(String exp_type){
        this.exp_type = exp_type;
    }

    @Override
    public void onCacheUpdated(List<Integer> list, PHBridge phBridge) {

    }

    @Override
    public void onBridgeConnected(PHBridge phBridge, String s) {
        phHueSDK.setSelectedBridge(phBridge);
        Log.d("check", "Bridge connected");
        bridge = phHueSDK.getSelectedBridge();
        if (bridge != null) {
            Log.d("check", "Bridge selected");
            boolean on = true;

            group = new PHGroup();
            List<PHLight> lights = bridge.getResourceCache().getAllLights();
            map = new HashMap<String, PHLight>();
            for (PHLight light : lights){
                String id = light.getIdentifier();
                map.put(id, light);
            }
            for (String id: light_ids){
                if (map.keySet().contains(id.trim())){
                    lightIdentifiers.add(id.trim());
                    Log.d("check", "ID found: " + id.trim());
                }
            }
        }
    }

    @Override
    public void onAuthenticationRequired(PHAccessPoint phAccessPoint) {
        Log.d("check", "Authentication requested");
        phHueSDK.startPushlinkAuthentication(phAccessPoint);
    }

    @Override
    public void onAccessPointsFound(List<PHAccessPoint> accessPoints) {
        Log.d("check", "Access points found");
    }

    @Override
    public void onError(int i, String s) {
        Log.d("check", "Hue error");
        Log.d("check", s);
    }

    @Override
    public void onConnectionResumed(PHBridge phBridge) {

    }

    @Override
    public void onConnectionLost(PHAccessPoint phAccessPoint) {

    }

    @Override
    public void onParsingErrors(List<PHHueParsingError> list) {

    }

    class SocketThread implements Runnable {

        @Override
        public void run() {
            try {
                InetAddress serverAddr = InetAddress.getByName(server_ip);
                socket = new Socket(serverAddr, 9999); //Update
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);
                if (out != null){
                    out.println("hue#username#" + username + "#partner#" + partner_name);
                    out.flush();
                }

                if (in == null) {
                    Log.d("socket_check", "In is null");
                }
                else{
                    Log.d("socket_check", "In is not null");
                }
                String classes = in.readLine();
                while (startRecord && classes != null && classes != "") {
                    new Thread(new HueController(classes)).start();
                    classes = in.readLine();
                }
            }catch(Exception e){
                e.printStackTrace();
            }
        }
    }

    class HueController implements Runnable {

        private String classes;
        public HueController(String classes){
            this.classes = classes;
        }

        @Override
        public void run() {
            if (exp_type.equalsIgnoreCase("Lights")) {
                changeHueProperty(classes);
            }
            else{
                changeLightState(neutral_xy);
            }
        }

        public void changeLightState(float [] xy) {
            state.setOn(true);
            state.setX(xy[0]);
            state.setY(xy[1]);

            for (String id: lightIdentifiers){
                bridge.updateLightState(map.get(id), state);
            }
        }

        public void changeHueProperty(String server_resp){
            String[] acts = server_resp.split(" ");
            int len = acts.length;
            for (int i=0; i<len; i++) {
                String val = acts[i];
                val = val.replaceAll("\\s","");
                if (bridge != null && group != null) {
                    if (val.equals("6.0")) {
                        changeLightState(resting_xy);
                        Log.d("light_check", "Laying");
                    } else if (val.equals("5.0")) {
                        changeLightState(passive_xy);
                        Log.d("light_check", "Standing");
                    } else if (val.equals("4.0")) {
                        changeLightState(passive_xy);
                        Log.d("light_check", "Sitting");
                    } else if (val.equals("3.0")) {
                        changeLightState(active_xy);
                        Log.d("light_check", "Walking downstairs");
                    } else if (val.equals("2.0")) {
                        changeLightState(active_xy);
                        Log.d("light_check", "Walking upstairs");
                    } else if (val.equals("1.0")) {
                        changeLightState(active_xy);
                        Log.d("light_check", "Walking");
                    }

                }
                try {
                    // Sleep for 2.5 seconds
                    Thread.sleep(2500);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }

    }
}
