package com.example.scparchive;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Locale;

final class ArchiveNet {
    static byte[] request(String url, int maxBytes) throws Exception {
        if (!url.startsWith("https://")) throw new SecurityException("Only HTTPS sources are allowed");
        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
        con.setConnectTimeout(15000);
        con.setReadTimeout(45000);
        con.setRequestProperty("User-Agent", "SCP-Archive-Android/1.0 (non-commercial reader)");
        con.setInstanceFollowRedirects(true);
        try {
            if (con.getResponseCode() != 200) throw new java.io.IOException("HTTP " + con.getResponseCode());
            int contentLength = con.getContentLength();
            if (contentLength > maxBytes) throw new java.io.IOException("Download exceeds size limit");
            try (InputStream in = con.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buf = new byte[32768]; int n;
                while ((n=in.read(buf))!=-1) {
                    if (out.size() + n > maxBytes) throw new java.io.IOException("Download exceeds size limit");
                    out.write(buf,0,n);
                }
                return out.toByteArray();
            }
        } finally { con.disconnect(); }
    }
    static JSONObject json(String url, int maxBytes) throws Exception {
        return new JSONObject(new String(request(url, maxBytes), java.nio.charset.StandardCharsets.UTF_8));
    }
    static File imageFile(File root, String url) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(url.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        StringBuilder name = new StringBuilder();
        for (byte b:hash) name.append(String.format(Locale.US, "%02x", b & 255));
        return new File(root, name + ".img");
    }
    static File cacheImage(File root, String url) throws Exception {
        if (url == null || url.isEmpty()) return null;
        if (!root.exists() && !root.mkdirs()) throw new java.io.IOException("Cannot make image directory");
        File cached = imageFile(root, url);
        if (cached.length() > 0) return cached;
        byte[] data = request(url, 10 * 1024 * 1024);
        BitmapFactory.Options probe = new BitmapFactory.Options();
        probe.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, probe);
        if (probe.outWidth <= 0 || probe.outHeight <= 0)
            throw new java.io.IOException("Unsupported image format");
        File temporary = new File(root, cached.getName() + ".part");
        try (FileOutputStream out = new FileOutputStream(temporary)) { out.write(data); }
        if (!temporary.renameTo(cached)) { temporary.delete(); throw new java.io.IOException("Cannot save image"); }
        return cached;
    }
    static Bitmap bitmap(File image, int maxWidth) {
        if (image == null || !image.exists()) return null;
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(image.getAbsolutePath(), bounds);
        int sample=1;
        while (bounds.outWidth/sample > maxWidth*2 && sample < 32) sample*=2;
        BitmapFactory.Options opt = new BitmapFactory.Options(); opt.inSampleSize=sample;
        return BitmapFactory.decodeFile(image.getAbsolutePath(), opt);
    }
}
