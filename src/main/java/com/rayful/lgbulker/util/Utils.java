package com.rayful.lgbulker.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.util.HtmlUtils;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class Utils {

	public static String convertDateFormat(String inputDate) {
		if (inputDate == null || inputDate.isBlank()) return "";
		try {
			SimpleDateFormat inputFormat = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.ENGLISH);
			Date date = inputFormat.parse(inputDate);
			SimpleDateFormat outputFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
			return outputFormat.format(date);
		} catch (ParseException e) {
			return "";
		}
	}
	
	/**
	 * String 타입의 현재 시간
	 * @return String yyyyMMddHHmmss
	 */
	public static String getNow() {
		String result = null;
		Date date = new Date();
		SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss.S");
		result = sdf.format(date);
		
		return result;
	}
	
	/**
	 * String 타입의 현재 시간
	 * @return String yyyy-MM-dd HH:mm:ss
	 */
	public static String getNow2() {
		String result = null;
		Date date = new Date();
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		result = sdf.format(date);
		
		return result;
	}
	
	public static String getNow(String format) {
		String result = null;
		Date date = new Date();
		SimpleDateFormat sdf = new SimpleDateFormat(format);
		result = sdf.format(date);
		
		return result;
	}
	
	/**
	 * Bulker 실행 시작 시간
	 * @param timeZone
	 * @return String yyyy-MM-dd HH:mm:ss
	 */
	public static String getExecuteTime(String timeZone) {
		String result = null;
		Date date = new Date();
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		
		if(timeZone != null) {
			sdf.setTimeZone(TimeZone.getTimeZone(timeZone));
		}
		
		result = sdf.format(date);
		
		return result;
	}
	
	/**
	 * 프로그램 수행 시간
	 * @param ms
	 * @return
	 */
	public static String getMsToTime(long ms) {
		String result = String.format("%02d:%02d:%02d", 
				TimeUnit.MILLISECONDS.toHours(ms)
				, TimeUnit.MILLISECONDS.toMinutes(ms) - TimeUnit.HOURS.toMinutes(TimeUnit.MILLISECONDS.toHours(ms))
				, TimeUnit.MILLISECONDS.toSeconds(ms) - TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(ms))
			);
		
		return result;
	}
	
	/**
	 * 첨부파일 다운로드명
	 * @param fileName
	 * @return
	 */
	public static String getDownFileName(String fileName, String fileId) {
		String result = null;
		String ext = "";
		
		if(fileName != null) {
			if(fileName.lastIndexOf(".") > -1) {
				ext = fileName.substring(fileName.lastIndexOf(".") + 1);
			}
			
			result = fileId + "." + ext;
		}
		
		return result;
	}
	
	/**
	 * 파일 확장자 조회
	 * @param fileName
	 * @return
	 */
	public static String getFileExt(String fileName) {
		String result = null;
		
		if(fileName != null && fileName.lastIndexOf(".") > 0) {
			result = fileName.substring(fileName.lastIndexOf(".") + 1);
		}
		
		return result;
	}

	public static String getFileNameWithoutExt(String fileName) {
		String result = fileName;
		
		if(fileName != null && fileName.lastIndexOf(".") > 0) {
			result = fileName.substring(0, fileName.lastIndexOf("."));
		}
		
		return result;
	}
	
	public static String getFileName(String filePath) {
		String fileName = filePath;
		
		if(filePath != null) {
			if(filePath.lastIndexOf("\\") > 0) {
				fileName = filePath.substring(filePath.lastIndexOf("\\") + 1);
			} else if(filePath.lastIndexOf("/") > 0) {
				fileName = filePath.substring(filePath.lastIndexOf("/") + 1);
			}
		}
		
		return fileName;
	}
	
	/**
	 * 파일을 읽어 바이너리로 리턴
	 * @param filepath
	 * @return
	 */
	public static byte[] getFileBinary(String filepath) {
		File file = new File(filepath);
		byte[] data = new byte[(int) file.length()];
		
		try (FileInputStream stream = new FileInputStream(file)) {
			stream.read(data, 0, data.length);
		
		} catch (Throwable e) {
			e.printStackTrace();
		}
	
		return data;
	}
	
	public static String getFileText(String filePath, String lineSeparator) throws IOException {
		StringBuilder builder = new StringBuilder();
		String line = null;
		
		try (BufferedReader br = Files.newBufferedReader(Paths.get(filePath), StandardCharsets.UTF_8)) {
			while((line = br.readLine()) != null) {
				builder.append(line).append(lineSeparator);
			}
		}
		
		return builder.toString();
	}
	
	public static void getFileText(String filePath, String lineSeparator, StringBuilder builder) throws IOException {
		String line = null;
		
		try (BufferedReader br = Files.newBufferedReader(Paths.get(filePath), StandardCharsets.UTF_8)) {
			while((line = br.readLine()) != null) {
				builder.append(line).append(lineSeparator);
			}
		}
	}
	
	@SuppressWarnings("unchecked")
	public static Map<String, Object> getMapFromJsonFile(File filePath) throws IOException {
		Map<String,Object> result =  (Map<String,Object>) new ObjectMapper().readValue(filePath, HashMap.class);
		return result;
	}
	
	public static void moveDirectory(Path source, Path target) throws IOException{
		if(!Files.isDirectory(source)) {
			throw new IllegalArgumentException("Not directory: " + source);
		} else if(!Files.exists(source)) {
			throw new FileNotFoundException("Source is not exists: " + source);
		}
		
		if(!Files.exists(target.getParent())) {
			throw new FileNotFoundException("The Parent of target is not exists: " + target.getParent());
		}
		
		Files.walkFileTree(source, new FileVisitor<Path>() {

			@Override
			public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
				if(dir.equals(source)) {
					if(!Files.exists(target)) {
						Files.createDirectory(target);
					}
				} else {
					Path destDir = target.resolve(source.relativize(dir));
					if(!Files.exists(destDir)) {
						Files.createDirectory(destDir);
					}
				}
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
				Path destFile = target.resolve(source.relativize(file));
				Files.move(file, destFile, StandardCopyOption.REPLACE_EXISTING);
				
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
				return FileVisitResult.TERMINATE;
			}

			@Override
			public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
				if(isEmptyDirectory(dir)) {
					Files.delete(dir);
				}
				return FileVisitResult.CONTINUE;
			}
		});
	}
	
	public static boolean isEmptyDirectory(Path dirPath) throws IOException{
		boolean isEmptyDirectory = true;
		
		if(Files.exists(dirPath)) {
			if(!Files.isDirectory(dirPath)) {
				throw new IllegalArgumentException("Not directory: " + dirPath);
			}
			
			try(DirectoryStream<Path> stream = Files.newDirectoryStream(dirPath)) {
				isEmptyDirectory = !stream.iterator().hasNext();
			}
		}
		
		return isEmptyDirectory;
	}
	
	/**
	 * Object > String 형변환
	 * @param
	 * @return
	 */
	public static String objToString(Object objStr) {
		String result = null;
		
		if(objStr == null) {
			result = null;
		} else if(objStr instanceof String) {
			result = (String) objStr;
		} else if (objStr instanceof Integer
				|| objStr instanceof Long
				|| objStr instanceof Double
				|| objStr instanceof Float
				) {
			result = String.valueOf(objStr);
		} else {
			result = objStr.toString();
		}
		
		return result;
	}
	
	/**
	 * HTML 테그 제거
	 * @param
	 * @return
	 */
	public static String getRemoveTag(String str) {
		String result = null;
		
		if(str != null) {
			result = str.replaceAll("<(/)?([a-zA-Z1-9]*)(\\s[a-zA-Z1-9]*=[^>]*)?(\\s)*(/)?>", "");
			result = result.replace("<!DOCTYPE html>", "");
			result = result.replace("\n", "");
			
			while (result.indexOf("  ") > -1) {
				result = result.replace("  ", " ");
			}
		}
		
		return result;
	}
	
	/**
	 * long 타입을 시간으로 변경
	 * @param ms
	 * @return
	 */
	public static String getLongToTime(long ms) {
		String result = null;
		long hours = (ms / 1000) / 60 / 60 % 24;
		long minutes = (ms / 1000) / 60 % 60;
		long seconds = (ms / 1000) % 60;
		
		if(hours + minutes + seconds == 0) {
			result = ms + "(ms)";
		
		} else {
			result = String.format("%02d:%02d:%02d", hours, minutes, seconds);
		}
		
		return result;
	}
	
	public static String toFormatString(Date date) {
		SimpleDateFormat oSdf = new SimpleDateFormat( "yyyy-MM-dd HH:mm:ss" );
		String sDate = oSdf.format( date );
		return sDate;
	}

	/**
	 * Html 테그를 제거한다.
	 * </p>
	 * @param text 태그를 포함한 텍스트  
	 * @return 테그를 제거한 텍스트
	 */
	public static String removeHtmlTag(String text) {
		String result = null;
		if(text != null) {
			result = HtmlUtils.htmlUnescape(text).replaceAll("<[^>]*>", " ");
		}
		
		return result;
	}
	
	/**
	 * 불필요한 공백(스페이스, 탭, 개행)문자를 제거한다.
	 * </p>
	 * @param text 텍스트
	 * @return 불필요한 공백(스페이스, 탭, 개행)문자를 제거한 텍스트
	 */
	public static String removeSpace(String text) {
		String result = null;
		if(text != null) {
			result = text.replaceAll("\\s", " ").replaceAll("\\s{2,}", " ");
		}
		
		return result;
	}

}
