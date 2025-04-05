package com.rayful.lgbulker.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.rayful.lgbulker.vo.BulkerLogVO;
import com.rayful.lgbulker.vo.CountLogVO;
import org.apache.http.client.HttpClient;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.ssl.TrustStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

import javax.net.ssl.SSLContext;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * 벌커 수행에 필요한 공통기능을 구현한 유틸릴티 클래스
 */
public class BulkerUtils {
	private static final Logger logger = LoggerFactory.getLogger(BulkerUtils.class);
	private static final int RESTTEMPAT_CONN_TIMOUT=3000;
	private static final int RESTTEMPAT_POOL_MAXTOTAL=30;
	private static final int RESTTEMPAT_POOL_MAXPERROUTE=10;
	public static final String LOG_DATA_LINE 		= "---------------------------------------------------------------";
	public static final String LOG_SUMMARY_LINE 	= "===============================================================================";
	
	
	private static RestTemplate REST_TMPLATE = null;
	private static RestTemplate REST_SSL_TMPLATE = null;
	private static final Gson GSON =  new GsonBuilder().disableHtmlEscaping().create();
	private static final Gson GSONS24 =  new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
	
	/**
	 * 벌커 실행파라미터 유효성 체크
	 * @param args 실팽 파라미터
	 * @return 파라미터 정보 Map
	 */
	public static Map<String, Object> validParams(String[] args, Properties prop) {
		Map<String, Object> paramMap = new HashMap<>();
		String paramKey = null;
		String paramVal = null;
		
		if(args.length > 0) {
			for (int i = 0; i < args.length - 1; i += 2) {
				paramKey = args[i].toLowerCase().replace("-", "");
				paramVal = args[i + 1].toLowerCase();
				paramMap.put(paramKey, paramVal);
			}
			
			if(!paramMap.containsKey("dfname")) {
				logger.error("-dfname \"DEFINITION_FILE_NAME\" parameter is required");
				System.out.println("Invalid parameter:");
				System.out.println("\t-dfname \"DEFINITION_FILE_NAME\" parameter is required");
				System.exit(1);
			}
			
			String indexMode = (String)paramMap.get("indexmode");
			if(indexMode == null) {
				paramMap.put("indexmode", prop.get("BULKER.DEFAULT.INDEXMODE"));
				if(!"all".equals(indexMode) && "inc".equals(indexMode) &&"send".equals(indexMode)) {
					logger.error("indexmode is invalid: {}",  indexMode);
					System.out.println("Invalid parameter:");
					System.out.println("\tindexmode is invalid: " + indexMode);
					System.exit(1);
				}
			}
			
			// 2024.09.02 정충열 - parameter (maxrows) 추가 
			String maxrows = (String)paramMap.get("maxrows");
			if(paramMap.containsKey("maxrows")) {
				try {
					paramMap.put("maxrows", Integer.parseInt(maxrows));
				} catch(Exception e) {
					logger.error("maxrows must be number format: {}", maxrows);
					System.exit(1);
				}
			} else {
				paramMap.put("maxrows", -1); // Default 값
			}
			
			// 2024.08.26 정충열 - parameter (printdetail) 추가 
			if(paramMap.containsKey("printdetail")) {
				paramMap.put("printdetail", "yes".equals(paramMap.get("printdetail")));
			} else {
				paramMap.put("printdetail", true); // Default 값
			}
			
			// 2024.08.26 정충열 - parameter (printinterval) 추가 
			if(paramMap.containsKey("printinterval")) {
				try {
					paramMap.put("printinterval", Integer.parseInt((String)paramMap.get("printinterval")));
				} catch(Exception e) {
					logger.warn("Parameter - printinterval is ignored. Because not number format.");
					paramMap.put("printinterval", 100); // Default 값
				}
			} else {
				paramMap.put("printinterval", 100); // Default 값
			}
			
		} else {
			System.out.println("printUsage:");
			System.out.println("\tjava com.rayful.bulk.exec.Bulker -indexmode INDEX_MODE -dfname DEFINITION_FILE_NAME");
			System.out.println("\t\t[-maxrows number]");
			System.out.println("\t\t[-printdetail yes/no] [-printinterval number]"); 
			
			System.out.println("\tOptions:");
			
			System.out.println("\t\t-indexmode INDEX_MODE all/inc/send");
			System.out.println("\t\t\tall: Collect all data");
			System.out.println("\t\t\tinc: Collect modified data after last indexed");
			System.out.println("\t\t\tsend: Do not collect dat but only send indexing reqeust for bulk file on result folder");
			
			System.out.println("\t\t-dfname DEFINITION_FILE_NAME");
			System.out.println("\t\t\tDEFINITION_FILE_NAME value must be xml file name without extension");
			
			System.out.println("\t\t-maxrows number");
			System.out.println("\t\t\tDefine number for bulkering data to test (TEST MODE)");
			
			System.out.println("\t\t-printdetail yes/no");
			System.out.println("\t\t\tyes: Print detail log for bulkering (default value)");
			System.out.println("\t\t\tno: Print short log for bulkering");
			
			System.out.println("\t\t-printinterval number");
			System.out.println("\t\t\tDefine interval(number) of printing log for bulkering");
			System.out.println("\t\t\tdefault value: 100");
		}
		
		return paramMap;
	}
	
	/**
	 * lock 파일을 객체를 생성해서 리턴한다.
	 * @param dfName DF 정보
	 * @param indexType 색인 타입 (index / delete)
	 * @param prop 설정정보 파일
	 * @return lock 파일
	 */
	public static File getLockFile(String dfName, String indexType, Properties prop) {
		String lockPath = prop.getProperty("BULKER.PATH.LOCK");
		String lockName = null;
		
		if("index".equalsIgnoreCase(indexType)) {
			lockName = dfName + ".lck";
		} else if("update".equalsIgnoreCase(indexType)) {
			lockName = dfName + "_update.lck";
		} else {
			lockName = dfName + "_delete.lck";
		}
		
		File file = new File(lockPath, lockName);
		
		return file;
	}
	
	/**
	 * Lock 파일 존재 여부 확인 및 생성
	 * <p/>
	 * lock 파일이 존재 하지 않으면 lock 파일을 생성하고 false를 리턴한다.
	 * @param file lock 파일
	 * @return Lock 파일 존재할 경우 true, Lock 파일 존재하지 않을 경우 false + lock 파일 생성
	 */
	public static boolean hasLockFile(File file) {
		boolean isResult = false;

		if(file.exists()) {
			isResult = true;
		
		} else {
			FileOutputStream fos = null;
			
			try {
				if(!file.getParentFile().exists()) {
					file.getParentFile().mkdirs();
				}
				
				fos = new FileOutputStream(file);
				fos.write(1);
			
			} catch (FileNotFoundException e1) {
				logger.error(e1.toString(), e1);
			
			} catch (IOException e2) {
				logger.error(e2.toString(), e2);
			
			} finally {
				if(fos != null) { try { fos.close(); } catch (IOException e3) { logger.error(e3.getMessage(), e3); } }
			}
		}
		
		return isResult;
	}
	
	/**
	 * Lock 파일 삭제
	 * @param file lock 파일
	 */
	public static void deleteLockFile(File file) {
		if(file.exists()) {
			file.delete();
		}
	}
	/**
	 * RestTemplate 클래스의 인스턴스를 생성애서 리턴한다.
	 * @return RestTemplate 클래스의 인스턴스
	 */
	public static RestTemplate getInstanceRestTemplate(){
		if( REST_TMPLATE == null) {
			HttpClient httpClient = HttpClientBuilder.create()
					.setMaxConnTotal(RESTTEMPAT_POOL_MAXTOTAL)    //최대 오픈되는 커넥션 수, 연결을 유지할 최대 숫자
					.setMaxConnPerRoute(RESTTEMPAT_POOL_MAXPERROUTE)   //IP, 포트 1쌍에 대해 수행할 커넥션 수, 특정 경로당 최대 숫자
					.build();
	
			HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory();
							//factory.setReadTimeout(5000);        
							factory.setConnectTimeout(RESTTEMPAT_CONN_TIMOUT);     
							factory.setHttpClient(httpClient);
			
			RestTemplate restTemplate = new RestTemplate(factory);
			restTemplate.setErrorHandler(new DefaultResponseErrorHandler() {
				@Override
				public void handleError(ClientHttpResponse response) throws IOException {
					logger.error("RestTemplate --- ERROR statusCode={}", response.getStatusCode());
				}
			});
			
			REST_TMPLATE = restTemplate;
		}
		
		return REST_TMPLATE;
	}
	
	/**
	 * SSL 용 RestTemplate 클래스의 인스턴스를 생성애서 리턴한다.
	 * @return SSL 용 RestTemplate 클래스의 인스턴스
	 * @throws Exception
	 */
	public static RestTemplate getInstanceRestTemplateSsl() throws Exception {
		if(REST_SSL_TMPLATE == null) {
			TrustStrategy trustAllStrategy = (X509Certificate[] chain, String authType) -> true;
			SSLContext sslContext = null;
			try {
				sslContext = org.apache.http.ssl.SSLContexts.custom()
								.loadTrustMaterial(null, trustAllStrategy)
								.build();
			} catch (KeyManagementException | NoSuchAlgorithmException | KeyStoreException e) {
				logger.error("falied to load RestTemplateSsl class", e);
				throw new Exception("RestTemplateSsl class: " + e.getMessage());
			}
			
			SSLConnectionSocketFactory sslFactory = new SSLConnectionSocketFactory(sslContext, new NoopHostnameVerifier());
			CloseableHttpClient httpClient = HttpClients.custom()
												.setSSLSocketFactory(sslFactory)
												.setMaxConnTotal(RESTTEMPAT_POOL_MAXTOTAL)
												.setMaxConnPerRoute(RESTTEMPAT_POOL_MAXPERROUTE)
												.build();
			
			HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory();
			//factory.setReadTimeout(5000);        
			factory.setConnectTimeout(RESTTEMPAT_CONN_TIMOUT);     
			factory.setHttpClient(httpClient);
			
			RestTemplate restTemplate = new RestTemplate(factory);
			restTemplate.setErrorHandler(new DefaultResponseErrorHandler() {
				@Override
				public void handleError(ClientHttpResponse response) throws IOException {
					logger.error("RestTemplateSsl --- ERROR statusCode={}", response.getStatusCode());
				}
			});
			
			REST_SSL_TMPLATE = restTemplate;
		}
		
		return REST_SSL_TMPLATE;
	}
	
	/**
	 * Gson 객체를 생성해서 리턴한다.
	 * @return Gson 객체
	 */
	public static Gson getGson() {
		return GSON;
	}
	
	/**
	 * GsonS24 객체를 생성해서 리턴한다.
	 * @return GsonS24 객체
	 */
	public static Gson getGsonS24() {
		return GSONS24;
	}
	
	/**
	 * 벌커의 요약로그를 기록한다.
	 * <p/>
	 * 쿼리 요약 로그, 덤프 요약 로그, QA 덤프 요약 로그, 인덱스 요청 요약 로그를 기록한다.
	 * @param bulkerLogVO 벌커 로그 객체
	 */
	public static void printBulkerLog(BulkerLogVO bulkerLogVO) {
		CountLogVO logVO = null;

		logger.info("");
		logger.info(LOG_SUMMARY_LINE);
		logger.info("            작업내용 요약           ");
		logger.info(LOG_SUMMARY_LINE);
		

		// -----------------------------------------------
		// 이메일정보 요약 로그 출력
		logVO = bulkerLogVO.email;
		if (logVO.getTotalCount() > 0) {
			logger.info(LOG_DATA_LINE);
			logger.info("# Email Summary #");
			logger.info("- [Processed Count] {}", logVO.getTotalCount());
//			logger.info("- [Data Process] Success: {}",logVO.getSuccessCount());
			if(logVO.getWarnCount() > 0) {
				logger.info("\tWarn: {} {} ", logVO.getWarnCount(), logVO.getWarnMessage());
			}
			if(logVO.getFailCount() > 0) {
				logger.info("\tFail: {} {} ", logVO.getFailCount(), logVO.getFailMessage());
			}
			//logger.info(LOG_DATA_LINE);
		}

		// -----------------------------------------------
		// 첨부파일 요약 로그 출력
		logVO = bulkerLogVO.attach;
		if (logVO.getTotalCount() > 0) {
			logger.info(LOG_DATA_LINE);
			logger.info("# Attachment Summary #");
			logger.info("- [Processed Count] {}", logVO.getTotalCount());
//			logger.info("- [Data Process] Success: {}",logVO.getSuccessCount());
			if(logVO.getWarnCount() > 0) {
				logger.info("\tWarn: {} {} ", logVO.getWarnCount(), logVO.getWarnMessage());
			}
			if(logVO.getFailCount() > 0) {
				logger.info("\tFail: {} {} ", logVO.getFailCount(), logVO.getFailMessage());
			}
			//logger.info(LOG_DATA_LINE);
		}
		// -----------------------------------------------
		// 인덱스 요청 요약 로그 출력
		logVO = bulkerLogVO.index;
		if (logVO.getTotalCount() > 0) {
			logger.info(LOG_DATA_LINE);
			logger.info("# Index Request Summary #");
			logger.info("- [Bulk File Count] {}", logVO.getTotalCount());
			logger.info("- [Index Request] Success: {}",logVO.getSuccessCount());
			if(logVO.getWarnCount() > 0) {
				logger.info("\tWarn: {} {} ", logVO.getWarnCount(), logVO.getWarnMessage());
			}
			if(logVO.getFailCount() > 0) {
				logger.info("\tFail: {} {} ", logVO.getFailCount(), logVO.getFailMessage());
			}
			//logger.info(LOG_DATA_LINE);
			
			logger.info(LOG_DATA_LINE);
			logger.info("# Index Response Summary #");
			logger.info("- [Engine Response] Total: {}", bulkerLogVO.indexResult.getTotalCount());
			logger.info("- [Engine Resposne] Created: {}", bulkerLogVO.indexResult.getCreateCount());
			logger.info("- [Engine Resposne] Updated: {}", bulkerLogVO.indexResult.getUpdateCount());
			logger.info("- [Engine Resposne] Deleted: {}", bulkerLogVO.indexResult.getDeleteCount());
			if(bulkerLogVO.indexResult.getPartialCount() > 0) {
				logger.warn("- [Engine Resposne] Partail: {}", bulkerLogVO.indexResult.getPartialCount());
			}
			if(bulkerLogVO.indexResult.getEtcCount() > 0) {
				logger.warn("- [Engine Resposne] Etc: {}", bulkerLogVO.indexResult.getEtcCount());
				logger.warn("    {}", bulkerLogVO.indexResult.getEtcResult());
			}
			if(bulkerLogVO.indexResult.getErrorCount() > 0) {
				logger.warn("- [Engine Resposne] Error: {}", bulkerLogVO.indexResult.getErrorCount());
				logger.warn("    {}", bulkerLogVO.indexResult.getErrorReason());
			}
		}
		logger.info(LOG_SUMMARY_LINE);
		
	}
	
}
