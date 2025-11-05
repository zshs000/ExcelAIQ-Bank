package com.zhoushuo.eaqb.excel.parser.biz.util;


import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;


@Slf4j
public class PresignedUrlDownloader {
    private static final CloseableHttpClient httpClient = HttpClientBuilder.create().build();

    /**
     * 最简单的下载方法
     */
    public static InputStream downloadFromUrl(String presignedUrl) throws IOException {
        HttpGet httpGet = new HttpGet(presignedUrl);

        return httpClient.execute(httpGet, response -> {
            if ( response.getStatusLine().getStatusCode()  != HttpStatus.SC_OK) {
                throw new IOException("下载失败，HTTP状态码: " +  response.getStatusLine().getStatusCode() );
            }
            return response.getEntity().getContent();
        });
    }

    /**
     * 直接下载为字节数组（适用于小文件）
     */
    public static byte[] downloadAsBytes(String presignedUrl) throws IOException {
        try (InputStream is = downloadFromUrl(presignedUrl)) {
            return is.readAllBytes();
        }
    }


}