package org.zywx.wbpalmstar.plugin.uexuploadermgr;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;

import javax.net.ssl.HttpsURLConnection;

import org.json.JSONObject;
import org.zywx.wbpalmstar.base.BUtility;
import org.zywx.wbpalmstar.engine.EBrowserView;
import org.zywx.wbpalmstar.engine.universalex.EUExBase;
import org.zywx.wbpalmstar.engine.universalex.EUExCallback;
import org.zywx.wbpalmstar.platform.certificates.HNetSSLSocketFactory;
import org.zywx.wbpalmstar.platform.certificates.HX509HostnameVerifier;
import org.zywx.wbpalmstar.platform.certificates.Http;
import org.zywx.wbpalmstar.widgetone.dataservice.WWidgetData;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;

public class EUExUploaderMgr extends EUExBase {

	public final static String KEY_APPVERIFY = "appverify";
	public final static String XMAS_APPID = "x-mas-app-id";
	private WWidgetData mCurWData;

	public static final String tag = "uexUploaderMgr_";
	private static final String F_CALLBACK_NAME_UPLOADSTATUS = "uexUploaderMgr.onStatus";
	private static final String F_CALLBACK_NAME_CREATEUPLOADER = "uexUploaderMgr.cbCreateUploader";

    public static final int F_FILE_TYPE_CREATE = 0;
    public static final int F_FILE_TYPE_UPLOAD = 1;
    
    public static final int TAG_MSG_CREATE = 1;
    public static final int TAG_MSG_UPLOAD = 2;
    public static final int TAG_MSG_CLOSE = 3;

    static final String SCRIPT_HEADER = "javascript:";

    private static final int TIME_OUT = 5 * 60 * 1000; // 超时时间
    private static final String CHARSET = "utf-8"; // 设置编码
    private static final String TAG_PARAMS_DATA = "data";
    private HashMap<Integer, EUExFormFile> objectMap;
    private HashMap<String, String> mHttpHead;
	private String mCertPassword = "";
	private String mCertPath = "";
	private boolean mHasCert = false;
    private String lastPercenttage = "";
    private long lastPercentTime=0;

	public EUExUploaderMgr(Context context, EBrowserView inParent) {
		super(context, inParent);
		objectMap = new HashMap<Integer, EUExFormFile>();
		mHttpHead = new HashMap<String, String>();
        if (inParent.getBrowserWindow()!=null) {
            mCurWData = getWidgetData(inParent);
        }
	}

    public void createUploader(String[] parm) {
        if(parm == null || parm.length < 2){
            errorCallback(0, 0, "error params!");
            return;
        }
        Message msg = new Message();
        msg.what = TAG_MSG_CREATE;
        msg.obj = this;
        Bundle bd = new Bundle();
        bd.putStringArray(TAG_PARAMS_DATA, parm);
        msg.setData(bd);
        mHandler.sendMessage(msg);
    }
    
    public void createUploaderMsg(String[] parm) {
        if (parm == null || parm.length < 2) {
            return;
        }
        String inOpCode = parm[0], inTargetAddress = parm[1];
        if (!BUtility.isNumeric(inOpCode)) {
            return;
        }
        if (objectMap.containsKey(Integer.parseInt(inOpCode))) {
            jsCallback(F_CALLBACK_NAME_CREATEUPLOADER,
                    Integer.parseInt(inOpCode), EUExCallback.F_C_INT,
                    EUExCallback.F_C_FAILED);
            return;
        }
        if (inTargetAddress == null || inTargetAddress.length() == 0
                || !(inTargetAddress.startsWith(BUtility.F_HTTP_PATH)
                	|| inTargetAddress.startsWith("https://"))) {
            jsCallback(F_CALLBACK_NAME_CREATEUPLOADER,
                    Integer.parseInt(inOpCode), EUExCallback.F_C_INT,
                    EUExCallback.F_C_FAILED);
            return;
        }

        objectMap.put(Integer.parseInt(inOpCode), new EUExFormFile(
                inTargetAddress, null));
        jsCallback(F_CALLBACK_NAME_CREATEUPLOADER, Integer.parseInt(inOpCode),
                EUExCallback.F_C_INT, EUExCallback.F_C_SUCCESS);
    }

    public void closeUploader(String[] parm) {
        if(parm == null || parm.length < 1){
            errorCallback(0, 0, "error params!");
            return;
        }
        Message msg = new Message();
        msg.what = TAG_MSG_CLOSE;
        msg.obj = this;
        Bundle bd = new Bundle();
        bd.putStringArray(TAG_PARAMS_DATA, parm);
        msg.setData(bd);
        mHandler.sendMessage(msg);
    }
    
    public void closeUploaderMsg(String[] parm) {
        if(parm == null || parm.length < 1){
            return;
        }
        String inOpCode = parm[0];
        if (!BUtility.isNumeric(inOpCode)) {
            return;
        }
        objectMap.remove(Integer.parseInt(inOpCode));
    }

    public void uploadFile(String[] parm) {
        if(parm == null || parm.length < 3){
            errorCallback(0, 0, "error params!");
            return;
        }
        Message msg = new Message();
        msg.what = TAG_MSG_UPLOAD;
        msg.obj = this;
        Bundle bd = new Bundle();
        bd.putStringArray(TAG_PARAMS_DATA, parm);
        msg.setData(bd);
        mHandler.sendMessage(msg);
    }
    
    public void uploadFileMsg(String[] parm) {
        if (parm == null || parm.length < 3) {
            return;
        }

        final String inOpCode = parm[0];
        String inFilePath = parm[1];
        final String inInputName = parm[2];
        int inCompress = 0;
        if (parm.length == 4) {
            inCompress = Integer.parseInt(parm[3]);
        }
        float inWith = -1;
        if (parm.length == 5) {
            inCompress = Integer.parseInt(parm[3]);
            if (!TextUtils.isEmpty(parm[4])) {
                inWith = Float.valueOf(parm[4]).floatValue();
            }
        }
        if (!BUtility.isNumeric(inOpCode)) {
            return;
        }
        if (inFilePath == null || inFilePath.length() == 0) {
            String js = SCRIPT_HEADER + "if(" + F_CALLBACK_NAME_UPLOADSTATUS
                    + "){" + F_CALLBACK_NAME_UPLOADSTATUS + "(" + inOpCode
                    + "," + 0 + "," + 0 + "," + "null,"
                    + EUExCallback.F_C_UpLoadError + ")}";
            onCallback(js);
            return;

        }
        if (mBrwView.getCurrentWidget() == null) {
            String js = SCRIPT_HEADER + "if(" + F_CALLBACK_NAME_UPLOADSTATUS
                    + "){" + F_CALLBACK_NAME_UPLOADSTATUS + "(" + inOpCode
                    + "," + 0 + "," + 0 + "," + "null,"
                    + EUExCallback.F_C_UpLoadError + ")}";
            onCallback(js);
            return;
        }
        inFilePath = BUtility.makeRealPath(
                BUtility.makeUrl(mBrwView.getCurrentUrl(), inFilePath),
                mBrwView.getCurrentWidget().m_widgetPath,
                mBrwView.getCurrentWidget().m_wgtType);
        if (inFilePath.startsWith(BUtility.F_FILE_SCHEMA)) {
            inFilePath = inFilePath.substring(BUtility.F_FILE_SCHEMA.length());
        }
        final EUExFormFile formFile = objectMap.get(Integer.parseInt(inOpCode));
        if (formFile.state == F_FILE_TYPE_CREATE) {
            formFile.state = F_FILE_TYPE_UPLOAD;
        } else {
            return;
        }
        try {

            if (inFilePath.startsWith("/")) {
                File file = new File(inFilePath);
                if (!file.exists()) {
                    String js = SCRIPT_HEADER + "if("
                            + F_CALLBACK_NAME_UPLOADSTATUS + "){"
                            + F_CALLBACK_NAME_UPLOADSTATUS + "(" + inOpCode
                            + "," + 0 + "," + 0 + "," + "null,"
                            + EUExCallback.F_C_UpLoadError + ")}";
                    onCallback(js);
                    return;
                }
                InputStream inputSteam = null;
                if (inCompress > 0) {
                    try {
                        inputSteam = compress(mContext, inFilePath, inCompress,
                                inWith);
                    } catch (OutOfMemoryError e) {
                        String js = SCRIPT_HEADER + "if("
                                + F_CALLBACK_NAME_UPLOADSTATUS + "){"
                                + F_CALLBACK_NAME_UPLOADSTATUS + "(" + inOpCode
                                + "," + 0 + "," + 0 + "," + "null,"
                                + EUExCallback.F_C_UpLoadError + ")}";
                        onCallback(js);
                        e.printStackTrace();
                        return;
                    } catch (IOException e) {
                        String js = SCRIPT_HEADER + "if("
                                + F_CALLBACK_NAME_UPLOADSTATUS + "){"
                                + F_CALLBACK_NAME_UPLOADSTATUS + "(" + inOpCode
                                + "," + 0 + "," + 0 + "," + "null,"
                                + EUExCallback.F_C_UpLoadError + ")}";
                        onCallback(js);
                        e.printStackTrace();
                        return;
                    }

                } else {
                    inputSteam = new FileInputStream(file);
                }

                formFile.setInputStream(inputSteam);
                formFile.m_filname = file.getName();
            } else {
                InputStream inputSteam = null;
                if (inCompress > 0) {
                    try {
                        inputSteam = compress(mContext, inFilePath, inCompress,
                                inWith);
                    } catch (OutOfMemoryError e) {
                        String js = SCRIPT_HEADER + "if("
                                + F_CALLBACK_NAME_UPLOADSTATUS + "){"
                                + F_CALLBACK_NAME_UPLOADSTATUS + "(" + inOpCode
                                + "," + 0 + "," + 0 + "," + "null,"
                                + EUExCallback.F_C_UpLoadError + ")}";
                        onCallback(js);
                        e.printStackTrace();
                        return;
                    } catch (IOException e) {
                        String js = SCRIPT_HEADER + "if("
                                + F_CALLBACK_NAME_UPLOADSTATUS + "){"
                                + F_CALLBACK_NAME_UPLOADSTATUS + "(" + inOpCode
                                + "," + 0 + "," + 0 + "," + "null,"
                                + EUExCallback.F_C_UpLoadError + ")}";
                        onCallback(js);
                        e.printStackTrace();
                        return;
                    }

                } else {
                    inputSteam = mContext.getAssets().open(inFilePath);
                }

                formFile.setInputStream(inputSteam);
                formFile.m_filname = inFilePath.substring(inFilePath
                        .lastIndexOf("/") + 1);
            }

            final UploadPercentage uploadPercentage = new UploadPercentage();

            new Thread("SoTowerMobile-uexUpload") {
                public void run() {
                    Uploader(formFile, uploadPercentage,
                            Integer.parseInt(inOpCode), inInputName);
                }
            }.start();
        } catch (Exception e) {
            String js = SCRIPT_HEADER + "if(" + F_CALLBACK_NAME_UPLOADSTATUS
                    + "){" + F_CALLBACK_NAME_UPLOADSTATUS + "(" + inOpCode
                    + "," + 0 + "," + 0 + "," + "null,"
                    + EUExCallback.F_C_UpLoadError + ")}";
            onCallback(js);
            e.printStackTrace();
        }
    }

    private String Uploader(EUExFormFile formFile,
            UploadPercentage uploadPercentage, int inOpCode, String inInputName) {

        InputStream fileIs = null;
        DataOutputStream outStream = null;
        HttpURLConnection conn = null;

        try {

            if (formFile != null) {
                if (formFile.m_inputStream == null) {
                    String js = SCRIPT_HEADER + "if("
                            + F_CALLBACK_NAME_UPLOADSTATUS + "){"
                            + F_CALLBACK_NAME_UPLOADSTATUS + "(" + inOpCode
                            + "," + 0 + "," + 0 + "," + "null,"
                            + EUExCallback.F_C_UpLoadError + ")}";
                    onCallback(js);
                    return null;
                }
                if (inInputName == null || inInputName.length() == 0) {
                    inInputName = "file";
                }
                String BOUNDARY = UUID.randomUUID().toString(); // 边界标识 随机生成
                String PREFIX = "--", LINE_END = "\r\n";
                String CONTENT_TYPE = "multipart/form-data"; // 内容类型

                URL url = new URL(formFile.getM_targetAddress());
				if (formFile.getM_targetAddress().startsWith(
						BUtility.F_HTTP_PATH)) {
					conn = (HttpURLConnection) url.openConnection();
				} else {
					conn = (HttpsURLConnection) url.openConnection();
					javax.net.ssl.SSLSocketFactory ssFact = null;
					if (mHasCert) {
						ssFact = Http.getSSLSocketFactoryWithCert(mCertPassword,
								mCertPath, mContext);
					} else {
						ssFact = new HNetSSLSocketFactory(null, null);
					}
					((HttpsURLConnection) conn).setSSLSocketFactory(ssFact);
					((HttpsURLConnection) conn)
							.setHostnameVerifier(new HX509HostnameVerifier());
				}
                String cookie = getCookie(formFile.getM_targetAddress());
                if (null != cookie) {
                    conn.setRequestProperty("Cookie", cookie);
                }

                conn.setReadTimeout(TIME_OUT);
                conn.setConnectTimeout(TIME_OUT);
                conn.setDoInput(true); // 允许输入流
                conn.setDoOutput(true); // 允许输出流
                conn.setChunkedStreamingMode(4096);
                conn.setUseCaches(false); // 不允许使用缓存
                conn.setRequestMethod("POST"); // 请求方式
                conn.setRequestProperty("Charset", CHARSET); // 设置编码
                conn.setRequestProperty("connection", "keep-alive");
                conn.setRequestProperty("Content-Type", CONTENT_TYPE
                        + ";boundary=" + BOUNDARY);
                addHeaders(conn);
				
				if (null != mCurWData) {
					conn.setRequestProperty(
							KEY_APPVERIFY,
							getAppVerifyValue(mCurWData,
									System.currentTimeMillis()));
					conn.setRequestProperty(XMAS_APPID,mCurWData.m_appId);
				}
				/**
                 * 当文件不为空，把文件包装并且上传
                 */
                DataOutputStream dos = new DataOutputStream(
                        conn.getOutputStream());
                StringBuffer sb = new StringBuffer();
                sb.append(PREFIX);
                sb.append(BOUNDARY);
                sb.append(LINE_END);
                /**
                 * 这里重点注意： name里面的值为服务器端需要key 只有这个key 才可以得到对应的文件
                 * filename是文件的名字，包含后缀名的 比如:abc.png
                 */

                sb.append("Content-Disposition: form-data; name=\""
                        + inInputName + "\"; filename=\"" + formFile.m_filname
                        + "\"" + LINE_END);
                sb.append("Content-Type: application/octet-stream; charset="
                        + CHARSET + LINE_END);
                sb.append(LINE_END);
                dos.write(sb.toString().getBytes());
                fileIs = formFile.m_inputStream;
                // int l;
                int upload = 0;
                int fileSize = fileIs.available();
                uploadPercentage.setFileSize(fileSize, inOpCode);
                byte[] bytes = new byte[4096];
                int len = 0;
                try {
                    while ((len = fileIs.read(bytes)) != -1) {
                        dos.write(bytes, 0, len);
                        upload += len;
                        uploadPercentage.sendMessage(upload);
                    }
                } catch (OutOfMemoryError e) {
                    String js = SCRIPT_HEADER + "if("
                            + F_CALLBACK_NAME_UPLOADSTATUS + "){"
                            + F_CALLBACK_NAME_UPLOADSTATUS + "(" + inOpCode
                            + "," + 0 + "," + 0 + "," + "null,"
                            + EUExCallback.F_C_UpLoadError + ")}";
                    onCallback(js);
                    return null;
                }

                dos.write(LINE_END.getBytes());
                byte[] end_data = (PREFIX + BOUNDARY + PREFIX + LINE_END)
                        .getBytes();
                dos.write(end_data);

                int res = conn.getResponseCode();
                if (res == 200) {
                    String js = SCRIPT_HEADER + "if("
                            + F_CALLBACK_NAME_UPLOADSTATUS + "){"
                            + F_CALLBACK_NAME_UPLOADSTATUS + "(" + inOpCode
                            + "," + uploadPercentage.fileSize + "," + 100 + ","
                            + "null," + EUExCallback.F_C_UpLoading + ")}";
                    onCallback(js);
                    InputStreamReader isReader = new InputStreamReader(  
                            conn.getInputStream(),CHARSET);
                    BufferedReader bufReader = new BufferedReader(isReader);
                    StringBuffer buffer = new StringBuffer();
                    String line = " ";
                    while ((line = bufReader.readLine()) != null){
                        buffer.append(line);
                    }

                    js = SCRIPT_HEADER + "if(" + F_CALLBACK_NAME_UPLOADSTATUS
                            + "){" + F_CALLBACK_NAME_UPLOADSTATUS + "("
                            + inOpCode + "," + uploadPercentage.fileSize + ","
                            + 100 + ",'" + buffer.toString()
                            + "'," + EUExCallback.F_C_FinishUpLoad + ")}";
                    onCallback(js);

                } else {
                    String js = SCRIPT_HEADER + "if("
                            + F_CALLBACK_NAME_UPLOADSTATUS + "){"
                            + F_CALLBACK_NAME_UPLOADSTATUS + "(" + inOpCode
                            + "," + 0 + "," + 0 + "," + "null,"
                            + EUExCallback.F_C_UpLoadError + ")}";
                    onCallback(js);
                }

                fileIs.close();
                dos.flush();
                dos.close();
                formFile.m_isUpLoaded = true;
            }

        } catch (MalformedURLException e) {
            String js = SCRIPT_HEADER + "if(" + F_CALLBACK_NAME_UPLOADSTATUS
                    + "){" + F_CALLBACK_NAME_UPLOADSTATUS + "(" + inOpCode
                    + "," + 0 + "," + 0 + "," + "null,"
                    + EUExCallback.F_C_UpLoadError + ")}";
            onCallback(js);
            e.printStackTrace();
        } catch (Exception e) {
            String js = SCRIPT_HEADER + "if(" + F_CALLBACK_NAME_UPLOADSTATUS
                    + "){" + F_CALLBACK_NAME_UPLOADSTATUS + "(" + inOpCode
                    + "," + 0 + "," + 0 + "," + "null,"
                    + EUExCallback.F_C_UpLoadError + ")}";
            onCallback(js);
            e.printStackTrace();
        } finally {

            try {
                if (fileIs != null) {
                    fileIs.close();
                }
                if (outStream != null) {
                    outStream.close();
                }
                if (conn != null) {
                    conn.disconnect();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            fileIs = null;
            outStream = null;
            conn = null;
        }

        return null;
    }
    
    private void addHeaders(HttpURLConnection mConnection) {
        if (null != mConnection) {
            Set<Entry<String, String>> entrys = mHttpHead.entrySet();
            for (Map.Entry<String, String> entry : entrys) {
                mConnection.setRequestProperty(entry.getKey(), entry.getValue());
            }
        }
    }

    public class UploadPercentage {
        int fileSize;
        int opCode = 0;
        String uploadPercentage = null;
        String callBack = null;
        DecimalFormat df = new DecimalFormat();

        public void setFileSize(int inFileSize, int inOpCode) {
            fileSize = inFileSize;
            df.setMaximumFractionDigits(2);
            df.setMinimumFractionDigits(0);
            opCode = inOpCode;
        }
        
        public void sendMessage(int msg) {
            String percentage = "0";
            if (fileSize * 100 < 0) {
                percentage = df.format(msg / (fileSize / 100));
            } else {
                percentage = df.format(msg * 100 / fileSize);
            }
            long currentTime=System.currentTimeMillis();
            if(!percentage.equals(lastPercenttage)&&
                    ((currentTime-lastPercentTime)>200//进度回调间隔为200ms,或者进度为100也进行回调
                            ||"100".equals(percentage)))
            {
                lastPercenttage=percentage;
                lastPercentTime=currentTime;
                String js = SCRIPT_HEADER + "if(" + F_CALLBACK_NAME_UPLOADSTATUS
                        + "){" + F_CALLBACK_NAME_UPLOADSTATUS + "(" + opCode + ","
                        + fileSize + "," + percentage + "," + "null,"
                        + EUExCallback.F_C_UpLoading + ")}";
                onCallback(js);
            }

        }
    }
    
    public void setHeaders(String[] params) {
        if (params.length < 2 || null == params) {
            return;
        }
        String opCode = params[0];
        String headJson = params[1];
        if(objectMap.get(Integer.parseInt(opCode)) != null) {
            try {
                JSONObject json = new JSONObject(headJson);
                Iterator<?> keys = json.keys();
                while (keys.hasNext()) {
                    String key = (String) keys.next();
                    String value = json.getString(key);
                    mHttpHead.put(key, value);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        
        }
    }

    @Override
    protected boolean clean() {
        return false;
    }

    private InputStream compress(Context m_eContext, String path, int compress,
            float with) throws OutOfMemoryError, IOException {
        FileDescriptor fileDescriptor = null;
        boolean isRes = false;
        if (!path.startsWith("/")) {
            AssetFileDescriptor assetFileDescriptor = m_eContext.getAssets()
                    .openFd(path);
            fileDescriptor = assetFileDescriptor.getFileDescriptor();
            isRes = true;
        } else {
            FileInputStream fis = new FileInputStream(new File(path));
            fileDescriptor = fis.getFD();
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        Bitmap source = BitmapFactory.decodeFileDescriptor(fileDescriptor,
                null, options);
        if (options.outHeight <= 0 || options.outWidth <= 0) {
            if (isRes) {
                return m_eContext.getAssets().open(path);
            } else {
                return new FileInputStream(new File(path));
            }

        }

        int quality = 0;
        if (compress == 1) {
            quality = 100;
        } else if (compress == 2) {
            quality = 75;
        } else if (compress == 3) {
            quality = 50;
        } else {
            quality = 25;
        }

        float max = with == -1 ? 640 : with;
        float src_w = options.outWidth;
        float scaleRate = 1;

        scaleRate = src_w / max;

        scaleRate = scaleRate > 1 ? scaleRate : 1;

        if (scaleRate != 1) {
            Bitmap dstbmp = null;
            ByteArrayOutputStream baos = new ByteArrayOutputStream(4096);
            options.inSampleSize = (int) scaleRate;
            options.inJustDecodeBounds = false;
            options.inInputShareable = true;
            options.inPurgeable = true;
            options.inPreferredConfig = Config.RGB_565;// 会失真，缩略图失真没事^_^

            source = BitmapFactory.decodeFileDescriptor(fileDescriptor, null,
                    options);
            if (source != null) {
                int srcWidth = source.getWidth();
                int srcHeight = source.getHeight();
                final float sacleRate = max / (float) srcWidth;
                if (sacleRate != 1) {
                	final int destWidth = (int) (srcWidth * sacleRate);
                	final int destHeight = (int) (srcHeight * sacleRate);
                	dstbmp = Bitmap.createScaledBitmap(source, destWidth,
                			destHeight, false);
                	if (source != null && !source.isRecycled()) {
                		source.recycle();
                	}
                } else {
                	dstbmp = source;
                }
                if (dstbmp.compress(CompressFormat.JPEG, quality, baos)) {
                    if (dstbmp != null && !dstbmp.isRecycled()) {
                        dstbmp.recycle();
                    }
                    return new ByteArrayInputStream(baos.toByteArray());
                } else {
                    baos.close();
                    if (isRes) {
                        return m_eContext.getAssets().open(path);
                    } else {
                        return new FileInputStream(new File(path));
                    }
                }
            } else {
                if (isRes) {
                    return m_eContext.getAssets().open(path);
                } else {
                    return new FileInputStream(new File(path));
                }
            }

        } else {
            if (isRes) {
                return m_eContext.getAssets().open(path);
            } else {
                return new FileInputStream(new File(path));
            }
        }

    }

    @Override
    public void onHandleMessage(Message msg) {
        if(msg == null){
            return;
        }
        String[] params = msg.getData().getStringArray(TAG_PARAMS_DATA);
        switch (msg.what) {
        case TAG_MSG_CREATE:
            createUploaderMsg(params);
            break;
        case TAG_MSG_UPLOAD:
            uploadFileMsg(params);
            break;
        case TAG_MSG_CLOSE:
            closeUploaderMsg(params);
        default:
            break;
        }
    }
    
	/**
	 * 添加验证头
	 * 
	 * @param curWData
	 *            当前widgetData
	 * @param timeStamp
	 *            当前时间戳
	 * @return
	 */
	private String getAppVerifyValue(WWidgetData curWData, long timeStamp) {
		String value = null;
		String md5 = getMD5Code(curWData.m_appId + ":" + curWData.m_appkey
				+ ":" + timeStamp);
		value = "md5=" + md5 + ";ts=" + timeStamp;
		return value;

	}

	private String getMD5Code(String value) {
		if (value == null) {
			value = "";
		}
		try {
			MessageDigest md = MessageDigest.getInstance("MD5");
			md.reset();
			md.update(value.getBytes());
			byte[] md5Bytes = md.digest();
			StringBuffer hexValue = new StringBuffer();
			for (int i = 0; i < md5Bytes.length; i++) {
				int val = ((int) md5Bytes[i]) & 0xff;
				if (val < 16)
					hexValue.append("0");
				hexValue.append(Integer.toHexString(val));
			}
			return hexValue.toString();
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		}

		return null;
	}
	/**
	 * plugin里面的子应用的appId和appkey都按照主应用为准
	 */
	private WWidgetData getWidgetData(EBrowserView view){
		WWidgetData widgetData = view.getCurrentWidget();
		String indexUrl=widgetData.m_indexUrl;
		Log.i("uexUploaderMgr", "m_indexUrl:"+indexUrl);
		if(widgetData.m_wgtType!=0){
			if(indexUrl.contains("widget/plugin")){
				return view.getRootWidget();
			}
		}
		return widgetData;
	}

}
