package com.zhoushuo.eaqb.excel.parser.biz.util;


import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.commons.lang3.tuple.Pair;

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

    /**
     * 正确方案：返回流的同时，需要手动关闭的HttpResponse（交给调用者管理生命周期）
     * 目的：保持流式读取，不加载整个文件到内存，符合EasyExcel设计初衷
     */
    public static Pair<InputStream, CloseableHttpResponse> downloadWithResponse(String presignedUrl) throws IOException {
        HttpGet httpGet = new HttpGet(presignedUrl);
        CloseableHttpResponse response = httpClient.execute(httpGet);

        if (response.getStatusLine().getStatusCode() != HttpStatus.SC_OK) {
            // 状态码异常时，必须先关闭响应，避免泄漏
            response.close();
            throw new IOException("下载失败，HTTP状态码: " + response.getStatusLine().getStatusCode());
        }

        // 返回流和响应对象（流由响应对象持有，需要一起关闭）
        InputStream inputStream = response.getEntity().getContent();
        return Pair.of(inputStream, response);
    }


}