# OSS Upload Boundary Refactor Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace implicit OSS path inference with explicit upload endpoints, make Excel uploads create a formal `UPLOADING` record before remote upload, and move image uploads to fixed avatar/background slots.

**Architecture:** The refactor keeps OSS as a storage-focused service but shifts naming control back to business services. `excel-parser` will own `fileId` and object name generation, `user` will own local image validation and fixed slot selection, and `oss-service` will only validate type, read `userId` from context, and upload to fixed internal paths.

**Tech Stack:** Spring Boot, OpenFeign, MyBatis XML mapper, MinIO/Aliyun OSS strategies, JUnit/Mockito

---

## File Map

### OSS API / Service

- Modify: `eaqb-oss/eaqb-oss-api/src/main/java/com/zhoushuo/eaqb/oss/api/FileFeignApi.java`
- Modify: `eaqb-oss/eaqb-oss-biz/src/main/java/com/zhoushuo/eaqb/oss/biz/controller/FileController.java`
- Modify: `eaqb-oss/eaqb-oss-biz/src/main/java/com/zhoushuo/eaqb/oss/biz/service/FileService.java`
- Modify: `eaqb-oss/eaqb-oss-biz/src/main/java/com/zhoushuo/eaqb/oss/biz/service/impl/FileServiceImpl.java`
- Modify: `eaqb-oss/eaqb-oss-biz/src/main/java/com/zhoushuo/eaqb/oss/biz/strategy/FileStrategy.java`
- Modify: `eaqb-oss/eaqb-oss-biz/src/main/java/com/zhoushuo/eaqb/oss/biz/strategy/impl/MinioFileStrategy.java`
- Modify: `eaqb-oss/eaqb-oss-biz/src/main/java/com/zhoushuo/eaqb/oss/biz/strategy/impl/AliyunOSSFileStrategy.java`
- Create: `eaqb-oss/eaqb-oss-biz/src/main/java/com/zhoushuo/eaqb/oss/biz/constant/ObjectPathConstants.java`
- Test: `eaqb-oss/eaqb-oss-biz/src/test/java/com/zhoushuo/eaqb/oss/biz/strategy/impl/MinioFileStrategyTest.java`

### Excel Upload

- Modify: `eaqb-excel-parser/eaqb-excel-parser.biz/src/main/java/com/zhoushuo/eaqb/excel/parser/biz/rpc/OssRpcService.java`
- Modify: `eaqb-excel-parser/eaqb-excel-parser.biz/src/main/java/com/zhoushuo/eaqb/excel/parser/biz/service/impl/ExcelFileServiceImpl.java`
- Modify: `eaqb-excel-parser/eaqb-excel-parser.biz/src/main/resources/mapper/FileInfoDOMapper.xml`
- Test: `eaqb-excel-parser/eaqb-excel-parser.biz/src/test/java/com/zhoushuo/eaqb/excel/parser/biz/service/impl/ExcelFileServiceImplTest.java`

### User Image Upload

- Modify: `eaqb-user/eaqb.user.biz/src/main/java/com/zhoushuo/eaqb/user/biz/rpc/OssRpcService.java`
- Modify: `eaqb-user/eaqb.user.biz/src/main/java/com/zhoushuo/eaqb/user/biz/service/impl/UserServiceImpl.java`
- Create: `eaqb-user/eaqb.user.biz/src/main/java/com/zhoushuo/eaqb/user/biz/util/ImageUploadValidator.java`
- Test: `eaqb-user/eaqb.user.biz/src/test/java/com/zhoushuo/eaqb/user/biz/service/impl/UserServiceImplTest.java`

### Shared Verification

- Run targeted Maven tests for `oss`, `excel-parser`, `user`.

## Task 1: Refactor OSS API To Explicit Upload Endpoints

**Files:**
- Modify: `eaqb-oss/eaqb-oss-api/src/main/java/com/zhoushuo/eaqb/oss/api/FileFeignApi.java`
- Modify: `eaqb-oss/eaqb-oss-biz/src/main/java/com/zhoushuo/eaqb/oss/biz/controller/FileController.java`
- Modify: `eaqb-oss/eaqb-oss-biz/src/main/java/com/zhoushuo/eaqb/oss/biz/service/FileService.java`
- Modify: `eaqb-oss/eaqb-oss-biz/src/main/java/com/zhoushuo/eaqb/oss/biz/service/impl/FileServiceImpl.java`
- Modify: `eaqb-oss/eaqb-oss-biz/src/main/java/com/zhoushuo/eaqb/oss/biz/strategy/FileStrategy.java`
- Modify: `eaqb-oss/eaqb-oss-biz/src/main/java/com/zhoushuo/eaqb/oss/biz/strategy/impl/MinioFileStrategy.java`
- Modify: `eaqb-oss/eaqb-oss-biz/src/main/java/com/zhoushuo/eaqb/oss/biz/strategy/impl/AliyunOSSFileStrategy.java`
- Create: `eaqb-oss/eaqb-oss-biz/src/main/java/com/zhoushuo/eaqb/oss/biz/constant/ObjectPathConstants.java`
- Test: `eaqb-oss/eaqb-oss-biz/src/test/java/com/zhoushuo/eaqb/oss/biz/strategy/impl/MinioFileStrategyTest.java`

- [ ] Step 1: Add failing OSS strategy tests for explicit object names and fixed avatar/background slots.
- [ ] Step 2: Update `FileStrategy` contract to accept explicit object names instead of generating random names internally.
- [ ] Step 3: Implement OSS path constants and explicit upload methods in controller/service/api.
- [ ] Step 4: Update MinIO/Aliyun strategy implementations to validate type and upload to fixed paths.
- [ ] Step 5: Run OSS-focused tests or compile checks.

## Task 2: Make Excel Upload Create Formal Records Before Remote Upload

**Files:**
- Modify: `eaqb-excel-parser/eaqb-excel-parser.biz/src/main/java/com/zhoushuo/eaqb/excel/parser/biz/rpc/OssRpcService.java`
- Modify: `eaqb-excel-parser/eaqb-excel-parser.biz/src/main/java/com/zhoushuo/eaqb/excel/parser/biz/service/impl/ExcelFileServiceImpl.java`
- Modify: `eaqb-excel-parser/eaqb-excel-parser.biz/src/main/resources/mapper/FileInfoDOMapper.xml`
- Test: `eaqb-excel-parser/eaqb-excel-parser.biz/src/test/java/com/zhoushuo/eaqb/excel/parser/biz/service/impl/ExcelFileServiceImplTest.java`

- [ ] Step 1: Add failing Excel upload tests for `UPLOADING` initialization and `UPLOAD_FAILED` fallback.
- [ ] Step 2: Extend mapper/status handling so upload stage distinguishes `UPLOADING`, `UPLOAD_FAILED`, and `UPLOADED`.
- [ ] Step 3: Change Excel upload flow to allocate `fileId`, insert `t_file_info(status=UPLOADING)`, then call `uploadExcel(file, "{fileId}.xlsx")`.
- [ ] Step 4: Update success/failure handling to fill `objectKey` and final upload status correctly.
- [ ] Step 5: Run Excel upload tests.

## Task 3: Move User Image Uploads To Local Validation + Fixed Slots

**Files:**
- Create: `eaqb-user/eaqb.user.biz/src/main/java/com/zhoushuo/eaqb/user/biz/util/ImageUploadValidator.java`
- Modify: `eaqb-user/eaqb.user.biz/src/main/java/com/zhoushuo/eaqb/user/biz/rpc/OssRpcService.java`
- Modify: `eaqb-user/eaqb.user.biz/src/main/java/com/zhoushuo/eaqb/user/biz/service/impl/UserServiceImpl.java`
- Test: `eaqb-user/eaqb.user.biz/src/test/java/com/zhoushuo/eaqb/user/biz/service/impl/UserServiceImplTest.java`

- [ ] Step 1: Add failing user service tests for avatar/background calling dedicated RPC methods and rejecting invalid images locally.
- [ ] Step 2: Implement `ImageUploadValidator` with non-empty, size, extension, and magic-number/content-type checks.
- [ ] Step 3: Replace generic image upload calls with `uploadAvatar` / `uploadBackground`.
- [ ] Step 4: Keep user table update logic unchanged apart from the new upload methods and validation.
- [ ] Step 5: Run user service tests.

## Task 4: Verify End-To-End Contract Consistency

**Files:**
- Modify if needed: affected tests and constants discovered during integration

- [ ] Step 1: Search for remaining callers of old generic OSS upload API and remove them.
- [ ] Step 2: Run targeted Maven test suites for `eaqb-oss`, `eaqb-excel-parser`, and `eaqb-user`.
- [ ] Step 3: Run a compile pass if any module-specific tests are unavailable or too narrow.
- [ ] Step 4: Summarize changed contract points for the final handoff.
