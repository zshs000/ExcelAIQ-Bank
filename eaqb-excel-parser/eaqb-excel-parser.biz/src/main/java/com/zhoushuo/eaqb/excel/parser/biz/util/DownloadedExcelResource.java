package com.zhoushuo.eaqb.excel.parser.biz.util;

import org.apache.http.client.methods.CloseableHttpResponse;

import java.io.IOException;
import java.io.InputStream;

/**
 * 封装下载后的 Excel 流及其底层 HTTP 响应，统一管理资源生命周期。
 */
public class DownloadedExcelResource implements AutoCloseable {
    private final InputStream inputStream;
    private final CloseableHttpResponse response;

    public DownloadedExcelResource(InputStream inputStream, CloseableHttpResponse response) {
        this.inputStream = inputStream;
        this.response = response;
    }

    public InputStream getInputStream() {
        return inputStream;
    }

    @Override
    public void close() throws IOException {
        IOException closeException = null;
        try {
            if (inputStream != null) {
                inputStream.close();
            }
        } catch (IOException ex) {
            closeException = ex;
        }

        try {
            if (response != null) {
                response.close();
            }
        } catch (IOException ex) {
            if (closeException == null) {
                closeException = ex;
            } else {
                closeException.addSuppressed(ex);
            }
        }

        if (closeException != null) {
            throw closeException;
        }
    }
}
