/*
 *
 * Copyright (C) 2013 Alfonso Vinti <me@alfonsovinti.it>
 *
 * Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 * OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 *
 */

package it.alfonsovinti.cordova.plugins.bixolonprint;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;

import java.util.Map;
import java.util.HashMap;
//import java.util.Queue;
import java.util.Set;
import java.util.Locale;
import java.util.ArrayList;

import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.bixolon.printer.BixolonPrinter;
import com.bixolon.printer.print.BitmapManager;
import com.bixolon.printer.service.ServiceManager;
import com.bixolon.printer.property.CodePageManager;
import com.bixolon.printer.utility.Command;
import com.bixolon.printer.property.SecurityManager;
import com.bixolon.printer.print.RunLengthEncoding;
import java.nio.ByteBuffer;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.widget.Toast;

class elemConfig {
    int textWidth;
    int textHeight;
    String textAlign;
    String fontStyle;
    int width;
    int brightness;
    int Alignment;
    public elemConfig(){
        textWidth=0;
            textHeight=0;
            textAlign="";
            fontStyle="";
            Alignment = 0;
    }
}

class printElem {
    String rawData;
    Bitmap bitmap;
    String type;
    elemConfig config;
    public printElem(){
        config = new elemConfig();
        type = "";
        rawData = null;
    }
}


public class BixolonPrint extends CordovaPlugin {

    private static final String TAG = "BixolonPrint";

    // Action to execute
    public static final String ACTION_PRINT_TEXT = "printText";
    public static final String ACTION_PRINT_1D_BARCODE = "printBarCode";
    public static final String ACTION_PRINT_QRCODE = "printQRCode";
    public static final String ACTION_GET_STATUS = "getStatus";
    public static final String ACTION_CUT_PAPER = "cutPaper";
    public static final String ACTION_START_MSR_READER_LISTENER = "startMsrReaderListener";
    public static final String ACTION_STOP_MSR_READER_LISTENER = "stopMsrReaderListener";
    public static final String ACTION_START_CONNECTION_LISTENER = "startConnectionListener";
    public static final String ACTION_RECONNECT = "reconnect";
    public static final String ACTION_DISCONNECT = "disconnect";
    public static final String ACTION_STOP_CONNECTION_LISTENER = "stopConnectionListener";
    public static final String ACTION_PRINT_BITMAP = "printBitmap";
    public static final String ACTION_GET_BITMAP = "bitmap2printerData";

    public static final String ACTION_ADD_RAWLINE = "addRawLine";
    public static final String ACTION_PRINT_RAWLINE = "printRaw";

    // Alignment string
    public static final String ALIGNMENT_LEFT = "LEFT";
    public static final String ALIGNMENT_CENTER = "CENTER";
    public static final String ALIGNMENT_RIGHT = "RIGHT";

    // Font string
    public static final String FONT_A = "A";
    public static final String FONT_B = "B";

    @SuppressWarnings({"serial", "unused"})
    private final static Map<String, String> PRODUCT_IDS = new HashMap<String, String>() {{
        put("10", "SPP-R200");
        put("18", "SPP-100");
        put("22", "SRP-F310");
        put("31", "SRP-350II");
        put("29", "SRP-350plusII");
        put("35", "SRP-F312");
        put("36", "SRP-350IIK");
        put("40", "SPP-R200II");
        put("33", "SPP-R300");
        put("41", "SPP-R400");
    }};

    @SuppressWarnings("serial")
    private final static Map<String, Integer> MAX_COL = new HashMap<String, Integer>() {{
        put("SPP-R200", 0);
        put("SPP-100", 0);
        put("SRP-F310", 0);
        put("SRP-350II", 0);
        put("SRP-350plusII", 0);
        put("SRP-F312", 0);
        put("SRP-350IIK", 0);
        put("SPP-R200II", 0);
        put("SPP-R300", 48);
        put("SPP-R400", 69);
    }};


    public static final int STATUS_STEP_GET_STATUS = 0;
    public static final int STATUS_STEP_GET_BATTERY_STATUS = 1;
    public static final int STATUS_STEP_GET_PRINTER_ID_FIRMWARE_VERSION = 2;
    public static final int STATUS_STEP_GET_PRINTER_ID_MANUFACTURER = 3;
    public static final int STATUS_STEP_GET_PRINTER_ID_PRINTER_MODEL = 4;
    public static final int STATUS_STEP_GET_PRINTER_ID_CODE_PAGE = 5;
    public static final int STATUS_STEP_GET_PRINTER_ID_MODEL_ID = 6;
    public static final int STATUS_STEP_GET_PRINTER_ID_PRODUCT_SERIAL = 7;
    public static final int STATUS_STEP_GET_PRINTER_ID_TYPE_ID = 8;
    public static final int STATUS_STEP_COMPLETE = 10;

    //
    private CallbackContext cbContext;
    private String lastActionName;
    private JSONArray lastActionArgs;
    private String actionSuccess;
    private String actionError;
    //private Queue<Integer> printerQueue;

    public boolean isValidAction;
    public boolean optAutoConnect;
    public boolean optToastMessage;

    private int statusStep = 0;

    public String mConnectedDeviceName;
    public String mConnectedDeviceAddress;
    private JSONObject mConnectedDeviceStatus;
    public boolean mIsConnected;
    static BixolonPrinter mBixolonPrinter;
    static BitmapManager   bmpManager;
    static CodePageManager cpManager;
    private ServiceManager srvManager;
    //

    // MSR Reader
    private CallbackContext msrReaderCallbackContext;
    private CallbackContext connectionListenerCallbackContext;

    private ArrayList<Object> lines;
    private JSONArray  lines_json = new JSONArray();

    @Override
    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
        Log.d(TAG, "BixolonPrint.initialize_START");

        super.initialize(cordova, webView);

        mBixolonPrinter = new BixolonPrinter(cordova.getActivity(), mHandler, null);
        bmpManager = new BitmapManager();
        srvManager = new ServiceManager(this.hashCode(), cordova.getActivity(), mHandler, null);
        srvManager.executeCommand(Command.INITIALIZATION, false);
        cpManager = new CodePageManager();

        Locale.setDefault(new Locale("pt","PORTUGAL", "PT"));

        lines = new ArrayList<Object>();
        this.mIsConnected = false;
        this.mConnectedDeviceName = null;
        this.mConnectedDeviceAddress = null;
        this.mConnectedDeviceStatus = null;
        this.actionSuccess = null;
        this.actionError = null;
        this.lastActionArgs = null;
        this.lastActionName = null;

        Log.d(TAG, "BixolonPrint.initialize_END");
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "BixolonPrint.onDestroy_START");

        super.onDestroy();

        this.isValidAction = false;
        //mBixolonPrinter.disconnect();

        this.srvManager.disconnect();
        Log.d(TAG, "BixolonPrint.onDestroy_END");
    }

    /**
     * Executes the request and returns PluginResult
     *
     * @param action          Action to execute
     * @param args            JSONArray of arguments to the plugin
     * @param callbackContext The callback context used when calling back into JavaScript.
     * @return A PluginRequest object with a status
     */

    @Override
    public boolean execute(String action, JSONArray args, final CallbackContext callbackContext) {
        Log.d(TAG, "BixolonPrint.execute_START");
        this.isValidAction = true;
        this.cbContext = callbackContext;
        this.lastActionName = action;
        this.lastActionArgs = args;

        Log.i(TAG, "action: " + action);

        if (ACTION_PRINT_TEXT.equals(action)) {
            JSONObject printConfig = args.optJSONObject(1);
            this.optAutoConnect = printConfig.optBoolean("autoConnect");
            this.optToastMessage = printConfig.optBoolean("toastMessage");
        } else if (ACTION_PRINT_1D_BARCODE.equals(action)) {
            JSONObject printConfig = args.optJSONObject(1);
            this.optAutoConnect = printConfig.optBoolean("autoConnect");
            this.optToastMessage = printConfig.optBoolean("toastMessage");
        } else if (ACTION_PRINT_QRCODE.equals(action)) {
            JSONObject printConfig = args.optJSONObject(1);
            this.optAutoConnect = printConfig.optBoolean("autoConnect");
            this.optToastMessage = printConfig.optBoolean("toastMessage");
        } else if (ACTION_CUT_PAPER.equals(action)) {
            JSONObject printConfig = args.optJSONObject(0);
            this.optAutoConnect = printConfig.optBoolean("autoConnect");
            this.optToastMessage = printConfig.optBoolean("toastMessage");
        } else if (ACTION_GET_STATUS.equals(action)) {
            JSONObject printConfig = args.optJSONObject(1);
            this.optAutoConnect = printConfig.optBoolean("autoConnect");
            this.optToastMessage = printConfig.optBoolean("toastMessage");
        } else if (ACTION_START_MSR_READER_LISTENER.equals(action)) {
            this.optAutoConnect = true;
            if(!this.startMsrReaderListener()) {
                return true;
            }
        } else if (ACTION_STOP_MSR_READER_LISTENER.equals(action)) {
            this.optAutoConnect = true;
            this.stopMsrReaderListener();
        } else if (ACTION_RECONNECT.equals(action)) {
            this.optAutoConnect = true;
        } else if (ACTION_DISCONNECT.equals(action)) {
            this.disconnect();
            return true;
        } else if (ACTION_START_CONNECTION_LISTENER.equals(action)) {
            this.optAutoConnect = true;
            if(!this.startConnectionListener()) {
                return true;
            }
        } else if (ACTION_STOP_CONNECTION_LISTENER.equals(action)) {
            this.optAutoConnect = true;
            this.stopConnectionListener();
        } else if (ACTION_PRINT_BITMAP.equals(action)) {
            this.optAutoConnect = true;
            this.optToastMessage = true;
        } else if (ACTION_GET_BITMAP.equals(action)) {
                     this.optAutoConnect = true;
                     this.optToastMessage = true;
/*                     PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, this.bitmap2printerData());
                     pluginResult.setKeepCallback(true);
                     callbackContext.sendPluginResult(pluginResult);*/
          } else if (ACTION_ADD_RAWLINE.equals(action)) {
                              this.optAutoConnect = true;
                              this.optToastMessage = true;
                              PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, this.addRawLine());
                              pluginResult.setKeepCallback(true);
                              callbackContext.sendPluginResult(pluginResult);
        } else if (ACTION_PRINT_RAWLINE.equals(action)) {
                                PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, this.printRaw());
                                pluginResult.setKeepCallback(true);
                                callbackContext.sendPluginResult(pluginResult);
          } else {
            this.isValidAction = false;
            this.cbContext.error("Invalid Action : " + action);
            Log.d(TAG, "Invalid action : " + action + " passed");
        }

        if (this.isValidAction) {
            this.connect();
        }

        Log.d(TAG, "BixolonPrint.execute_END");
        return this.isValidAction;
    }

    /**
     *
     */
    private void connect() {
        Log.d(TAG, "BixolonPrint.connect_START");

        if (this.mIsConnected) {
            this.onConnect();
        } else {
            mBixolonPrinter.findBluetoothPrinters();
            //mBixolonPrinter.findNetworkPrinters(3000);
            //mBixolonPrinter.findUsbPrinters();
        }

        Log.d(TAG, "BixolonPrint.connect_END");
    }

    private void onConnect() {
        Log.d(TAG, "BixolonPrint.onConnect_START");

        this.mIsConnected = true;

        if (ACTION_PRINT_TEXT.equals(this.lastActionName)) {
            this.printText();
        } else if (ACTION_PRINT_1D_BARCODE.equals(this.lastActionName)) {
            this.printBarCode();
        } else if (ACTION_PRINT_QRCODE.equals(this.lastActionName)) {
            this.printQRCode();
        } else if (ACTION_CUT_PAPER.equals(this.lastActionName)) {
            this.cutPaper();
        } else if (ACTION_GET_STATUS.equals(this.lastActionName)) {
            this.getStatus();
        } else if(ACTION_PRINT_BITMAP.equals(this.lastActionName)){
            this.printBitmap();
        } /*else if(ACTION_GET_BITMAP.equals(this.lastActionName)){
           this.bitmap2printerData();
        }*/

        this.sendConnectionData();

        Log.d(TAG, "BixolonPrint.onConnect_END");
    }

    /**
     *
     */
    private void disconnect() {
        Log.d(TAG, "BixolonPrint.disconnect_START");
        mBixolonPrinter.disconnect();
        srvManager.disconnect();
        Log.d(TAG, "BixolonPrint.disconnect_END");
    }

    private void onDisconnect() {
        Log.d(TAG, "BixolonPrint.onDisconnect_START");

        String action = this.lastActionName;
        String error = this.actionError;
        String success = this.actionSuccess;
        JSONObject status = this.mConnectedDeviceStatus;

        if (!this.mIsConnected && this.isValidAction) {
            this.cbContext.error("Connection failed");
            return;
        }

        this.mIsConnected = false;
        this.mConnectedDeviceName = null;
        this.mConnectedDeviceAddress = null;
        this.mConnectedDeviceStatus = null;

        this.actionSuccess = null;
        this.actionError = null;
        this.lastActionArgs = null;
        this.lastActionName = null;

        this.statusStep = STATUS_STEP_GET_STATUS;

        if (error != null) {
            Log.d(TAG, "End with error");
            Log.d(TAG, error);
            this.cbContext.error(error);
        } else {
            if (ACTION_GET_STATUS.equals(action)) {
                this.cbContext.success(status);
            } else {
                this.cbContext.success(success);
            }

            this.sendConnectionData();
        }

        Log.d(TAG, "BixolonPrint.onDisconnect_END");
    }

    /**
        print image base64
        based on
        https://github.com/itsKaynine/cordova-plugin-bixolon-printing
     */
    public static Bitmap decodeBase64Image(String input) {
		String trimmed = input.replace("data:image/png;base64,", "").replace("data:image/jpg;base64,", "").replace("data:image/jpeg;base64,", "");
	    byte[] arr = Base64.decode(input, 0);
	    return BitmapFactory.decodeByteArray(arr, 0, arr.length);
	}
//Codigo adicionado por David.Ricardo para gerir posicionamento de imagens
    private JSONArray addRawLine() {
        Log.d(TAG, "AddRawLine_BEGIN");
        Object obj;
        Object config;
        String type;
        String base64Image;
        int width ;
        int brightness;
        int alignment;
        byte[] temp = new byte[1];
        try {
            type = this.lastActionArgs.getString(0);
        }catch (JSONException e1) {
            this.isValidAction = false;
            this.actionError = "error: " + e1.getMessage();
            this.disconnect();
            temp[0] = (byte) '1';
            return null;
        }
        if(type.equals("img")) {
           try {
                base64Image = this.lastActionArgs.getString(1);
                width = this.lastActionArgs.getInt(2);
                brightness = this.lastActionArgs.getInt(3);
                alignment = this.lastActionArgs.getInt(4);
            }catch (JSONException e1) {
                this.isValidAction = false;
                this.actionError = "print error: " + e1.getMessage() + "TYPE IMAGE";
                this.disconnect();
                temp[0] = (byte) '2';
                //lines_json.put(this.actionError);
                return null;
            }
            printElem elem =  new printElem();
            lastActionArgs = new JSONArray();
            lastActionArgs.put(base64Image);
            lastActionArgs.put(width);
            lastActionArgs.put(brightness);
            lastActionArgs.put(alignment);
            elem.bitmap = bitmap2printerData();
            elem.rawData = base64Image;
            elem.type="img";
            elem.config = new elemConfig();
            elem.config.Alignment=0;//alignment;
            this.lines.add(elem);
            //lines_json.put(elem);
            try{
                 JSONObject obj_img = new JSONObject();
                 obj_img.put("data:" , elem.rawData);
                 //lines_json.put(obj_img);
             }
             catch (JSONException e1) {
                 this.isValidAction = false;
                 this.actionError = "print error: " + e1.getMessage();
                 this.disconnect();
                 temp[0] = (byte) '2';
                 return null;
             }
            return lines_json;
        }
        else if(type.equals("txt"))  {
            String text;
            int textWidth;
            int textHeight;
            String textAlign;
            String fontStyle;
            try {
                text = this.lastActionArgs.getString(1);
                textWidth = this.lastActionArgs.getInt(2);
                textHeight = this.lastActionArgs.getInt(3);
                textAlign = this.lastActionArgs.getString(4);
                fontStyle = this.lastActionArgs.getString(5);
            }catch (JSONException e1) {
                this.isValidAction = false;
                this.actionError = "print error: " + e1.getMessage();
                this.disconnect();
                temp[0] = (byte) '2';
                return null;
            }
             printElem elem = new printElem();
             elem.rawData=text.toString();
             elem.config = new elemConfig();
             elem.config.textWidth = textWidth;
             elem.config.textHeight = textHeight;
             elem.config.textAlign = textAlign;
             elem.config.fontStyle = fontStyle;
             elem.type="txt";
             this.lines.add(elem);
             //lines_json.put(elem);
             try{
                 JSONObject obj_txt = new JSONObject();
                 obj_txt.put("data:" , elem.rawData);
                 //lines_json.put(obj_txt);
             }
             catch (JSONException e1) {
                 this.isValidAction = false;
                 this.actionError = "print error: " + e1.getMessage();
                 this.disconnect();
                 temp[0] = (byte) '2';
                 return null;
             }
            return lines_json;
        }
         return lines_json;
    }

	private Bitmap bitmap2printerData() {
	    Log.d(TAG, "Bitmap2Printer_BEGIN");
	    String base64Image;
        int width ;
        int brightness;
        int alignment;
        try {
            base64Image = this.lastActionArgs.getString(0);
            width = this.lastActionArgs.getInt(1);
            brightness = this.lastActionArgs.getInt(2);
            alignment = this.lastActionArgs.getInt(3);
        }catch (JSONException e1) {
            this.isValidAction = false;
            this.actionError = "print error: " + e1.getMessage();
            this.disconnect();
            byte[] temp = new byte[1];
            temp[0] = (byte) '1';
            return null;
        }
        Bitmap bitmap = decodeBase64Image(base64Image);
        this.actionSuccess = "getdata success";
	    return bitmap;
	}

	private byte[] text2printerData() {
    	    Log.d(TAG, "text2Printer_BEGIN");
    	    String txt;
    	   try {
               txt = this.lastActionArgs.getString(0);
           }catch (JSONException e1) {
               this.isValidAction = false;
               this.actionError = "print error: " + e1.getMessage();
               this.disconnect();
               byte[] temp = new byte[1];
               temp[0] = (byte) '1';
               return temp;
           }
    	    final byte[] bytes = CodePageManager.getBytes(txt, Locale.getDefault(), 16);
    	    return bytes;
    }

    //////
    private String printRaw() {
            Log.d(TAG, "printRaw_BEGIN");
           // try {
                JSONArray response = new JSONArray();
                JSONObject res = new JSONObject();
                ArrayList<ByteBuffer> buffers = new ArrayList<ByteBuffer>();
                String commands = "";
                for(Object elem : lines){
                    if(((printElem) elem).type == "txt")
                    {
                        //commands = commands +
                        this.printRawText(((printElem) elem).rawData, ((printElem) elem).config);// + "\r\n";
                    } else if((((printElem) elem).type == "img")) {
                        //commands = commands +
                        this.printRawImage(/*((printElem) elem).bitmap,*/ ((printElem) elem).rawData, ((printElem) elem).config);// + "\r\n";
                    }
                }
                int size = 0;
                for(Object buff : buffers){
                    size = size + ((ByteBuffer) buff).array().length;
                }
                final ByteBuffer allocate_res = ByteBuffer.allocate(size);
                int offset=0;
                for(Object buff : buffers){
                    allocate_res.put(((ByteBuffer) buff).array(),offset,((ByteBuffer) buff).array().length);
                    offset=offset+((ByteBuffer) buff).array().length;
                }

                lines = new ArrayList<Object>();
                return this.actionSuccess;
        }

    /////
    private String printRawImage(String base64Image, elemConfig config) {
                Log.d(TAG, "printRawImage_BEGIN");
                Bitmap bitmap = decodeBase64Image(base64Image);
                printBitmap1(bitmap,100,-2, 50,false);
                       /* if (bitmap == null) {
                        this.actionSuccess = this.actionSuccess +  "sem bitmap";
                                    return this.actionSuccess;
                                }
                          else {
                            this.actionSuccess = this.actionSuccess +  bitmap.toString();
                          }


                       int width = 100; //bitmap.getWidth();
                        Log.i("BixolonPrinter", "++ printBitmap(" + bitmap + ", " + -2 + ", " + width + ", " + false + ") ++");
                        final int maxWidth = this.srvManager.getMaxWidth();
                        if (width == -1) {
                            width = maxWidth;
                        }
                        else if (width == 0 || width < 0) {
                            width = bitmap.getWidth();
                        }
                        if (width > maxWidth) {
                            width = maxWidth;
                        }
                        final int bitmapHeight = BitmapManager.getBitmapHeight(bitmap, width);
                         Log.d("BixolonPrinter", "bitmap width: " + width + ", bitmap height: " + bitmapHeight);
                        final byte[] bitmap2printerData = BitmapManager.bitmap2printerData(bitmap, width, -2, 0, true);
                       this.a(bitmap2printerData,width,bitmapHeight,-2,false);*/

                this.actionSuccess = "raw print Image success";
                return this.actionSuccess;
            }



            public void printBitmap1(final Bitmap bitmap, final int n, int width, final int n2, final boolean b) {
                    if (BixolonPrinter.D) {
                        Log.i("BixolonPrinter", "++ printBitmap(" + bitmap + ", " + n + ", " + width + ", " + n2 + ") ++");
                    }
                    if (bitmap == null) {
                        return;
                    }
                    final int maxWidth = this.srvManager.getMaxWidth();
                    if (width == -1) {
                        width = maxWidth;
                    }
                    else if (width == 0 || width < 0) {
                        width = bitmap.getWidth();
                    }
                    if (width > maxWidth) {
                        width = maxWidth;
                    }
                    byte[] bitmap2printerData;
                    try {
                        bitmap2printerData = BitmapManager.bitmap2printerData(bitmap, width, n2, 0, true);
                    }
                    catch (RuntimeException ex2) {
                        final RuntimeException ex = ex2;
                        ex2.printStackTrace();
                        Log.e("BixolonPrinter", ex.toString());
                        this.mHandler.obtainMessage(14, (Object)this.hashCode()).sendToTarget();
                        return;
                    }
                    this.a(bitmap2printerData, width, BitmapManager.getBitmapHeight(bitmap, width), 1, b);
                }


            private void a(final byte[] array, int bytesOfWidth, final int n, final int n2, final boolean b) {
                    int n3 = 1;
                    if (n > 1662) {
                        n3 = 1 + n / 1662;
                    }
                    bytesOfWidth = BitmapManager.bytesOfWidth(bytesOfWidth);
                    for (int i = 0; i < n3; ++i) {
                        final int n4 = (n > 1662 * (i + 1)) ? 1662 : (n % 1662);
                        final byte[] array2 = new byte[bytesOfWidth * n4];
                        System.arraycopy(array, bytesOfWidth * (i * 1662), array2, 0, array2.length);
                        final ByteBuffer allocate = ByteBuffer.allocate(Command.ALIGNMENT_LEFT.length + Command.RASTER_BIT_IMAGE_NORMAL.length + 4 + array2.length);
                        switch (n2) {
                            case 1: {
                                allocate.put(Command.ALIGNMENT_CENTER);
                                break;
                            }
                            case 2: {
                                allocate.put(Command.ALIGNMENT_RIGHT);
                                break;
                            }
                            default: {
                                allocate.put(Command.ALIGNMENT_LEFT);
                                break;
                            }
                        }
                        allocate.put(Command.RASTER_BIT_IMAGE_NORMAL);
                        allocate.put((byte)(bytesOfWidth % 256));
                        allocate.put((byte)(bytesOfWidth / 256));
                        allocate.put((byte)(n4 % 256));
                        allocate.put((byte)(n4 / 256));
                        allocate.put(array2);
                        this.srvManager.executeCommand(allocate.array(), i >= n3 - 1 && b);
                    }
                }


    /////
        private void printRawText(String text, elemConfig config) {
                    Log.d(TAG, "printRawText_BEGIN");
                    this.srvManager.executeCommand(10, 16);
                    final byte[] bytes = CodePageManager.getBytes(text, Locale.getDefault(), 16);
                    byte[] securityCode = null;
                    int n4 = Command.ALIGNMENT_LEFT.length + Command.DEVICE_FONT_A.length + Command.UNDERLINE_OFF.length + Command.EMPHASIZED_OFF.length
                            + Command.REVERSE_OFF.length + Command.CHARACTER_SIZE.length + 1 + bytes.length + 1;
                    final ByteBuffer allocate = ByteBuffer.allocate(n4);
                    String commands = text + " :";
                    if(config.textAlign.equals("center")) {
                        allocate.put(Command.ALIGNMENT_CENTER);
                    } else if(config.textAlign.equals("right")) {
                        allocate.put(Command.ALIGNMENT_RIGHT);
                    } else if(config.textAlign.equals("left")){
                         allocate.put(Command.ALIGNMENT_LEFT);
                    }
                    if(config.fontStyle.equals("underline")){
                        allocate.put(Command.UNDERLINE_1DOT_THICK);
                    } else if(config.fontStyle.equals("bold")){
                        allocate.put(Command.EMPHASIZED_ON);
                    }
                    allocate.put(Command.CHARACTER_SIZE);
                    allocate.put((byte)config.textWidth);
                    allocate.put(bytes);
                    this.srvManager.executeCommand(allocate.array(), false);
                }
    ////
//Fim de codigo novo, 20/01/2020

    private void printBitmap() {
        Log.d(TAG, "printBitmap_BEGIN");
        String base64Image;
        int width;
        int brightness;
        int alignment;
        try {
            base64Image = this.lastActionArgs.getString(0);
            width = this.lastActionArgs.getInt(1);
            brightness = this.lastActionArgs.getInt(2);
            alignment = this.lastActionArgs.getInt(3);
        }catch (JSONException e1) {
            this.isValidAction = false;
            this.actionError = "print error: " + e1.getMessage();
            this.disconnect();
            return;
        }

            int lineFeed = 3;
            Boolean formFeed = false;

        Bitmap bmp = decodeBase64Image(base64Image);
        try{
            //mBixolonPrinter.printBitmap(bmp,alignment, width,brightness,false);
            mBixolonPrinter.printBitmap(bmp,width,alignment, brightness,false);
        }
        catch (Exception e2) {
            this.isValidAction = false;
            this.actionError = "print error: " + e2.getMessage();
            this.disconnect();
            return;
        }

        this.actionSuccess = "print success";
    }

    private void printQRCode() {
        Log.d(TAG, "BixolonPrint.printQRCode_START");

        JSONObject data;
        JSONObject printConfig;
        boolean formFeed;

        try {
            data = this.lastActionArgs.getJSONObject(0);
            printConfig = this.lastActionArgs.getJSONObject(1);
            formFeed = printConfig.getBoolean("formFeed");
        } catch (JSONException e1) {
            this.isValidAction = false;
            this.actionError = "print error: " + e1.getMessage();
            this.disconnect();
            return;
        }

        String text = data.optString("text");
        String align = data.optString("alignment");
        int size = data.optInt("size");
        int model = data.optInt("model");
        int qrcodeAlignment = this.getAlignment(align);

        try {
            Log.d(TAG, "BixolonPrint.printQRCode: data: " + text);
            if(formFeed) {
                mBixolonPrinter.printQrCode(text, qrcodeAlignment, model, size, false);
                mBixolonPrinter.formFeed(true);
            } else {
                mBixolonPrinter.printQrCode(text, qrcodeAlignment, model, size, true);
            }
        } catch (Exception e2) {
            this.isValidAction = false;
            this.actionError = "print error: " + e2.getMessage();
            this.disconnect();
            return;
        }

        this.actionSuccess = "print success";

        Log.d(TAG, "BixolonPrint.printQRCode_END");
    }

    private void printBarCode() {
        Log.d(TAG, "BixolonPrint.printBarCode_START");

        JSONObject data;
        JSONObject printConfig;
        boolean formFeed;

        try {
            data = this.lastActionArgs.getJSONObject(0);
            printConfig = this.lastActionArgs.getJSONObject(1);
            formFeed = printConfig.getBoolean("formFeed");
        } catch (JSONException e1) {
            this.isValidAction = false;
            this.actionError = "print error: " + e1.getMessage();
            this.disconnect();
            return;
        }

        String text = data.optString("text");
        String align = data.optString("alignment");
        int width = data.optInt("width");
        int height = data.optInt("height");

        int barCodeSystem = data.optInt("barcodeSystem");
        int barcodeAlignment = this.getAlignment(align);
        int characterPosition = data.optInt("characterPosition");

        try {
            Log.d(TAG, "BixolonPrint.printBarCode: data: " + text);
            if(formFeed) {
                mBixolonPrinter.print1dBarcode(text, barCodeSystem, barcodeAlignment, width, height, characterPosition, false);
                mBixolonPrinter.formFeed(true);
            } else {
                mBixolonPrinter.print1dBarcode(text, barCodeSystem, barcodeAlignment, width, height, characterPosition, true);
            }
        } catch (Exception e2) {
            this.isValidAction = false;
            this.actionError = "print error: " + e2.getMessage();
            this.disconnect();
            return;
        }

        this.actionSuccess = "print success";

        Log.d(TAG, "BixolonPrint.printBarCode_END");
    }

    private void printText() {
        Log.d(TAG, "BixolonPrint.printText_START");

        String hrBCode = "[hr]";
        int paperWidth = 0;

        JSONArray textLines;
        JSONObject printConfig;
        boolean formFeed = true;
        int lineFeed = 0;
        int codePage = 16;

        try {
            textLines = this.lastActionArgs.getJSONArray(0);
       /*     printConfig = this.lastActionArgs.getJSONObject(1);
            formFeed = printConfig.getBoolean("formFeed");
            lineFeed = printConfig.getInt("lineFeed");
            codePage = printConfig.getInt("codePage");*/
        } catch (JSONException e1) {
            this.isValidAction = false;
            this.actionError = "print error: " + e1.getMessage() + "111";
            this.disconnect();
            return;
        }

        String text;
        String align;
        String fontType;
        String fontStyle;
        int height;
        int width;

        int textAlignment;
        int textAttribute;
        int textSize;

        if (MAX_COL.containsKey(this.mConnectedDeviceName)) {
            paperWidth = MAX_COL.get(this.mConnectedDeviceName);
        }

        mBixolonPrinter.setSingleByteFont(codePage);

        JSONObject textLine;
        int arlength = textLines.length();

        for (int i = 0; i < arlength; i++) {
            try {

                Log.d(TAG, "BixolonPrint.printText: line:" + (i + 1) + " of " + arlength);

                textLine = textLines.getJSONObject(i);
                text = textLine.optString("text");
                align = textLine.optString("textAlign");
                width = textLine.optInt("textWidth");
                height = textLine.optInt("textHeight");
                fontType = textLine.optString("fontType");
                fontStyle = textLine.optString("fontStyle");

                if (hrBCode.equals(text.substring(0, Math.min(4, text.length())))) {
                    String hrStr = text.substring(text.length() - 1);
                    text = "";
                    for (int j = 0; j < paperWidth; j++) {
                        text += hrStr;
                    }
                    text += "\r\n";
                }

                textAlignment = this.getAlignment(align);
                textAttribute = this.getAttribute(fontType, fontStyle);
                textSize = this.getTextSize(width, height);

                // mBixolonPrinter.printText(text + "\r\n", textAlignment, textAttribute, textSize, false);
                mBixolonPrinter.printText(text, textAlignment, textAttribute, textSize, false);

            } catch (JSONException e2) {
                this.isValidAction = false;
                this.actionError = "print error: " + e2.getMessage() + "222";
                this.disconnect();
                return;
            }
        }

        if (formFeed) {
            mBixolonPrinter.formFeed(false);
        } else {
            mBixolonPrinter.lineFeed(lineFeed, false);
        }

        mBixolonPrinter.cutPaper(true);
        mBixolonPrinter.kickOutDrawer(BixolonPrinter.DRAWER_CONNECTOR_PIN5);

        this.actionSuccess = "print success";
        //this.disconnect();

        Log.d(TAG, "BixolonPrint.printText_END");
    }

    private void cutPaper() {
        Log.d(TAG, "BixolonPrint.cutPaper_START");

        JSONObject printConfig;
        boolean formFeed;
        int lineFeed;

        try {
            printConfig = this.lastActionArgs.getJSONObject(0);
            formFeed = printConfig.getBoolean("formFeed");
            lineFeed = printConfig.getInt("lineFeed");
        } catch (JSONException e1) {
            this.isValidAction = false;
            this.actionError = "cut paper error: " + e1.getMessage();
            this.disconnect();
            return;
        }

        if (formFeed) {
            mBixolonPrinter.formFeed(false);
        } else {
            mBixolonPrinter.lineFeed(lineFeed, false);
        }

        mBixolonPrinter.cutPaper(true);
        mBixolonPrinter.kickOutDrawer(BixolonPrinter.DRAWER_CONNECTOR_PIN5);

        this.actionSuccess = "cut paper success";
        //this.disconnect();

        Log.d(TAG, "BixolonPrint.cutPaper_END");
    }

    private void onPrintComplete() {
        Log.d(TAG, "BixolonPrint.onPrintComplete_START");
        this.disconnect();
        Log.d(TAG, "BixolonPrint.onPrintComplete_END");
    }

    private void getStatus() {
        Log.d(TAG, "BixolonPrint.getStatus_START");

        switch (this.statusStep) {
            case STATUS_STEP_GET_STATUS:
                Log.d(TAG, "BixolonPrint.getStatus: STATUS_STEP_GET_STATUS");
                this.mConnectedDeviceStatus = new JSONObject();
                try {
                    this.mConnectedDeviceStatus.put("printerName", this.mConnectedDeviceName);
                    this.mConnectedDeviceStatus.put("printerAddress", this.mConnectedDeviceAddress);
                } catch (JSONException e1) {
                }
                mBixolonPrinter.getStatus();
                return;
            case STATUS_STEP_GET_BATTERY_STATUS:
                Log.d(TAG, "BixolonPrint.getStatus: STATUS_STEP_GET_BATTERY_STATUS");
                mBixolonPrinter.getBatteryStatus();
                return;
            case STATUS_STEP_GET_PRINTER_ID_FIRMWARE_VERSION:
                Log.d(TAG, "BixolonPrint.getStatus: STATUS_STEP_GET_PRINTER_ID_FIRMWARE_VERSION");
                mBixolonPrinter.getPrinterId(BixolonPrinter.PRINTER_ID_FIRMWARE_VERSION);
                return;
            case STATUS_STEP_GET_PRINTER_ID_MANUFACTURER:
                Log.d(TAG, "BixolonPrint.getStatus: STATUS_STEP_GET_PRINTER_ID_MANUFACTURER");
                mBixolonPrinter.getPrinterId(BixolonPrinter.PRINTER_ID_MANUFACTURER);
                return;
            case STATUS_STEP_GET_PRINTER_ID_PRINTER_MODEL:
                Log.d(TAG, "BixolonPrint.getStatus: STATUS_STEP_GET_PRINTER_ID_PRINTER_MODEL");
                mBixolonPrinter.getPrinterId(BixolonPrinter.PRINTER_ID_PRINTER_MODEL);
                return;
            case STATUS_STEP_GET_PRINTER_ID_CODE_PAGE:
                Log.d(TAG, "BixolonPrint.getStatus: STATUS_STEP_GET_PRINTER_ID_CODE_PAGE");
                mBixolonPrinter.getPrinterId(BixolonPrinter.PRINTER_ID_CODE_PAGE);
                return;
            case STATUS_STEP_GET_PRINTER_ID_MODEL_ID:
                Log.d(TAG, "BixolonPrint.getStatus: STATUS_STEP_GET_PRINTER_ID_MODEL_ID");
                mBixolonPrinter.getPrinterId(BixolonPrinter.PRINTER_ID_MODEL_ID);
                return;
            case STATUS_STEP_GET_PRINTER_ID_PRODUCT_SERIAL:
                Log.d(TAG, "BixolonPrint.getStatus: STATUS_STEP_GET_PRINTER_ID_PRODUCT_SERIAL");
                mBixolonPrinter.getPrinterId(BixolonPrinter.PRINTER_ID_PRODUCT_SERIAL);
                return;
            case STATUS_STEP_GET_PRINTER_ID_TYPE_ID:
                Log.d(TAG, "BixolonPrint.getStatus: STATUS_STEP_GET_PRINTER_ID_TYPE_ID");
                mBixolonPrinter.getPrinterId(BixolonPrinter.PRINTER_ID_TYPE_ID);
                return;

            case STATUS_STEP_COMPLETE:
                this.statusStep = STATUS_STEP_GET_STATUS;
                break;
        }

        boolean printStatus = false;

        try {
            printStatus = this.lastActionArgs.getBoolean(0);
        } catch (JSONException e1) {
            this.isValidAction = false;
            this.actionError = "get status error: " + e1.getMessage();
            this.disconnect();
            return;
        }

        if (printStatus) {
            String text = "";

            try {
                text = //"Status: " + this.mConnectedDeviceStatus.get("status") + "\n" +
                        "Cover: " + this.mConnectedDeviceStatus.get("cover") + "\n" +
                        "Paper: " + this.mConnectedDeviceStatus.get("paper") + "\n" +
                        "Battery: " + this.mConnectedDeviceStatus.get("battery") + "\n" +
                        "Firmware Version: " + this.mConnectedDeviceStatus.get("firmwareVersion") + "\n" +
                        "Manufacturer: " + this.mConnectedDeviceStatus.get("manufacturer") + "\n" +
                        "Printer Model: " + this.mConnectedDeviceStatus.get("printerModel") + "\n" +
                        "Printer Name: " + this.mConnectedDeviceStatus.get("printerName") + "\n" +
                        "Printer Address: " + this.mConnectedDeviceStatus.get("printerAddress") + "\n" +
                        //"Model ID: " + this.mConnectedDeviceStatus.get("modelId") + "\n" +
                        //"Product Serial: " + this.mConnectedDeviceStatus.get("productSerial") + "\n" +
                        //"Type ID: " + this.mConnectedDeviceStatus.get("typeId") + "\n" +
                        "Code Page: " + this.mConnectedDeviceStatus.get("codePage");
            } catch (JSONException e) {
                e.printStackTrace();
            }

            mBixolonPrinter.printText(text + "\r\n", 0, 0, 0, false);
            mBixolonPrinter.lineFeed(3, false);
            mBixolonPrinter.cutPaper(true);
        } else {
            this.disconnect();
        }

        Log.d(TAG, "BixolonPrint.getStatus_END");
    }

    private void onMessageRead(Message msg) {
        Log.d(TAG, "BixolonPrint.onMessageRead_START: " + msg.arg1);

        boolean isMsrTrackDatas = false;

        switch (msg.arg1) {
            case BixolonPrinter.PROCESS_GET_STATUS:
                if (msg.arg2 == BixolonPrinter.STATUS_NORMAL) {
                    try {
                        //this.mConnectedDeviceStatus.put("status", "NO ERROR");
                        this.mConnectedDeviceStatus.put("cover", "OPENED");
                        this.mConnectedDeviceStatus.put("paper", "FILL");
                    } catch (JSONException e) {
                    }
                } else {
                    if ((msg.arg2 & BixolonPrinter.STATUS_COVER_OPEN) == BixolonPrinter.STATUS_COVER_OPEN) {
                        try {
                            this.mConnectedDeviceStatus.put("cover", "OPENED");
                        } catch (JSONException e) {
                        }
                    } else {
                        try {
                            this.mConnectedDeviceStatus.put("cover", "CLOSED");
                        } catch (JSONException e) {
                        }
                    }

                    if ((msg.arg2 & BixolonPrinter.STATUS_PAPER_NOT_PRESENT) == BixolonPrinter.STATUS_PAPER_NOT_PRESENT) {
                        try {
                            this.mConnectedDeviceStatus.put("paper", "EMPTY");
                        } catch (JSONException e) {
                        }
                    } else {
                        try {
                            this.mConnectedDeviceStatus.put("paper", "FILL");
                        } catch (JSONException e) {
                        }
                    }
                }

                this.statusStep = STATUS_STEP_GET_BATTERY_STATUS;
                break;

            case BixolonPrinter.PROCESS_GET_BATTERY_STATUS:
                switch (msg.arg2) {
                    case BixolonPrinter.STATUS_BATTERY_FULL:
                        try {
                            this.mConnectedDeviceStatus.put("battery", "FULL");
                        } catch (JSONException e) {
                        }
                        break;
                    case BixolonPrinter.STATUS_BATTERY_HIGH:
                        try {
                            this.mConnectedDeviceStatus.put("battery", "HIGH");
                        } catch (JSONException e) {
                        }
                        break;
                    case BixolonPrinter.STATUS_BATTERY_MIDDLE:
                        try {
                            this.mConnectedDeviceStatus.put("battery", "MIDDLE");
                        } catch (JSONException e) {
                        }
                        break;
                    case BixolonPrinter.STATUS_BATTERY_LOW:
                        try {
                            this.mConnectedDeviceStatus.put("battery", "LOW");
                        } catch (JSONException e) {
                        }
                        break;
                }

                this.statusStep = STATUS_STEP_GET_PRINTER_ID_FIRMWARE_VERSION;
                break;

            case BixolonPrinter.PROCESS_GET_PRINTER_ID:
                Bundle data = msg.getData();
                switch (this.statusStep) {
                    case STATUS_STEP_GET_PRINTER_ID_FIRMWARE_VERSION:
                        try {
                            this.mConnectedDeviceStatus.put("firmwareVersion", data.getString(BixolonPrinter.KEY_STRING_PRINTER_ID));
                        } catch (JSONException e) {
                        }

                        this.statusStep = STATUS_STEP_GET_PRINTER_ID_MANUFACTURER;
                        break;

                    case STATUS_STEP_GET_PRINTER_ID_MANUFACTURER:
                        try {
                            this.mConnectedDeviceStatus.put("manufacturer", data.getString(BixolonPrinter.KEY_STRING_PRINTER_ID));
                        } catch (JSONException e) {
                        }

                        this.statusStep = STATUS_STEP_GET_PRINTER_ID_PRINTER_MODEL;
                        break;

                    case STATUS_STEP_GET_PRINTER_ID_PRINTER_MODEL:
                        try {
                            this.mConnectedDeviceStatus.put("printerModel", data.getString(BixolonPrinter.KEY_STRING_PRINTER_ID));
                        } catch (JSONException e) {
                        }

                        this.statusStep = STATUS_STEP_GET_PRINTER_ID_CODE_PAGE;
                        break;

                    case STATUS_STEP_GET_PRINTER_ID_CODE_PAGE:
                        try {
                            this.mConnectedDeviceStatus.put("codePage", data.getString(BixolonPrinter.KEY_STRING_PRINTER_ID));
                        } catch (JSONException e) {
                        }

                        this.statusStep = STATUS_STEP_COMPLETE;
                        break;
                    case STATUS_STEP_GET_PRINTER_ID_MODEL_ID:
                        try {
                            this.mConnectedDeviceStatus.put("modelId", data.getString(BixolonPrinter.KEY_STRING_PRINTER_ID));
                        } catch (JSONException e) {
                        }

                        this.statusStep = STATUS_STEP_GET_PRINTER_ID_PRODUCT_SERIAL;
                        break;
                    case STATUS_STEP_GET_PRINTER_ID_PRODUCT_SERIAL:
                        try {
                            this.mConnectedDeviceStatus.put("productSerial", data.getString(BixolonPrinter.KEY_STRING_PRINTER_ID));
                        } catch (JSONException e) {
                        }

                        this.statusStep = STATUS_STEP_GET_PRINTER_ID_TYPE_ID;
                        break;
                    case STATUS_STEP_GET_PRINTER_ID_TYPE_ID:
                        try {
                            this.mConnectedDeviceStatus.put("typeId", data.getString(BixolonPrinter.KEY_STRING_PRINTER_ID));
                        } catch (JSONException e) {
                        }

                        this.statusStep = STATUS_STEP_COMPLETE;
                        break;
                }
                break;

            case BixolonPrinter.PROCESS_MSR_TRACK:
                Bundle bundle = msg.getData();

                isMsrTrackDatas = true;

                sendMsrTrackData(getMsrTrackData(bundle), true);
                break;
        }

        Log.d(TAG, "BixolonPrint.onMessageRead_END");

        if(!isMsrTrackDatas)
            this.getStatus();
    }

    private boolean startMsrReaderListener() {
        if(this.msrReaderCallbackContext != null) {
            this.cbContext.error("MSR Reader listener already started.");
            return false;
        }
        this.msrReaderCallbackContext = this.cbContext;
        //this.cbContext.success();
        return true;
    }

    private void sendMsrTrackData(JSONObject obj, boolean keepCallback) {
        if(msrReaderCallbackContext != null) {
            PluginResult result = new PluginResult(PluginResult.Status.OK, obj);
            result.setKeepCallback(keepCallback);
            this.msrReaderCallbackContext.sendPluginResult(result);
        }
    }

    private void stopMsrReaderListener() {
        this.sendMsrTrackData(new JSONObject(), false);
        this.msrReaderCallbackContext = null;
        //this.cbContext.success();
    }

    private boolean startConnectionListener() {
        if(this.connectionListenerCallbackContext != null) {
            this.cbContext.error(createConnectionData("Connection listener already started."));
            return false;
        }
        this.connectionListenerCallbackContext = this.cbContext;
        //this.cbContext.success(createConnectionData("Connection listener started."));
        return true;
    }

    private JSONObject createConnectionData(String message) {
        JSONObject obj = new JSONObject();

        String msg = "";

        if(message != null)
            msg = message;

        try {
            obj.put("isConnected", this.mIsConnected);
            obj.put("message", message);
        } catch(JSONException e) {
            Log.e(TAG, "BixolonPrint.createConnectionData: " + e.getMessage(), e);
        }

        return obj;
    }

    private void sendConnectionData() {
        if(this.connectionListenerCallbackContext != null) {
            PluginResult result = new PluginResult(PluginResult.Status.OK, createConnectionData(null));
            result.setKeepCallback(true);
            this.connectionListenerCallbackContext.sendPluginResult(result);
        }
    }

    private void stopConnectionListener() {
        this.sendConnectionData();
        this.connectionListenerCallbackContext = null;
        //this.cbContext.success("Connection listener stopped.");
    }

    private JSONObject getMsrTrackData(Bundle bundle) {
        JSONObject obj = new JSONObject();

        try {
            byte[] mTrack1Data = bundle.getByteArray(BixolonPrinter.KEY_STRING_MSR_TRACK1);
            byte[] mTrack2Data = bundle.getByteArray(BixolonPrinter.KEY_STRING_MSR_TRACK2);
            byte[] mTrack3Data = bundle.getByteArray(BixolonPrinter.KEY_STRING_MSR_TRACK3);

            if(mTrack1Data == null) {
                obj.put("msrTrack1", "");
            } else {
                obj.put("msrTrack1", new String(mTrack1Data));
            }

            if(mTrack2Data == null) {
                obj.put("msrTrack2", "");
            } else {
                obj.put("msrTrack2", new String(mTrack2Data));
            }

            if(mTrack3Data == null) {
                obj.put("msrTrack3", "");
            } else {
                obj.put("msrTrack3", new String(mTrack3Data));
            }

        } catch(JSONException e) {
            Log.e(TAG, "BixolonPrint.getMsrTrackData: " + e.getMessage(), e);
        }

        return obj;
    }

	/*         METODI ACCESSORI
	 ---------------------------------------*/

    /**
     * @param fontType
     * @param fontStyle
     * @return
     */
    @SuppressLint("DefaultLocale")
    private int getAttribute(String fontType, String fontStyle) {
        // setting attribute
        int attribute = 0;

        if (fontType != null) {
            if (fontType.toUpperCase().equals("A")) {
                attribute |= BixolonPrinter.TEXT_ATTRIBUTE_FONT_A;
            }
            if (fontType.toUpperCase().equals("B")) {
                attribute |= BixolonPrinter.TEXT_ATTRIBUTE_FONT_B;
            }
            if (fontType.toUpperCase().equals("C")) {
                attribute |= BixolonPrinter.TEXT_ATTRIBUTE_FONT_C;
            }
        }

        // TODO add multiple selection
        if (fontStyle != null) {
            if (fontStyle.toUpperCase().equals("UNDERLINE")) {
                attribute |= BixolonPrinter.TEXT_ATTRIBUTE_UNDERLINE1;
            }

            if (fontStyle.toUpperCase().equals("UNDERLINE2")) {
                attribute |= BixolonPrinter.TEXT_ATTRIBUTE_UNDERLINE2;
            }

            if (fontStyle.toUpperCase().equals("BOLD")) {
                attribute |= BixolonPrinter.TEXT_ATTRIBUTE_EMPHASIZED;
            }

            if (fontStyle.toUpperCase().equals("REVERSE")) {
                attribute |= BixolonPrinter.TEXT_ATTRIBUTE_REVERSE;
            }
        }

        return attribute;
    }

    /**
     * @param align
     * @return
     */
    @SuppressLint("DefaultLocale")
    private int getAlignment(String align) {
        int alignment = BixolonPrinter.ALIGNMENT_LEFT;
        if (align != null) {
            if (ALIGNMENT_LEFT.equals(align.toUpperCase())) {
                alignment = BixolonPrinter.ALIGNMENT_LEFT;
            } else if (ALIGNMENT_CENTER.equals(align.toUpperCase())) {
                alignment = BixolonPrinter.ALIGNMENT_CENTER;
            } else if (ALIGNMENT_RIGHT.equals(align.toUpperCase())) {
                alignment = BixolonPrinter.ALIGNMENT_RIGHT;
            }
        }

        return alignment;
    }

    /**
     * @param width
     * @param height
     * @return
     */
    private int getTextSize(int width, int height) {
        int size = 0;

        switch (width) {
            case 0:
                size = BixolonPrinter.TEXT_SIZE_HORIZONTAL1;
                break;
            case 1:
                size = BixolonPrinter.TEXT_SIZE_HORIZONTAL2;
                break;
            case 2:
                size = BixolonPrinter.TEXT_SIZE_HORIZONTAL3;
                break;
            case 3:
                size = BixolonPrinter.TEXT_SIZE_HORIZONTAL4;
                break;
            case 4:
                size = BixolonPrinter.TEXT_SIZE_HORIZONTAL5;
                break;
            case 5:
                size = BixolonPrinter.TEXT_SIZE_HORIZONTAL6;
                break;
            case 6:
                size = BixolonPrinter.TEXT_SIZE_HORIZONTAL7;
                break;
            case 7:
                size = BixolonPrinter.TEXT_SIZE_HORIZONTAL8;
                break;
        }

        switch (height) {
            case 0:
                size |= BixolonPrinter.TEXT_SIZE_VERTICAL1;
                break;
            case 1:
                size |= BixolonPrinter.TEXT_SIZE_VERTICAL2;
                break;
            case 2:
                size |= BixolonPrinter.TEXT_SIZE_VERTICAL3;
                break;
            case 3:
                size |= BixolonPrinter.TEXT_SIZE_VERTICAL4;
                break;
            case 4:
                size |= BixolonPrinter.TEXT_SIZE_VERTICAL5;
                break;
            case 5:
                size |= BixolonPrinter.TEXT_SIZE_VERTICAL6;
                break;
            case 6:
                size |= BixolonPrinter.TEXT_SIZE_VERTICAL7;
                break;
            case 7:
                size |= BixolonPrinter.TEXT_SIZE_VERTICAL8;
                break;
        }

        return size;
    }

    private final Handler mHandler = new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(Message msg) {
            switch (msg.what) {
                case BixolonPrinter.MESSAGE_BLUETOOTH_DEVICE_SET:
                    Log.d(TAG, "perform mHandler: MESSAGE_BLUETOOTH_DEVICE_SET");
                    if (msg.obj == null) {
                        BixolonPrint.this.cbContext.error("Printer not found");
                    } else {
                        @SuppressWarnings("unchecked")
                        final Set<BluetoothDevice> pairedDevices = (Set<BluetoothDevice>) msg.obj;
                        final String[] itemsAddr = new String[pairedDevices.size()];
                        final String[] itemsName = new String[pairedDevices.size()];

                        int index = 0;
                        for (BluetoothDevice device : pairedDevices) {
                            itemsAddr[index] = device.getAddress();
                            itemsName[index] = device.getName();
                            index++;
                        }

                        if (BixolonPrint.this.optAutoConnect) {
                            mConnectedDeviceAddress = itemsAddr[0];
                            //BixolonPrint.mBixolonPrinter.connect(itemsAddr[0]);
                            srvManager.connect(itemsAddr[0],false);
                        } else {
                            new AlertDialog.Builder(BixolonPrint.this.cordova.getActivity())
                                    .setTitle("Available printers")
                                    .setItems(itemsAddr, new DialogInterface.OnClickListener() {
                                        public void onClick(DialogInterface dialog, int which) {
                                            mConnectedDeviceAddress = itemsAddr[which];
                                            //BixolonPrint.mBixolonPrinter.connect(itemsAddr[which]);
                                            srvManager.connect(itemsAddr[which],false);
                                        }
                                    })
                                    .show();
                        }

                    }
                    return true;
                // END MESSAGE_BLUETOOTH_DEVICE_SET

                case BixolonPrinter.MESSAGE_STATE_CHANGE:
                    switch (msg.arg1) {
                        case BixolonPrinter.STATE_CONNECTED:
                            Log.d(TAG, "perform mHandler: MESSAGE_STATE_CHANGE STATE_CONNECTED");
                            BixolonPrint.this.onConnect();
                            break;

                        case BixolonPrinter.STATE_CONNECTING:
                            Log.d(TAG, "perform mHandler: MESSAGE_STATE_CHANGE STATE_CONNECTING");
                            break;

                        case BixolonPrinter.STATE_NONE:
                            Log.d(TAG, "perform mHandler: MESSAGE_STATE_CHANGE STATE_NONE");
                            // disconnect or connection error
                            BixolonPrint.this.onDisconnect();
                            break;
                    }
                    return true;
                // END MESSAGE_STATE_CHANGE

                case BixolonPrinter.MESSAGE_DEVICE_NAME:
                    Log.d(TAG, "perform mHandler: MESSAGE_DEVICE_NAME");
                    mConnectedDeviceName = msg.getData().getString(BixolonPrinter.KEY_STRING_DEVICE_NAME);
                    return true;
                // END MESSAGE_DEVICE_NAME

                case BixolonPrinter.MESSAGE_TOAST:
                    Log.d(TAG, "perform mHandler: MESSAGE_TOAST");
                    if (BixolonPrint.this.optToastMessage) {
                        Toast.makeText(BixolonPrint.this.cordova.getActivity(), msg.getData().getString(BixolonPrinter.KEY_STRING_TOAST), Toast.LENGTH_SHORT).show();
                    }
                    return true;
                // END MESSAGE_TOAST

                case BixolonPrinter.MESSAGE_PRINT_COMPLETE:
                    Log.d(TAG, "perform mHandler: MESSAGE_PRINT_COMPLETE");
                    BixolonPrint.this.onPrintComplete();
                    return true;
                // END MESSAGE_PRINT_COMPLETE

                case BixolonPrinter.MESSAGE_READ:
                    Log.d(TAG, "perform mHandler: MESSAGE_READ");
                    BixolonPrint.this.onMessageRead(msg);
                    return true;
                // END MESSAGE_PRINT_COMPLETE

            }
            return false;
        }
    });
}
