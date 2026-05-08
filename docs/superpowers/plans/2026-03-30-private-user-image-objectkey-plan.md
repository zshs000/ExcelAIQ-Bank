


> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Refactor user image uploads to persist `objectKey`, make image reads private via presigned view URLs, and split OSS presign APIs by business scene.

**Architecture:** OSS keeps a single presign capability but exposes two scene-specific service methods: Excel download and image view. User service stores only `avatarObjectKey` and `backgroundImgObjectKey`, then signs image view URLs when returning current user profile data.

**Tech Stack:** Spring Boot, OpenFeign, MyBatis XML, JUnit 5, Mockito

---

### Task 1: Update OSS presign API contracts

**Files:**
- Modify: `eaqb-oss/eaqb-oss-api/src/main/java/com/zhoushuo/eaqb/oss/api/FileFeignApi.java`
- Modify: `eaqb-oss/eaqb-oss-biz/src/main/java/com/zhoushuo/eaqb/oss/biz/controller/FileController.java`
- Modify: `eaqb-oss/eaqb-oss-biz/src/main/java/com/zhoushuo/eaqb/oss/biz/service/FileService.java`
- Modify: `eaqb-oss/eaqb-oss-biz/src/main/java/com/zhoushuo/eaqb/oss/biz/service/impl/FileServiceImpl.java`
- Modify: `eaqb-oss/eaqb-oss-biz/src/test/java/com/zhoushuo/eaqb/oss/biz/service/impl/FileServiceImplTest.java`

- [ ] Step 1: Write failing tests for image view URL and Excel download URL methods.
- [ ] Step 2: Run OSS service tests to confirm the old contract fails the new expectations.
- [ ] Step 3: Replace the old generic presign endpoint with explicit Excel/image endpoints.
- [ ] Step 4: Re-run OSS service tests and make them pass.

### Task 2: Unify OSS strategy return semantics on objectKey

**Files:**
- Modify: `eaqb-oss/eaqb-oss-biz/src/main/java/com/zhoushuo/eaqb/oss/biz/strategy/FileStrategy.java`
- Modify: `eaqb-oss/eaqb-oss-biz/src/main/java/com/zhoushuo/eaqb/oss/biz/strategy/impl/MinioFileStrategy.java`
- Modify: `eaqb-oss/eaqb-oss-biz/src/main/java/com/zhoushuo/eaqb/oss/biz/strategy/impl/AliyunOSSFileStrategy.java`
- Modify: `eaqb-oss/eaqb-oss-biz/src/test/java/com/zhoushuo/eaqb/oss/biz/strategy/impl/MinioFileStrategyTest.java`

- [ ] Step 1: Write failing strategy tests asserting avatar/background uploads return objectKey.
- [ ] Step 2: Run strategy tests to verify they fail for the expected reason.
- [ ] Step 3: Change strategies to upload by objectKey and return objectKey only.
- [ ] Step 4: Re-run strategy tests and keep them green.

### Task 3: Switch Excel RPC to explicit Excel download URL method

**Files:**
- Modify: `eaqb-excel-parser/eaqb-excel-parser.biz/src/main/java/com/zhoushuo/eaqb/excel/parser/biz/rpc/OssRpcService.java`
- Modify: `eaqb-excel-parser/eaqb-excel-parser.biz/src/main/java/com/zhoushuo/eaqb/excel/parser/biz/service/impl/ExcelFileServiceImpl.java`
- Modify: `eaqb-excel-parser/eaqb-excel-parser.biz/src/test/java/com/zhoushuo/eaqb/excel/parser/biz/rpc/OssRpcServiceTest.java`
- Modify: `eaqb-excel-parser/eaqb-excel-parser.biz/src/test/java/com/zhoushuo/eaqb/excel/parser/biz/service/impl/ExcelFileServiceImplTest.java`

- [ ] Step 1: Update tests to expect `getExcelDownloadUrl`.
- [ ] Step 2: Run focused excel parser tests to verify red state.
- [ ] Step 3: Replace old RPC calls with the explicit Excel download method.
- [ ] Step 4: Re-run focused excel parser tests.

### Task 4: Persist user image objectKeys and expose current profile with signed URLs

**Files:**
- Create: `eaqb-user/eaqb-user-api/src/main/java/com/zhoushuo/eaqb/user/dto/resp/CurrentUserProfileRspDTO.java`
- Modify: `eaqb-user/eaqb.user.biz/src/main/java/com/zhoushuo/eaqb/user/biz/domain/dataobject/UserDO.java`
- Modify: `eaqb-user/eaqb.user.biz/src/main/resources/mapper/UserDOMapper.xml`
- Modify: `eaqb-user/eaqb.user.biz/src/main/java/com/zhoushuo/eaqb/user/biz/rpc/OssRpcService.java`
- Modify: `eaqb-user/eaqb.user.biz/src/main/java/com/zhoushuo/eaqb/user/biz/service/UserService.java`
- Modify: `eaqb-user/eaqb.user.biz/src/main/java/com/zhoushuo/eaqb/user/biz/service/impl/UserServiceImpl.java`
- Modify: `eaqb-user/eaqb.user.biz/src/main/java/com/zhoushuo/eaqb/user/biz/controller/UserProfileController.java`
- Modify: `eaqb-user/eaqb.user.biz/src/test/java/com/zhoushuo/eaqb/user/biz/service/impl/UserServiceImplTest.java`

- [ ] Step 1: Write failing user service tests for storing image objectKeys and signing URLs in current profile response.
- [ ] Step 2: Run focused user tests and verify the failures.
- [ ] Step 3: Update user domain, mapper, RPC, service, and controller code to use objectKey persistence and signed image URLs.
- [ ] Step 4: Re-run focused user tests until green.

### Task 5: Final verification

**Files:**
- Modify: `docs/superpowers/plans/2026-03-30-private-user-image-objectkey-plan.md`

- [ ] Step 1: Run targeted Maven test commands for `eaqb-oss`, `eaqb-user`, and `eaqb-excel-parser`.
- [ ] Step 2: Review diffs for contract consistency and note any runtime prerequisites.
