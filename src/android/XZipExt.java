/*
 Copyright 2012-2013, Polyvi Inc. (http://polyvi.github.io/openxface)
 This program is distributed under the terms of the GNU General Public License.

 This file is part of xFace.

 xFace is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 xFace is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with xFace.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.polyvi.xface.extension;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.json.JSONArray;
import org.json.JSONException;

import android.util.Log;

public class XZipExt extends CordovaPlugin {
    private enum ErrorCode {
        NONE,
        // 文件不存在.
        FILE_NOT_EXIST,
        // 压缩文件出错.
        COMPRESS_FILE_ERROR,
        // 解压文件出错.
        UNZIP_FILE_ERROR,
        // 文件路径错误
        FILE_PATH_ERROR,
        // 文件类型错误,不支持的文件类型
        FILE_TYPE_ERROR,
    }

    private static final String COMMAND_ZIP = "zip";
    private static final String COMMAND_ZIP_FILES = "zipFiles";
    private static final String COMMAND_UNZIP = "unzip";
    private final int BUFFER_LEN = 2048;

    @Override
    public boolean execute(String action, JSONArray args,
            CallbackContext callbackContext) throws JSONException {
        ErrorCode code = ErrorCode.NONE;
        if (COMMAND_ZIP.equals(action)) {
            try {
                boolean zipSuccess = zip(args.getString(0), args.getString(1));
                if (!zipSuccess) {
                    code = ErrorCode.FILE_PATH_ERROR;
                } else {
                    callbackContext.success();
                }
            } catch (FileNotFoundException e) {
                code = ErrorCode.FILE_NOT_EXIST;
            } catch (IOException e) {
                code = ErrorCode.COMPRESS_FILE_ERROR;
            } catch (IllegalArgumentException e) {
                code = ErrorCode.COMPRESS_FILE_ERROR;
            }
        } else if (COMMAND_ZIP_FILES.equals(action)) {
            try {
                boolean zipSuccess = zipFiles(args.getJSONArray(0), args.getString(1));
                if (!zipSuccess) {
                    code = ErrorCode.FILE_PATH_ERROR;
                } else {
                    callbackContext.success();
                }
            } catch (FileNotFoundException e) {
                code = ErrorCode.COMPRESS_FILE_ERROR;
            } catch (IOException e) {
                code = ErrorCode.COMPRESS_FILE_ERROR;
            } catch (IllegalArgumentException e) {
                code = ErrorCode.COMPRESS_FILE_ERROR;
            }
        } else if (COMMAND_UNZIP.equals(action)) {
            try {
                boolean zipSuccess = unzip(args.getString(0), args.getString(1));
                if (!zipSuccess) {
                    code = ErrorCode.FILE_PATH_ERROR;
                } else {
                    callbackContext.success();
                }
            } catch (FileNotFoundException e) {
                code = ErrorCode.COMPRESS_FILE_ERROR;
            } catch (IOException e) {
                code = ErrorCode.COMPRESS_FILE_ERROR;
            } catch (IllegalArgumentException e) {
                code = ErrorCode.COMPRESS_FILE_ERROR;
            }
        }
        callbackContext.error(code.toString());
        return true;
    }

    /**
     * zip压缩
     *
     * @param srcEntry            要压缩的源文件或文件夹绝对路径
     * @param destZipFile       压缩成的目标文件的绝对路径
     * @return                          压缩是否成功: true: 成功, false: 失败或者路径非法
     * @throws NullPointerException, FileNotFoundException, IOException,    IllegalArgumentException
     * */
    private boolean zip(String srcEntry, String destZipFile)
            throws NullPointerException, FileNotFoundException, IOException,    IllegalArgumentException {
        zipDir(srcEntry, destZipFile);
        return true;
    }

    private boolean isEmptyString(String str){
        if ((null == str)||(str.length() < 1)) {
            return true;
        }
        return false;
    }

    /**
     * 对目录或文件进行压缩
     *
     * @param srcFilePath     待压缩的目录的绝对路径
     * @param zipFileName   要压缩成的zip文件名的绝对路径
     * @throws NullPointerException, FileNotFoundException, IOException, IllegalArgumentException
     */
    private void zipDir(String srcFilePath, String zipFileName)
            throws NullPointerException, FileNotFoundException, IOException, IllegalArgumentException {
        if (isEmptyString(srcFilePath) || isEmptyString(zipFileName)) {
            throw new IllegalArgumentException();
        }
        File srcFile = new File(srcFilePath);
        if (!srcFile.exists()) {
            throw new FileNotFoundException();
        }
        //当要压缩的是一个文件时候，如果要压缩成的文件名和它同名，则抛出异常
        if (srcFile.isFile() && zipFileName.equals(srcFilePath)) {
            throw new IllegalArgumentException();
        }
        File zipFile = new File(zipFileName);
        File zipFileParent = zipFile.getParentFile();
        if (!zipFileParent.exists()) {
            zipFileParent.mkdirs();
        }
        ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile));
        String entryPath = "";
        if (srcFile.isDirectory()) {
            entryPath = srcFile.getName() + File.separator;
        }
        compressDir(srcFilePath, zos, entryPath);
        zos.close();
    }

    /**
     * 压缩一个文件或者文件夹
     *
     * @param srcFilePath        带压缩的文件或文件夹
     * @param zos                     zip输出流 {@link ZipOutputStream}
     * @param entryPath          要写入到zip文件中的文件或文件夹的路径
     * @throws IOException
     */
    private void compressDir(String srcFilePath, ZipOutputStream zos, String entryPath)
            throws IOException {
        File zipDir = new File(srcFilePath);
        String[] dirList = zipDir.list();
        if (null == dirList || 0 == dirList.length || zipDir.isFile()) {
            writeToZip(zipDir, zos, entryPath);
        } else {
            for (String pathName : dirList){
                File f = new File(zipDir, pathName);
                if (f.isDirectory()) {
                    String filePath = f.getPath();
                    compressDir(filePath, zos, entryPath + f.getName() + File.separator);
                    continue;
                }
                writeToZip(f, zos, entryPath);
            }
        }
    }

    /**
     * 将文件写入zip文件中
     *
     * @param zos                输出流
     * @param entryPath     要写入到zip文件中的文件或文件夹的路径
     * @throws IOException
     * */
    private void writeToZip(File srcFile, ZipOutputStream zos, String entryPath)
            throws IOException {
        ZipEntry anEntry = null;
        if (srcFile.isDirectory()) {
            anEntry = new ZipEntry(entryPath);
            zos.putNextEntry(anEntry);
            return;
        }
        anEntry = new ZipEntry(entryPath + srcFile.getName());
        zos.putNextEntry(anEntry);
        FileInputStream fis = new FileInputStream(srcFile);
        byte[] readBuffer = new byte[BUFFER_LEN];
        int bytesIn = 0;
        bytesIn = fis.read(readBuffer);
        while (bytesIn != -1) {
            zos.write(readBuffer, 0, bytesIn);
            bytesIn = fis.read(readBuffer);
        }
        fis.close();
        zos.closeEntry();
    }

    /**
     * 压缩多个可选文件方法
     *
     * @param srcEntries       要压缩的源文件列表，可以是文件也可以是文件夹
     * @param destZipFile     压缩成的目标文件，可以是test.zip也可以是a/b/c/test.zip
     * @return                        压缩是否成功，true：成功，false：失败或路径非法
     * @throws NullPointerException, FileNotFoundException, IOException, IllegalArgumentException,JSONException
     * */
    private boolean zipFiles(JSONArray srcEntries, String destZipFile)
            throws NullPointerException, FileNotFoundException, IOException, IllegalArgumentException,JSONException {

        String[] paths = new String[srcEntries.length()];
        for (int i = 0; i < srcEntries.length(); i++) {
            paths[i] = srcEntries.getString(i);
            if (null == paths[i]) {
                return false;
            }
            File srcFile = new File(paths[i]);
            if (!srcFile.exists()) {
                throw new FileNotFoundException();
            }
        }

        if (null == destZipFile) {
            return false;
        }
        zipFiles(paths, destZipFile);
        return true;
    }

    /**
     * 对多个目录或文件进行zip压缩
     *
     * @param srcFilePaths      待压缩的文件列表
     * @param zipFileName     要压缩成的zip文件名
     * @throws NullPointerException, FileNotFoundException, IOException, IllegalArgumentException
     */
    private void zipFiles(String[] srcFilePaths, String zipFileName)
            throws NullPointerException, FileNotFoundException, IOException, IllegalArgumentException {
        if (isEmptyString(zipFileName)) {
            throw new IllegalArgumentException();
        }

        for (String path:srcFilePaths) {
            if (isEmptyString(path)) {
                throw new IllegalArgumentException();
            }
            File srcFile = new File(path);
            if (!srcFile.exists()) {
                throw new FileNotFoundException();
            }
            //当要压缩的是一个文件时候，如果要压缩成的文件名和它同名，则抛出异常
            if (srcFile.isFile() && zipFileName.equals(path)) {
                throw new IllegalArgumentException();
            }
        }

        File zipFile = new File(zipFileName);
        File zipFileParent = zipFile.getParentFile();
        if (!zipFileParent.exists()) {
            zipFileParent.mkdirs();
        }

        ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile));
        for (String path:srcFilePaths) {
            File entry = new File(path);
            String entryPath = "";
            if (entry.isDirectory()) {
                entryPath = entry.getName() + File.separator;
            }
            compressDir(path, zos, entryPath);
        }
        zos.close();
    }


    /**
     * unzip解压缩方法(都限定在app的workspace下面)
     *
     * @param zipFilePath       要解压的源文件
     * @param destPath          要解压的目标路径
     * @return 解压缩是否成功，true：成功，false：失败或路径非法
     * */
    private boolean unzip(String zipFilePath, String destPath)
            throws FileNotFoundException, IOException {
        String srcPath = zipFilePath;
        String desPath = destPath;
        if (null == srcPath || null == desPath) {
            return false;
        }
        unzipFile(desPath, srcPath);
        return true;
    }

    /**
     * 解压zip文件
     *
     * @param targetPath          解压的目标路径
     * @param zipFilePath         zip包路径
     * @throws FileNotFoundException, IOException
     */
    private void unzipFile(String targetPath, String zipFilePath)
            throws FileNotFoundException, IOException {
        File zipFile = new File(zipFilePath);
        InputStream is = null;
        is = new FileInputStream(zipFile);
        if(!unzipFileFromStream(targetPath, is)) {
            throw new IOException();
        }
    }

    /**
     * 从输入流中读取zip文件数据进行解压
     *
     * @param targetPath          解压的目标路径
     * @param is                         源zip包输入流
     * @return   解压是否成功
     * @throws IOException
     */
    private boolean unzipFileFromStream(String targetPath, InputStream is)
            throws IOException {
        File dirFile = new File(targetPath + File.separator);
        if (!dirFile.exists()) {
            dirFile.mkdirs();
        }

        ZipInputStream zis = new ZipInputStream(is);
        ZipEntry entry = null;
        while (null != (entry = zis.getNextEntry())) {
            String zipFileName = entry.getName();
            if (entry.isDirectory()) {
                File zipFolder = new File(targetPath + File.separator
                        + zipFileName);
                if (!zipFolder.exists()) {
                    zipFolder.mkdirs();
                }
            } else {
                File file = new File(targetPath + File.separator + zipFileName);

                // 如果要解压的文件目标位置父目录不存在，创建对应目录
                File parent = file.getParentFile();
                if (!parent.exists()) {
                    parent.mkdirs();
                }

                FileOutputStream fos = null;
                try {
                    fos = new FileOutputStream(file);
                } catch (FileNotFoundException e) {
                    Log.d("Zip", "Can't write file: " + file.getAbsolutePath());
                    e.printStackTrace();
                    return false;
                }
                int readLen = 0;
                byte buffer[] = new byte[BUFFER_LEN];
                while (-1 != (readLen = zis.read(buffer))) {
                    fos.write(buffer, 0, readLen);
                }
                fos.close();
            }
        }
        zis.close();
        is.close();
        return true;
    }
}
