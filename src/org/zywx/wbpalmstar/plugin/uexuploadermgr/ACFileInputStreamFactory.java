package org.zywx.wbpalmstar.plugin.uexuploadermgr;

import android.content.Context;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.text.TextUtils;

import org.zywx.wbpalmstar.base.BDebug;

import java.io.File;
import java.io.FileInputStream;

/**
 * File Description: 用于处理FileProvider等路径
 * <p>
 * Created by zhangyipeng with Email: sandy1108@163.com at Date: 2023/12/28.
 */
public class ACFileInputStreamFactory {

    private static final String TAG = "ACFileInputStreamFactor";

    public static FileInputStream create(Context context, String filePath) {
        if (TextUtils.isEmpty(filePath)) {
            return null;
        }
        FileInputStream finalFis = null;
        try {
            // content://开头的
            if (filePath.startsWith("content://")) {
                Uri fileUri = Uri.parse(filePath);
                ParcelFileDescriptor inputPfd = context.getContentResolver().openFileDescriptor(fileUri, "rw");
                finalFis = new FileInputStream(inputPfd.getFileDescriptor());
            } else {
                finalFis = new FileInputStream(new File(filePath));
            }
        } catch (Exception e) {
            BDebug.w(TAG, "create FileInputStream error", e);
        }
        return finalFis;
    }

}
