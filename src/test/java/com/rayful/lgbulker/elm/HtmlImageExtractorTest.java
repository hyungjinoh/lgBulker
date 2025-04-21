package com.rayful.lgbulker.elm;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

public class HtmlImageExtractorTest {

    @Test
    public void extractImageTags() {
        String html = "<!-- 수정부분 : start -->\n" +
                "<br /><br />\n" +
                "<table style=\"width:100%;font-size:11px;border-bottom:1px solid #999;font-size:12px;font-family:Dotum;table-layout:fixed;\" cellpadding=\"4px\" cellspacing=\"0\">\n" +
                "<tbody>\n" +
                "<tr>\n" +
                "<th style=\"width:70px;text-align:left;vertical-align:top;\">Subject</th>\n" +
                "<td>사내메일에서 네이버 발송 Case0-본문이미지삽입(JPG, PNG, BMP)</td>\n" +
                "</tr>\n" +
                "<tr>\n" +
                "<th style=\"text-align:left;vertical-align:top;\">From</th>\n" +
                "<td>최성갑</td>\n" +
                "</tr>\n" +
                "<tr>\n" +
                "<th style=\"text-align:left;vertical-align:top;\">To</th>\n" +
                "<td>dowoomics@naver.com</td>\n" +
                "</tr>\n" +
                "<tr>\n" +
                "<th style=\"text-align:left;vertical-align:top;\">Cc</th>\n" +
                "<td></td>\n" +
                "</tr>\n" +
                "<tr><strong></strong>\n" +
                "<th style=\"text-align:left;vertical-align:top;\">Date</th>\n" +
                "<td>2025-04-07 14:38:00</td>\n" +
                "</tr>\n" +
                "</tbody>\n" +
                "</table>\n" +
                "<table style=\"width:100%;font-size:11px;border-bottom:1px solid #999;font-size:13px;font-family:Dotum;table-layout:fixed;\" cellpadding=\"4px\" cellspacing=\"0\">\n" +
                "<tbody>\n" +
                "\n" +
                "</tbody>\n" +
                "</table>\n" +
                "<!-- 수정부분 : end -->\n" +
                "<p style=\"font-size: 11pt; font-family: \"LG스마트체2.0\"; line-height: 1.2; margin-top: 0px; margin-bottom: 0px;\">&nbsp;</p>\n" +
                "<div role=\"signature\" style=\"font-family: \"LG스마트체2.0\"; font-size: 11pt; line-height: 1.2; margin-top: 0px; margin-bottom: 0px;\"><p style=\"font-family: \"LG스마트체2.0\"; font-size: 11pt; line-height: 1.2; margin-top: 0px; margin-bottom: 0px;\"><span style=\"font-size: 11pt; font-family: \"LG스마트체2.0\";\"><span style=\"font-family: \"LG스마트체2.0\"; font-size: 11pt;\">안녕하세요&nbsp;최성갑입니다.</span><span style=\"font-family: \"LG스마트체2.0\";\"><br>\n" +
                "</span><span style=\"font-family: \"LG스마트체2.0\";\"><br>\n" +
                "</span></span\n" +
                "></p>\n" +
                "<p style=\"font-family: \"LG스마트체2.0\"; font-size: 11pt; line-height: 1.5; margin-top: 0px; margin-bottom: 0px;\"><span style=\"font-family: \"LG스마트체2.0\";\"><img src=\"cid:000\" title=\"\" alt=\"그림1\" border=\"0\" style=\"width: 486px; height: 678px; border: 0px solid rgb(0, 0, 0);\"><br>\n" +
                "</span></p>\n" +
                "<p style=\"font-family: \"LG스마트체2.0\"; font-size: 11pt; line-height: 1.5; margin-top: 0px; margin-bottom: 0px;\"><span style=\"font-family: \"LG스마트체2.0\";\"><br>\n" +
                "</span></p>\n" +
                "<p style=\"font-family: \"LG스마트체2.0\"; font-size: 11pt; line-height: 1.5; margin-top: 0px; margin-bottom: 0px;\"><span style=\"font-family: \"LG스마트체2.0\";\"><img src=\"cid:001\" title=\"\" alt=\"그림2\" border=\"0\" style=\"width: 486px; height: 678px; border: 0px solid rgb(0, 0, 0);\"><br>\n" +
                "</span></p>\n" +
                "<p style=\"font-family: \"LG스마트체2.0\"; font-size: 11pt; line-height: 1.5; margin-top: 0px; margin-bottom: 0px;\"><span style=\"font-size: 11pt; font-family: \"LG스마트체2.0\";\"><span style=\"font-family: \"LG스마트체2.0\"; font-size: 11pt;\"><br>\n" +
                "</span></span\n" +
                "></p>\n" +
                "<p style=\"font-family: \"LG스마트체2.0\"; font-size: 11pt; line-height: 1.5; margin-top: 0px; margin-bottom: 0px;\"><span style=\"font-size: 11pt; font-family: \"LG스마트체2.0\";\"><span style=\"font-family: \"LG스마트체2.0\"; font-size: 11pt;\"><br>\n" +
                "</span></span\n" +
                "></p>\n" +
                "<p style=\"font-family: \"LG스마트체2.0\"; font-size: 11pt; line-height: 1.5; margin-top: 0px; margin-bottom: 0px;\"><span style=\"font-size: 11pt; font-family: \"LG스마트체2.0\";\"><span style=\"font-family: \"LG스마트체2.0\"; font-size: 11pt;\"><img src=\"cid:002\" title=\"\" alt=\"그림3\" border=\"0\" style=\"width: 486px; height: 678px; border: 0px solid rgb(0, 0, 0);\"><br>\n" +
                "</span></span\n" +
                "></p>\n" +
                "<p style=\"font-family: \"LG스마트체2.0\"; font-size: 11pt; line-height: 1.5; margin-top: 0px; margin-bottom: 0px;\"><span style=\"font-size: 11pt; font-family: \"LG스마트체2.0\";\"><span style=\"font-family: \"LG스마트체2.0\"; font-size: 11pt;\"><br>\n" +
                "</span></span\n" +
                "></p>\n" +
                "<p style=\"font-family: \"LG스마트체2.0\"; font-size: 11pt; line-height: 1.5; margin-top: 0px; margin-bottom: 0px;\"><span style=\"font-size: 11pt; font-family: \"LG스마트체2.0\";\"><span style=\"font-family: \"LG스마트체2.0\"; font-size: 11pt;\"><br>\n" +
                "</span></span\n" +
                "></p>\n" +
                "<p style=\"font-family: \"LG스마트체2.0\"; font-size: 11pt; line-height: 1.5; margin-top: 0px; margin-bottom: 0px;\"><span style=\"font-size: 11pt; font-family: \"LG스마트체2.0\";\"><span style=\"font-family: \"LG스마트체2.0\"; font-size: 11pt;\">감사합니다.</span></span\n" +
                "></p>\n" +
                "<p style=\"font-family: \"LG스마트체2.0\"; font-size: 11pt; line-height: 1.5; margin-top: 0px; margin-bottom: 0px;\"><span style=\"font-size: 11pt; color: rgb(0, 112, 54); font-family: \"LG스마트체2.0\";\"><span style=\"font-family: \"LG스마트체2.0\"; font-size: 11pt;\">**************************************************************************</span></span\n" +
                "><span style=\"font-family: \"LG스마트체2.0\";\"><br>\n" +
                "</span><span style=\"font-size: 11pt; color: rgb(0, 112, 54); font-family: \"LG스마트체2.0\";\"><span style=\"font-family: \"LG스마트체2.0\";\"><span style=\"font-family: \"LG스마트체2.0\"; font-size: 11pt;\"><b>최성갑&nbsp;책임</b>&nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp;LG&nbsp;CNS</span></span\n" +
                "></span></p>\n" +
                "<p style=\"font-family: \"LG스마트체2.0\"; font-size: 11pt; line-height: 1.5; margin-top: 0px; margin-bottom: 0px;\"><b style=\"color: rgb(0, 112, 54); font-size: 14.6667px;\">M.&nbsp;82-10-9473-4396 /&nbsp;</b><b style=\"color: rgb(0, 112, 54); font-size: 14.6667px;\">T.&nbsp;82-2-3773-2243</b></p>\n" +
                "<p style=\"font-family: \"LG스마트체2.0\"; font-size: 11pt; line-height: 1.5; margin-top: 0px; margin-bottom: 0px;\"><span style=\"font-size: 11pt; color: rgb(0, 112, 54); font-family: \"LG스마트체2.0\";\"><span style=\"font-family: \"LG스마트체2.0\"; font-size: 11pt;\">LG&nbsp;Twin&nbsp;Towers,&nbsp;128&nbsp;Yeoui-daero,&nbsp;Yeongdeungpo-gu,&nbsp;Seoul&nbsp;150-721,&nbsp;Korea</span></span\n" +
                "><span style=\"font-family: \"LG스마트체2.0\";\"><br>\n" +
                "</span><span style=\"font-size: 11pt; color: rgb(0, 112, 54); font-family: \"LG스마트체2.0\";\"><span style=\"font-family: \"LG스마트체2.0\"; font-size: 11pt;\">**************************************************************************</span></span\n" +
                "></p>\n" +
                "</div><p style=\"font-size: 11pt; font-family: \"LG스마트체2.0\"; line-height: 1.2; margin-top: 0px; margin-bottom: 0px;\">&nbsp;</p>\n" +
                "<div role=\"signature\" style=\"font-family: \"LG스마트체2.0\"; font-size: 11pt; line-height: 1.2; margin-top: 0px; margin-bottom: 0px;\"><p style=\"color: rgb(34, 34, 34); font-size: 11pt; background-color: rgb(255, 255, 255); font-family: LG스마트체; line-height: 1.2; margin-top: 0px; margin-bottom: 0px;\"><span style=\"color: rgb(255, 0, 0); font-size: 10pt; font-family: LG스마트체;\">본&nbsp;메일에는&nbsp;LG&nbsp;및&nbsp;관련&nbsp;회사의&nbsp;영업비밀,&nbsp;투자자의&nbsp;</span><wbr style=\"color: rgb(255, 0, 0); font-size: 10pt;\"><span style=\"color: rgb(255, 0, 0); font-size: 10pt; font-family: LG스마트체;\">투자&nbsp;판단에&nbsp;중대한&nbsp;영향을&nbsp;미치는&nbsp;미공개중요정보가&nbsp;</span><wbr style=\"color: rgb(255, 0, 0); font-size: 10pt;\"><span style=\"color: rgb(255, 0, 0); font-size: 10pt; font-family: LG스마트체;\">포함되어&nbsp;있을&nbsp;수&nbsp;있습니다.</span><span style=\"color: rgb(0, 0, 0); font-size: 11pt; font-family: LG스마트체;\"></span></p>\n" +
                "<p style=\"color: rgb(34, 34, 34); font-size: 11pt; background-color: rgb(255, 255, 255); font-family: LG스마트체; line-height: 1.2; margin-top: 0px; margin-bottom: 0px;\"><span style=\"color: rgb(255, 0, 0); font-size: 10pt; font-family: LG스마트체;\">미공개중요정보를&nbsp;증권&nbsp;등&nbsp;거래에&nbsp;이용하거나&nbsp;타인에게&nbsp;<wbr style=\"font-size: 10pt;\">이용하게&nbsp;하는&nbsp;경우&nbsp;관련&nbsp;법령에&nbsp;따른&nbsp;민∙형사상&nbsp;<wbr style=\"font-size: 10pt;\">책임을&nbsp;질&nbsp;수&nbsp;있으므로</span></p>\n" +
                "<p style=\"color: rgb(34, 34, 34); font-size: 11pt; background-color: rgb(255, 255, 255); font-family: LG스마트체; line-height: 1.2; margin-top: 0px; margin-bottom: 0px;\"><span style=\"color: rgb(255, 0, 0); font-size: 10pt; font-family: LG스마트체;\">업무상&nbsp;반드시&nbsp;필요하여&nbsp;공유하는&nbsp;외에는&nbsp;각별히&nbsp;보안에&nbsp;<wbr style=\"font-size: 10pt;\">유의하여&nbsp;주시기&nbsp;바랍니다.</span></p>\n" +
                "</div>"; // (생략 가능) 위의 긴 html 문자열

        // HTML 파싱
        Document doc = Jsoup.parse(html);

        // <img> 태그 선택
        Elements images = doc.select("img");

        List<Map<String, String>> imgurls = new ArrayList<>();

        for (Element img : images) {
            String src = img.attr("src");
            String alt = img.attr("alt");

            Map<String, String> map = new HashMap<>();
            map.put("src", src);
            map.put("alt", alt);

            imgurls.add(map);
        }

        // 결과 출력
        imgurls.forEach(System.out::println);

        // ✅ 테스트 검증용 (예시)
        assertThat(imgurls).hasSize(3); // 그림1, 그림2, 그림3
        assertThat(imgurls.get(0).get("alt")).isEqualTo("그림1");
    }
}
