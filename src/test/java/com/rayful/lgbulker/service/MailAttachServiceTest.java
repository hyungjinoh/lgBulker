package com.rayful.lgbulker.service;

import com.rayful.lgbulker.vo.LGFileMailVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.*;
import java.lang.reflect.Method;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MailAttachServiceTest {

    @InjectMocks
    private MailAttachService mailAttachService;

    @Mock
    private LargeSizeFileDownloadService largeSizeFileDownloadService;

    @BeforeEach
    void setUp() {
        // 필드 주입 (테스트 전용 이미지 저장 경로)
        ReflectionTestUtils.setField(mailAttachService, "IMAGEFILE_DIR", "D:/images");
    }

    @Test
    void test_processBodyLinks_withImageAndDownloadLink() throws Exception {
        // given: 본문 HTML에 이미지와 대용량 다운로드 링크 포함
        String htmlBody = "<html><body>" +
                "<img src='https://postfiles.pstatic.net/MjAyNTAzMjFfMjUg/MDAxNzQyNTQ2NDQ2Njcy.pgCdBr_zLiEio2MkPXGuPoykjg7hIm4CRRV18GdEhKIg.xqp0GFk4aCAnUMFi_QGIMj4_SpBm4GKa5o-IKqBKBSQg.JPEG/%EB%B0%98%EB%93%B1-001.jpg  <br><br> https://ecimg.cafe24img.com/pg1137b86515555020/infopubipg/web/product/big/20250415/ad2d235bfd53c262968a4cff84ab6f39.png' alt='그림1' />" +
                "<a href='https://bigfile.mail.naver.com/download?fid=g/RcWrC516c9KCYZHquZFqu9FIYZKAU9KAtZKxuXKogdFCYwFqtXaAKlFxvlHrpoKrFSMrkvMqMlKxUZMqvrKzpopot9Mri0FzU9MrM/'>첨부 다운로드</a>" +
                "</body></html>";

        LGFileMailVO testVo = LGFileMailVO.builder()
                .em_id("em001")
                .em_body(htmlBody)
                .build();

        List<LGFileMailVO> inputList = Collections.singletonList(testVo);
        List<LGFileMailVO> yesBodyLinkList = new ArrayList<>();
        List<LGFileMailVO> noBodyLinkList = new ArrayList<>();

        // mock: 대용량 다운로드 호출 결과 미리 지정
        when(largeSizeFileDownloadService.downloadFileFromTwoStepUrl_autoSession(anyString()))
                .thenReturn("D:/downloaded/test_file.pdf");

        // when: private 메서드 호출
        Method method = MailAttachService.class.getDeclaredMethod(
                "processBodyLinks",
                List.class, List.class, List.class
        );
        method.setAccessible(true);
        method.invoke(mailAttachService, inputList, yesBodyLinkList, noBodyLinkList);

        // then: 결과 검증
        assertThat(yesBodyLinkList).hasSize(1);
        LGFileMailVO result = yesBodyLinkList.get(0);

        assertThat(result.getLink_yn()).isEqualTo("Y");
        assertThat(result.getBodyLinks()).containsExactlyInAnyOrder(
                "D:/images" + File.separator + "그림1.jpg",
                "D:/downloaded/test_file.pdf"
        );

        assertThat(noBodyLinkList).isEmpty();
    }


    /***
     * LGFileMailVO 클래스를 빌더를 사용해 생성하고, bodyLinks 가 있는경우, 원본 baseVo생성,
     * 각 bodyLinks 마다, key값 별도로 생성후 개별 LGFileMailVO 생성후, 최종 목록에 (List<LGFileMailVO> totalList) 추가.
     */
    @Test
    void test_splitByBodyLinks() {
        // given
        LGFileMailVO baseVo = LGFileMailVO.builder()
                .key("f1_m1")
                .attach_id("f1_m1")
                .attach_path("D:\\1.data_poc\\attachments\\(강의자료)_스프링_부트를_이용한__웹서비스_개발.pdf")
                .attach_parent("")
                .from_zipfile("N")
                .attach_name("(강의자료)_스프링_부트를_이용한__웹서비스_개발.pdf")
                .attach_exist("Y")
                .link_yn("")
                .em_id("m1")
                .subject("명량")
                .sender("jinhoh21@naver.com")
                .senddtm("2020-07-30T22:06:53")
                .receiver(null)
                .idxdtm(null)
                .em_body("...")
                .bodyLinks(Arrays.asList(
                        "D:\\1.data_poc\\input\\download\\9791198752406.jpg",
                        "D:\\1.data_poc\\input\\download\\9791194033585.jpg"
                ))
                .build();

        List<LGFileMailVO> totalList = new ArrayList<>();

        // 1. 원본 attach_path가 존재하는 경우 bodyLinks 제거 후 복사
        if (baseVo.getAttach_path() != null && !baseVo.getAttach_path().isBlank()) {
            LGFileMailVO original = baseVo.toBuilder()
                    .bodyLinks(null)
                    .build();
            totalList.add(original);
        }

        // 2. bodyLinks 각 항목별 복사
        List<String> bodyLinks = baseVo.getBodyLinks();
        if (bodyLinks != null) {
            for (int i = 0; i < bodyLinks.size(); i++) {
                int count = i + 1;
                String linkPath = bodyLinks.get(i);
                File file = new File(linkPath);

                LGFileMailVO bodyLinkVo = baseVo.toBuilder()
                        .attach_path(linkPath)
                        .attach_name(file.getName())
                        .key(baseVo.getKey() + "_" + i)
                        .attach_id(baseVo.getAttach_id() + "_" + count)
                        .bodyLinks(null)    // bodyLinks 를 "Y"로 세팅해야 하나???
                        .build();

                totalList.add(bodyLinkVo);
            }
        }
        // then
        assertEquals(3, totalList.size());

        for (LGFileMailVO vo : totalList) {
            System.out.println(vo.toPrettyJson());
        }
    }



    /***
     * LGFileMailVO 클래스를 빌더를 사용해 vo객체를 생성하고,
     * attach_path 가 첨부파일 형식(.zip)이면 unzip후, 개별 파일마다 LGFileMailVO 생성하는 테스트 메서드
     */
    @Test
    void test_checkFile_Unzip_if_Zipfile() throws IOException {

        FileService fileService = new FileService();  // 🔥 직접 생성 (스프링 빈 아님)

        // 🧪 1. 임시 디렉터리 및 텍스트 파일 생성
//        Path tempDir = Files.createTempDirectory("test_unzip_zip");
        Path tempDir = Path.of("src/test/resources/temp");

        File file1 = new File(tempDir.toFile(), "file1.txt");
        File file2 = new File(tempDir.toFile(), "file2.txt");

        Files.writeString(file1.toPath(), "테스트 파일1입니다.");
        Files.writeString(file2.toPath(), "테스트 파일2입니다.");

        // 🧪 2. ZIP 파일 생성
        File zipFile = new File(tempDir.toFile(), "test.zip");
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile))) {
            addToZipFile(file1, zos);
            addToZipFile(file2, zos);
        }

        assertThat(zipFile.exists()).isTrue();

        // 🧪 3. LGFileMailVO 구성
        LGFileMailVO vo = LGFileMailVO.builder()
                .key("testKey")
                .attach_id("testKey")
                .attach_path(zipFile.getAbsolutePath())
                .em_id("m1")
                .sender("jinhoh21@naver.com")
                .subject("테스트제목")
                .senddtm("2024-01-01T00:00:00")
                .build();

        List<LGFileMailVO> inputList = Collections.singletonList(vo);

        // 🧪 4. 메서드 테스트 실행 : 첨부파일이면 unzip 수행
        List<LGFileMailVO> resultList = fileService.checkFile_Unzip_if_Zipfile(inputList);

        // ✅ 5. 검증
        assertThat(resultList).isNotEmpty();
        assertThat(resultList.size()).isEqualTo(2); // file1, file2

        for (LGFileMailVO result : resultList) {
            System.out.println("📄 파일 경로: " + result.getAttach_path());
            System.out.println("🔑 key: " + result.getKey());
            assertThat(result.getFrom_zipfile()).isEqualTo("Y");
        }

        // 🧹 6. 정리
        deleteDirectoryRecursively(tempDir.toFile());
    }



    /***
     * ===아래와 같이 attach_path에 값이 있고, bodyLinks 에 값이 있는경우 데이터 분리저장 처리===
     * attach_path 값이 있으면:
     *   bodyLinks는 제거하고, 나머지 필드 값만 복사하여 totalMap에 저장
     *   기존 key, attach_id에 는 동일하게 사용
     * bodyLinks 배열의 각 요소마다:
     *   * 개별 맵을 생성하여 attach_path에 link 값 지정
     *   * 기존 key, attach_id에 _번호를 붙여 새로 지정
     * 이들을 전부 totalMap에 담기
     */

    @Test
    void transformResultMap_withBodyLinksAndAttachPath() {
        // given
        Map<String, Object> resultMap = new HashMap<>();
        resultMap.put("key", "f1_m1");
        resultMap.put("attach_id", "f1_m1");
        resultMap.put("attach_path", "D:\\1.data_poc\\attachments\\(강의자료)_스프링_부트를_이용한__웹서비스_개발.pdf");
        resultMap.put("attach_parent", "");
        resultMap.put("from_zipfile", "N");
        resultMap.put("attach_name", "(강의자료)_스프링_부트를_이용한__웹서비스_개발.pdf");
        resultMap.put("attach_exist", "Y");
        resultMap.put("link_yn", "");
        resultMap.put("em_id", "m1");
        resultMap.put("subject", "명량");
        resultMap.put("sender", "jinhoh21@naver.com");
        resultMap.put("senddtm", "2020-07-30T22:06:53");
        resultMap.put("receiver", null);
        resultMap.put("idxdtm", null);
        resultMap.put("em_body", "...");
        resultMap.put("bodyLinks", Arrays.asList(
                "D:\\1.data_poc\\input\\download\\9791198752406.jpg",
                "D:\\1.data_poc\\input\\download\\9791194033585.jpg"
        ));

        // when
        List<Map<String, Object>> totalMap = new ArrayList<>();

        // 1. 원본 attach_path가 존재하는 경우 bodyLinks 제거 후 복사
        if (resultMap.containsKey("attach_path") && resultMap.get("attach_path") != null) {
            Map<String, Object> baseMap = new HashMap<>(resultMap);
            baseMap.remove("bodyLinks");
            totalMap.add(baseMap);
        }

        // 2. bodyLinks의 각 항목마다 새로운 맵 생성
        List<String> bodyLinks = (List<String>) resultMap.get("bodyLinks");
        if (bodyLinks != null) {
            for (int i = 0; i < bodyLinks.size(); i++) {
                int count = i + 1;
                Map<String, Object> bodyMap = new HashMap<>(resultMap);
                bodyMap.put("attach_path", bodyLinks.get(i));
                bodyMap.remove("bodyLinks");
                bodyMap.put("key", resultMap.get("key") + "_" + i);
                bodyMap.put("attach_id", resultMap.get("attach_id") + "_" + count);
                //파일명변경
                File file = new File(bodyLinks.get(i));
                String fileName = file.getName();
                bodyMap.put("attach_name", fileName);

                totalMap.add(bodyMap);
            }
        }

        // then
        assertEquals(3, totalMap.size());

        // 디버깅 출력
        for (Map<String, Object> map : totalMap) {
            System.out.println(map);
        }
    }

    // 👉 파일을 ZIP에 추가하는 유틸
    private void addToZipFile(File file, ZipOutputStream zos) throws IOException {
        try (FileInputStream fis = new FileInputStream(file)) {
            ZipEntry zipEntry = new ZipEntry(file.getName());
            zos.putNextEntry(zipEntry);
            byte[] bytes = new byte[1024];
            int length;
            while ((length = fis.read(bytes)) >= 0) {
                zos.write(bytes, 0, length);
            }
            zos.closeEntry();
        }
    }


    // 👉 임시 디렉토리 삭제 유틸
    private void deleteDirectoryRecursively(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteDirectoryRecursively(child);
                }
            }
        }
        file.delete();
    }


}