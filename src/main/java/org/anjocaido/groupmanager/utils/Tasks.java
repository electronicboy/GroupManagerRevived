/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.anjocaido.groupmanager.utils;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;

import org.anjocaido.groupmanager.GroupManager;
import org.anjocaido.groupmanager.data.Group;

/**
 *
 * @author gabrielcouto
 */
public abstract class Tasks {

    /**
     * Gets the exception stack trace as a string.
     *
     * @param exception
     * @return stack trace as a string
     */
    public static String getStackTraceAsString(Exception exception) {

        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        exception.printStackTrace(pw);
        return sw.toString();
    }

    public static void copy(InputStream src, File dst) throws IOException {

        // Transfer bytes from in to out
        try (OutputStream out = new FileOutputStream(dst)) {
            // Transfer bytes from in to out
            byte[] buf = new byte[1024];
            int len;
            while ((len = src.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
        }
        try {
            src.close();
        } catch (Exception ignored) {
        }
    }

    public static void copy(File src, File dst) throws IOException {

        InputStream in = new FileInputStream(src);
        copy(in, dst);
    }

    /**
     * Appends a string to a file
     *
     * @param data
     * @param file
     * @throws java.io.IOException
     */
    public static void appendStringToFile(String data, String file) throws IOException {

        FileWriter outStream = new FileWriter("." + System.getProperty("file.separator") + file, true);

        try (BufferedWriter out = new BufferedWriter(outStream)) {
            out.append(new SimpleDateFormat("yyyy-MM-dd HH-mm").format(System.currentTimeMillis()));
            out.append(System.getProperty("line.separator"));
            out.append(data);
            out.append(System.getProperty("line.separator"));
        }
    }

    public static void removeOldFiles(GroupManager gm, File folder) {

        if (folder.isDirectory()) {
            long oldTime = System.currentTimeMillis() - (((long) gm.getGMConfig().getBackupDuration() * 60 * 60) * 1000);
            if (folder.listFiles() == null)
                return;
            File[] files = folder.listFiles();
            if (files == null)
                return;

            for (File olds : files) {
                if (olds.isFile()) {
                    if (olds.lastModified() < oldTime) {
                        try {
                            //noinspection ResultOfMethodCallIgnored
                            olds.delete();
                        } catch (Exception ignored) {
                        }
                    }
                }
            }
        }
    }

    public static String getDateString() {

        GregorianCalendar now = new GregorianCalendar();
        String date = "";
        date += now.get(Calendar.DAY_OF_MONTH);
        date += "-";
        date += now.get(Calendar.HOUR);
        date += "-";
        date += now.get(Calendar.MINUTE);
        return date;
    }

    public static String getStringListInString(List<String> list) {

        if (list == null) {
            return "";
        }
        return String.join(", ", list);

        /* - Old code, New one should work.
        for (int i = 0; i < list.size(); i++) {

            result += list.get(i);
            if (i < list.size() - 1) {
                result += ", ";
            }
        } */
    }

    public static String getStringArrayInString(String[] list) {

        if (list == null) {
            return "";
        }

        return String.join(", ", (CharSequence[]) list);
        /*
        String result = "";
        for (int i = 0; i < list.length; i++) {
            result += list[i];
            if (i < ((list.length) - 1)) {
                result += ", ";
            }
        } */
    }

    public static String getGroupListInString(List<Group> list) {

        if (list == null) {
            return "";
        }
        String result = "";
        for (int i = 0; i < list.size(); i++) {
            result += list.get(i).getName();
            if (i < list.size() - 1) {
                result += ", ";
            }
        }
        return result;
    }

    public static String join(String[] arr, String separator) {

        if (arr.length == 0) {
            return "";
        }

        /* String out = arr[0];
        for (int i = 1; i < arr.length; i++) {
            out += separator + arr[i];
        } */

        return String.join(separator, (CharSequence[]) arr);
    }

}
