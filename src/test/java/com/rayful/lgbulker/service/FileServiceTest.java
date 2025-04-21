package com.rayful.lgbulker.service;

import com.rayful.lgbulker.vo.LGFileMailVO;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

class FileServiceTest {


    @Test
    void test_checkMap_Unzip_if_Zipfile() {
    }

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

        // 🧪 4. 메서드 실행
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