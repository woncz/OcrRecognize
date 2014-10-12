package test;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.cookie.Cookie;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.cookie.BasicClientCookie;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpParams;
import org.apache.http.util.EntityUtils;

import util.StaticParamConstants;

public class HttpClientNewVersionTest {
	public static void main(String[] args) throws Exception {
		boolean flag = true;
		while(flag) {
			String LT = "";
			String JSESSIONID = "";
			BasicClientCookie cookieUUID = new BasicClientCookie("P_UUID",
					StaticParamConstants.P_UUID);
			cookieUUID.setVersion(0);
			cookieUUID.setDomain(StaticParamConstants.UCAS_IP);
			cookieUUID.setPath("/ucas");
			cookieUUID.setExpiryDate(null);

			BasicCookieStore cookieStore = new BasicCookieStore();
			cookieStore.addCookie(cookieUUID);

			CloseableHttpClient httpClient = HttpClients.custom()
					.setDefaultCookieStore(cookieStore).build();
			try {
				String url = "http://"
						+ StaticParamConstants.UCAS_IP + "/ucas/login";
				HttpGet httpGet = new HttpGet(url);
				CloseableHttpResponse pmoResponse = httpClient.execute(httpGet);
				try {
					System.out.println("1登陆首页: " + pmoResponse.getStatusLine());
					HttpEntity pmoEntity = pmoResponse.getEntity();

					String content = EntityUtils.toString(pmoEntity);
					String target = content.replaceAll("\t", "");
					LT = getHiddenValue(target, "lt", 76);

					// and ensure it is fully consumed
					EntityUtils.consume(pmoEntity);

					/**
					 * List<Cookie> cookies = cookieStore.getCookies();
					 * System.out.println("Initial set of cookies:" +
					 * cookies.size()); if (cookies.isEmpty()) {
					 * System.out.println("None"); } else { for (int i = 0; i <
					 * cookies.size(); i++) { Cookie cookie = cookies.get(i);
					 * System.out.println("- " + cookie.toString()); if
					 * (cookie.getName().equalsIgnoreCase("JSESSIONID")) {
					 * JSESSIONID = cookie.getValue(); } }
					 * System.out.println("JSESSIONID = |" + JSESSIONID + "|"); }
					 */
				} catch (Exception e) {
					e.printStackTrace();
				} finally {
					pmoResponse.close();
				}

				// --2. /ucas/login
				downloadImage(httpClient);

				// use Tesseract jar(third party)
				String ocr = getOcr();
				System.out.println("  Ocr--|" + ocr + "|");

				// use enum all(better)
				ocr = ImagePreProcess3.getAllOcr("img\\pmo.jpg");
				System.out.println("  Ocr--|" + ocr + "|");

				// validate the ocr code
				httpGet = new HttpGet("http://" + StaticParamConstants.UCAS_IP
						+ "/ucas/user/auth/validator.htm?code=" + ocr);
				pmoResponse = httpClient.execute(httpGet);
				try {
					System.out.println("3验证OCR: " + pmoResponse.getStatusLine());
				} catch (Exception e) {
					e.printStackTrace();
				} finally {
					pmoResponse.close();
				}

				// login
				HttpPost httPost = new HttpPost("http://"
						+ StaticParamConstants.UCAS_IP + "/ucas/login;jsessionid="
						+ JSESSIONID);
				List<NameValuePair> nvps = new ArrayList<NameValuePair>();
				nvps.add(new BasicNameValuePair("lt", LT));
				nvps.add(new BasicNameValuePair("_eventId", "submit"));
				nvps.add(new BasicNameValuePair("timezone", "-480"));
				nvps.add(new BasicNameValuePair("account",
						StaticParamConstants.USERNAME));
				nvps.add(new BasicNameValuePair("cipher",
						StaticParamConstants.PASSWORD));
				nvps.add(new BasicNameValuePair("checkCode", ocr));
				nvps.add(new BasicNameValuePair("checkCodeTemp", "8015"));
				nvps.add(new BasicNameValuePair("chkPasmUserName", "yes"));
				nvps.add(new BasicNameValuePair("chkPasmUserName", "on"));
				try {
					httPost.setEntity(new UrlEncodedFormEntity(nvps));
					CloseableHttpResponse response2 = httpClient.execute(httPost);
					HttpEntity pmoEntity = response2.getEntity();
					System.out.println("4登陆: " + response2.getStatusLine());
					String content = EntityUtils.toString(pmoEntity);

					//System.out.println(content.trim());

					if (content.contains("index_new.action") && response2.getStatusLine().getStatusCode() == 200) {
						System.out.println("5登陆成功！");
						flag = false;
					} else {
						System.out.println("5登陆失败！");
						Thread.sleep(3000);
						continue;
					}
					// get Cookie CASTGC
					List<Cookie> cookies = cookieStore.getCookies();
					//System.out.println("  Initial set of cookies:" + cookies.size());
					if (cookies.isEmpty()) {
						System.out.println("  None");
					} else {
						for (int i = 0; i < cookies.size(); i++) {
							Cookie cookie = cookies.get(i);
							//System.out.println("  - " + cookie.toString());
							if (cookie.getName().equalsIgnoreCase("JSESSIONID")) {
								JSESSIONID = cookie.getValue();
							}
						}
						//System.out.println("  JSESSIONID = |" + JSESSIONID + "|");
					}
				} catch (Exception e) {
					e.printStackTrace();
				} finally {
					httPost.abort();
				}

				// ultrapmo
				System.out.println("6登陆UCAS");
				// -- /ultrapmo/portal/index.action
				String pmoUrl = "/ultrapmo/portal/index.action";
				pmoLogin(httpClient, pmoUrl);

				System.out.println("7校验");
				String location = ucasLogin(httpClient, JSESSIONID);

				ucasSecurityCheck(location, JSESSIONID);

				setCookie(httpClient, JSESSIONID);
				pmoLogin(httpClient, pmoUrl);

				pmoUrl = "/ultrapmo/portal/nportal2.action";
				setCookie(httpClient, JSESSIONID);
				pmoLogin(httpClient, pmoUrl);

				pmoUrl = "/ultrapmo/portal/";
				setCookie(httpClient, JSESSIONID);
				pmoLogin(httpClient, pmoUrl);

				System.out.println("8签到成功");
			} catch (Exception e) {
				e.printStackTrace();
			} finally {
				httpClient.close();
			}

		}
	}

	private static HttpClient setCookie(HttpClient httpClient, String JSESSIONID) {
		BasicClientCookie cookieUUID = new BasicClientCookie("JSESSIONID", JSESSIONID);
		cookieUUID.setVersion(0);
		cookieUUID.setDomain(StaticParamConstants.PMO_IP);
		cookieUUID.setPath("/ultrapmo");
		cookieUUID.setExpiryDate(null);
		BasicCookieStore cookieStore = new BasicCookieStore();
		cookieStore.addCookie(cookieUUID);
		httpClient = HttpClients.custom()
				.setDefaultCookieStore(cookieStore).build();

		return httpClient;
	}

	public static void ucasSecurityCheck(String location, String JSESSIONID) {
		BasicClientCookie cookieUUID = new BasicClientCookie("JSESSIONID", JSESSIONID);
		cookieUUID.setVersion(0);
		cookieUUID.setDomain(StaticParamConstants.PMO_IP);
		cookieUUID.setPath("/ultrapmo");
		cookieUUID.setExpiryDate(null);

		BasicCookieStore cookieStore = new BasicCookieStore();
		cookieStore.addCookie(cookieUUID);

		CloseableHttpClient httpClient = HttpClients.custom()
				.setDefaultCookieStore(cookieStore).build();
		HttpGet httpGet = new HttpGet(location);

		getLocation(httpClient, httpGet);
	}

	private static String getLocation(CloseableHttpClient httpClient, HttpGet httpGet) {
		String location = "";
		int responseCode = 0;
		try {
			HttpParams params = new BasicHttpParams();
			params.setParameter("http.protocol.handle-redirects", false); // 默认不让重定向
																			// 这样就能拿到Location头了
			httpGet.setParams(params);
			HttpResponse response = httpClient.execute(httpGet);
			responseCode = response.getStatusLine().getStatusCode();
			Header[] headers = response.getAllHeaders();

			if (responseCode == 302) {
				Header locationHeader = response.getFirstHeader("Location");
				if (locationHeader != null) {
					location = locationHeader.getValue();
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return location;
	}

	public static String ucasLogin(CloseableHttpClient httpClient,
			String JSESSIONID) {
		HttpGet httpGet = new HttpGet("http://" + StaticParamConstants.UCAS_IP
				+ "/ucas/login?service=http://" + StaticParamConstants.PMO_IP
				+ "/ultrapmo/j_acegi_cas_security_check;jsessionid="
				+ JSESSIONID + "?t=http://" + StaticParamConstants.PMO_IP
				+ "/ultrapmo/portal/index.action");
		String location = getLocation(httpClient, httpGet);
		return location;
	}

	public static void pmoLogin(CloseableHttpClient httpClient, String url) {
		try {
			HttpGet httpGet = new HttpGet("http://"
					+ StaticParamConstants.PMO_IP
					+ url);
			CloseableHttpResponse pmoResponse = httpClient.execute(httpGet);
			try {
				//System.out.println("登陆PMO: " + pmoResponse.getStatusLine());
				HttpEntity pmoEntity = pmoResponse.getEntity();

				// String content = EntityUtils.toString(pmoEntity);

				// and ensure it is fully consumed
				EntityUtils.consume(pmoEntity);

				// 获取消息头的信息
				Header[] headers = pmoResponse.getAllHeaders();
				for (int i = 0; i < headers.length; i++) {
					//System.out.println(headers[i]);
				}

			} catch (Exception e) {
				e.printStackTrace();
			} finally {
				pmoResponse.close();
			}
		} catch (ClientProtocolException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * @param httpClient
	 * @Desc download the ocr image
	 */
	public static void downloadImage(HttpClient httpClient) {
		HttpGet httpGet = new HttpGet("http://" + StaticParamConstants.UCAS_IP
				+ "/ucas/user/auth/generator.htm");
		try {
			HttpResponse response = httpClient.execute(httpGet);
			System.out.println("2下载OCR: " + response.getStatusLine());
			HttpEntity entity = response.getEntity();
			if (entity.getContentType().getValue().contains("image/jpeg")) {
				String picName = "img//" + "pmo" + ".jpg";
				InputStream inputStream = entity.getContent();
				OutputStream outputStream = new FileOutputStream(picName);

				int i = 0;
				byte buffer[] = new byte[1024];
				while (true) {
					if (inputStream.available() < 1024) {
						while (i != -1) {
							i = inputStream.read();
							outputStream.write(i);
						}
						break;// 注意此处不能忘记哦
					} else {
						// 当文件的大小大于1024字节时
						inputStream.read(buffer);
						outputStream.write(buffer);
					}
				}

				inputStream.close();
				outputStream.close();
			}
		} catch (ClientProtocolException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static String getOcr() {
		String fileName = "img\\pmo.jpg";
		String ocr = "";
		try {
			BufferedImage img = ImagePreProcess3.removeBackgroud(fileName);
			ImageIO.write(img, "jpg", new File(fileName));
		} catch (Exception e) {
			e.printStackTrace();
		}
		File imageFile = new File(fileName);
		Tesseract instance = Tesseract.getInstance();
		try {
			ocr = instance.doOCR(imageFile);
		} catch (TesseractException e) {
			System.err.println(e.getMessage());
		}
		return ocr.trim();
	}

	public static String getHiddenValue(String original, String name, int length) {
		int index = original.indexOf("<input type=\"hidden\" id=\"" + name
				+ "\" name=\"" + name + "\" value=\"");
		index += 46;
		return original.substring(index, index + length);
	}
}
