package com.zhoushuo.eaqb.oss.biz.util;

import com.zhoushuo.eaqb.oss.biz.constant.FileConstants;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FileTypeUtilTest {

    @Test
    void getFileType_imageFile_shouldReturnImageConstant() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "avatar.png",
                "image/png",
                "fake-image".getBytes(StandardCharsets.UTF_8)
        );

        assertEquals(FileConstants.FILE_TYPE_IMAGE, FileTypeUtil.getFileType(file));
    }

    @Test
    void getFileType_excelFile_shouldReturnExcelConstant() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "questions.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "fake-excel".getBytes(StandardCharsets.UTF_8)
        );

        assertEquals(FileConstants.FILE_TYPE_EXCEL, FileTypeUtil.getFileType(file));
    }
}
