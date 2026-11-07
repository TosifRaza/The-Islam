package com.ikp.dev.app.data.remote;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** Resolves a verse to its publicly hosted Al Quran Cloud recitation stream. */
public final class QuranAudioSource {

    private static final String API_BASE = "https://api.alquran.cloud/v1/ayah/";
    private static final String CDN_BASE = "https://cdn.islamic.network/quran/audio-surah/128/";
    private static final String EDITION = "ar.alafasy";
    private static final String CDN_HOST = "cdn.islamic.network";

    private QuranAudioSource() {
    }

    public static String surahAudioUrl(int surahId) {
        if (surahId < 1 || surahId > 114) {
            throw new IllegalArgumentException("Invalid Surah number");
        }
        return CDN_BASE + EDITION + "/" + surahId + ".mp3";
    }

    public static String resolveAyahUrl(int surahId, int ayahNumber)
            throws IOException, JSONException {
        if (surahId < 1 || surahId > 114 || ayahNumber < 1) {
            throw new IllegalArgumentException("Invalid Quran reference");
        }

        URL endpoint = new URL(API_BASE + surahId + ":" + ayahNumber + "/" + EDITION);
        HttpURLConnection connection = (HttpURLConnection) endpoint.openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(15000);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("User-Agent", "The-Islam-Android/0.5.0");
        try {
            int status = connection.getResponseCode();
            InputStream stream = status >= 200 && status < 300
                    ? connection.getInputStream() : connection.getErrorStream();
            String response = readBody(stream);
            if (status < 200 || status >= 300) {
                throw new IOException("Audio service returned HTTP " + status);
            }

            JSONObject envelope = new JSONObject(response);
            if (envelope.optInt("code") != 200) {
                throw new IOException("Audio service did not return a recitation");
            }
            String audioUrl = envelope.getJSONObject("data").getString("audio");
            URL streamUrl = new URL(audioUrl);
            if (!"https".equalsIgnoreCase(streamUrl.getProtocol())
                    || !CDN_HOST.equalsIgnoreCase(streamUrl.getHost())) {
                throw new IOException("Audio service returned an unexpected stream host");
            }
            return streamUrl.toString();
        } finally {
            connection.disconnect();
        }
    }

    private static String readBody(InputStream stream) throws IOException {
        if (stream == null) return "";
        StringBuilder body = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) body.append(line);
        }
        return body.toString();
    }
}
